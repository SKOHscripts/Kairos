package com.skohscripts.kairos;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedDispatcher;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Unique activité de Kairos : démarre le serveur local (CPython + uvicorn via
 * Chaquopy, voir kairos_boot.py) puis affiche l'interface dans une WebView.
 *
 * Le port et le thread serveur sont statiques : ils survivent à une recréation
 * d'activité tant que le process vit (le serveur n'est jamais démarré deux fois).
 * Si Android tue le process en arrière-plan, tout redémarre proprement au retour
 * (SQLite committe à chaque requête, rien n'est perdu). Pas de bouton « Quitter »
 * ici : on quitte par le système, comme toute application Android.
 */
public class MainActivity extends Activity {

    private static final String TAG = "Kairos";
    /** Attente maximale de la première réponse du serveur, une fois `prepare()`
     *  revenu (import de l'application et démarrage d'uvicorn). */
    private static final long SERVER_READY_TIMEOUT_MS = 90_000;
    /** Attente maximale de `onPageFinished` après `loadUrl`. */
    private static final long PAGE_LOAD_TIMEOUT_MS = 30_000;
    /** Délai avant d'expliquer qu'un premier lancement peut être long. */
    private static final long SLOW_HINT_DELAY_MS = 8_000;

    // Survivent à une recréation d'activité tant que le process vit (voir la
    // docstring de la classe). Accès sous verrou de classe : deux instances
    // d'activité peuvent coexister brièvement.
    private static int serverPort = -1;
    private static Thread serverThread;
    private static volatile String serverError;

    private WebView webView;
    private StartupScreen startup;
    private KairosNotificationBridge notificationBridge;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable slowHint = () -> startup.showSlowHint();
    private final Runnable pageTimeout = () -> showStartupError(
            getString(R.string.startup_page_timeout), null);
    // Thread principal uniquement.
    private boolean initRunning;
    private boolean pageLoadFailed;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);   // chrono vivant, alertes
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {       // navigation interne, pas de navigateur externe
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Une page en erreur appelle aussi onPageFinished : ne jamais
                // dévoiler la page d'erreur du WebView à la place de l'agenda.
                if (!pageLoadFailed) {
                    hideStartupScreen();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame() && startup.isShowing()) {
                    pageLoadFailed = true;
                    showStartupError(getString(R.string.startup_failed),
                            String.valueOf(error.getDescription()));
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            // Sans WebChromeClient, le WebView système n'affiche jamais les dialogues
            // JS confirm()/alert() : confirm() résout silencieusement à false, donc les
            // formulaires de suppression (tâche, créneau) ne se soumettent jamais.
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                        .setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .setCancelable(false)
                        .show();
                return true;
            }
        });

        notificationBridge = new KairosNotificationBridge(this, webView);
        webView.addJavascriptInterface(notificationBridge, "KairosAndroid");
        // Lancement depuis la notification de mise à jour (appli fermée).
        notificationBridge.handleAction(
                getIntent().getStringExtra(KairosNotificationBridge.EXTRA_ACTION), false);

        setContentView(webView);
        // Écran de démarrage par-dessus toute la fenêtre (voir StartupScreen) :
        // il reste affiché jusqu'à l'agenda chargé ou jusqu'à un état d'erreur
        // explicite, jamais masqué « à l'aveugle » sur une page vide.
        startup = new StartupScreen(this);
        startup.attachTo((ViewGroup) getWindow().getDecorView());
        registerPredictiveBackCallback();

        startInit();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Lance (ou relance, bouton « Réessayer ») la chaîne de démarrage sur le
     *  thread `kairos-init`, jamais le thread principal : `Python.start()` et
     *  surtout `kairos_boot.prepare()` peuvent prendre plusieurs secondes au
     *  premier lancement, et les exécuter ici bloquerait tout rendu (c'était la
     *  cause du premier écran blanc, voir docs/ANDROID_PACKAGING.md). Chaque
     *  étape est idempotente : un réessai reprend là où l'échec a eu lieu. */
    private void startInit() {
        if (initRunning) {
            return;
        }
        initRunning = true;
        pageLoadFailed = false;
        startup.showProgress(R.string.startup_python);
        handler.removeCallbacks(slowHint);
        handler.postDelayed(slowHint, SLOW_HINT_DELAY_MS);
        new Thread(this::runInit, "kairos-init").start();
    }

    private void runInit() {
        try {
            String base = "http://127.0.0.1:" + ensureServerStarted();
            showStep(R.string.startup_server);
            waitUntilServing(base);
            runOnUiThread(() -> loadApp(base));
        } catch (StartupFailure failure) {
            Log.e(TAG, "Démarrage interrompu : " + failure.getMessage());
            runOnUiThread(() -> showStartupError(getString(failure.titleRes), failure.getMessage()));
        } catch (Throwable t) {
            Log.e(TAG, "Échec du démarrage", t);
            runOnUiThread(() -> showStartupError(getString(R.string.startup_failed), describe(t)));
        }
    }

    /** Démarre Python, prépare l'environnement et lance uvicorn, chacun
     *  seulement si ce n'est pas déjà fait (recréation d'activité, réessai).
     *  Retourne le port servi. */
    private int ensureServerStarted() {
        synchronized (MainActivity.class) {
            if (!Python.isStarted()) {
                showStep(R.string.startup_python);
                Python.start(new AndroidPlatform(this));
            }
            PyObject boot = Python.getInstance().getModule("kairos_boot");
            if (serverPort < 0) {
                showStep(R.string.startup_prepare);
                serverPort = boot.callAttr("prepare", getFilesDir().getAbsolutePath()).toInt();
            }
            if (serverThread == null || !serverThread.isAlive()) {
                serverError = null;
                final int port = serverPort;
                serverThread = new Thread(() -> {
                    // Une exception non rattrapée sur ce thread tuerait tout le
                    // process (« Kairos s'est arrêté ») : on la garde pour
                    // l'afficher sur l'écran de démarrage à la place.
                    try {
                        boot.callAttr("serve", port);
                    } catch (Throwable t) {
                        Log.e(TAG, "Serveur arrêté", t);
                        serverError = describe(t);
                    }
                }, "kairos-uvicorn");
                serverThread.setDaemon(true);
                serverThread.start();
            }
            return serverPort;
        }
    }

    /** Sonde /favicon.ico (toujours 200 sur une instance réelle, même repère
     *  que le launcher de bureau) jusqu'à la première réponse, en abandonnant
     *  tôt si le thread serveur s'est arrêté. */
    private void waitUntilServing(String base) throws StartupFailure, InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + SERVER_READY_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                HttpURLConnection probe =
                        (HttpURLConnection) new URL(base + "/favicon.ico").openConnection();
                probe.setConnectTimeout(500);
                probe.setReadTimeout(2000);
                try {
                    if (probe.getResponseCode() == 200) {
                        return;
                    }
                } finally {
                    probe.disconnect();
                }
            } catch (Exception ignored) {
                // serveur pas encore prêt : on réessaie
            }
            Thread server = serverThread;
            if (server == null || !server.isAlive()) {
                throw new StartupFailure(R.string.startup_server_timeout,
                        serverError != null ? serverError : "Le serveur local s'est arrêté.");
            }
            Thread.sleep(300);
        }
        throw new StartupFailure(R.string.startup_server_timeout,
                "Aucune réponse de " + base + " après " + SERVER_READY_TIMEOUT_MS / 1000 + " s.");
    }

    private void loadApp(String base) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        initRunning = false;
        pageLoadFailed = false;
        startup.showProgress(R.string.startup_page);
        handler.removeCallbacks(pageTimeout);
        handler.postDelayed(pageTimeout, PAGE_LOAD_TIMEOUT_MS);
        webView.loadUrl(base + "/kairos");
    }

    private void showStep(int messageRes) {
        runOnUiThread(() -> {
            if (startup.isShowing()) {
                startup.showProgress(messageRes);
            }
        });
    }

    /** État d'erreur de l'écran de démarrage (au lieu d'un écran figé ou d'une
     *  page vide). Sans effet une fois l'agenda affiché. */
    private void showStartupError(String title, String detail) {
        if (isFinishing() || isDestroyed() || !startup.isShowing()) {
            return;
        }
        initRunning = false;
        handler.removeCallbacks(slowHint);
        handler.removeCallbacks(pageTimeout);
        startup.showError(title, detail, this::startInit);
    }

    private void hideStartupScreen() {
        handler.removeCallbacks(slowHint);
        handler.removeCallbacks(pageTimeout);
        startup.dismiss();
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        String text = t.getClass().getSimpleName() + (message != null ? " : " + message : "");
        return text.length() > 400 ? text.substring(0, 400) + "…" : text;
    }

    /** Échec attendu d'une étape (délai dépassé, serveur arrêté) : titre à
     *  afficher et détail technique. */
    private static final class StartupFailure extends Exception {
        final int titleRes;

        StartupFailure(int titleRes, String detail) {
            super(detail);
            this.titleRes = titleRes;
        }
    }

    /** Geste retour prédictif (API 33+, `android.window` natif — pas AndroidX, même
     *  parti pris que {@link KairosNotificationBridge}). En dessous de l'API 33,
     *  {@link #onBackPressed()} (legacy, inchangé) reste le seul chemin actif :
     *  duplication volontaire plutôt que factorisation, pour ne rien risquer sur
     *  ce chemin déjà en production. Un seul enregistrement suffit : `configChanges`
     *  couvre la rotation, `onCreate` n'est pas rappelé. */
    private void registerPredictiveBackCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                () -> {
                    if (webView != null && webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
                });
    }

    /** Toucher d'une notification alors que Kairos est déjà ouvert
     *  (FLAG_ACTIVITY_SINGLE_TOP) : la page est chargée, on la prévient. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (notificationBridge != null) {
            notificationBridge.handleAction(
                    intent.getStringExtra(KairosNotificationBridge.EXTRA_ACTION), true);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == KairosNotificationBridge.REQUEST_CODE_POST_NOTIFICATIONS
                && notificationBridge != null) {
            notificationBridge.notifyPermissionChanged();
        }
    }
}
