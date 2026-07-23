package com.skohscripts.kairos;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
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

    private static final long STARTUP_OVERLAY_TIMEOUT_MS = 30_000;

    private static int serverPort = -1;

    private WebView webView;
    private View startupOverlay;
    private KairosNotificationBridge notificationBridge;
    // Utilisé uniquement pour le filet de sécurité qui masque l'overlay de
    // démarrage si `onPageFinished` n'arrive jamais (page en échec) — voir
    // `hideStartupOverlay`.
    private final Handler overlayHandler = new Handler(Looper.getMainLooper());

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
                hideStartupOverlay();
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

        // Overlay de démarrage applicatif (plutôt que de compter sur le splash
        // système, voir `startupOverlay` et `hideStartupOverlay` ci-dessous) :
        // WebView en dessous, overlay par-dessus, masqué en fondu une fois la
        // première page réellement chargée.
        FrameLayout root = new FrameLayout(this);
        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        startupOverlay = buildStartupOverlay();
        root.addView(startupOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);
        registerPredictiveBackCallback();

        // Filet de sécurité : si `onPageFinished` n'arrive jamais (page en échec,
        // réseau local qui ne répond jamais), ne pas rester bloqué indéfiniment sur
        // le logo — l'utilisateur retrouve au moins la WebView (même vide/en erreur)
        // plutôt qu'un écran figé.
        overlayHandler.postDelayed(this::hideStartupOverlay, STARTUP_OVERLAY_TIMEOUT_MS);

        // `Python.start()` et surtout `kairos_boot.prepare()` (extraction du paquet
        // Python embarqué, première écriture de la base SQLite) peuvent prendre
        // plusieurs secondes au tout premier lancement — les exécuter sur le thread
        // principal bloquerait tout rendu, y compris celui de l'overlay ci-dessus
        // (c'est ce qui, avant ce correctif, produisait un écran blanc : le splash
        // système est piloté par ce même thread principal, qu'il ne pouvait pas
        // dessiner tant que ce bloc s'exécutait de façon synchrone dans `onCreate`).
        new Thread(() -> {
            startServerIfNeeded();
            loadWhenServerReady();
        }, "kairos-init").start();
    }

    /** Construit l'overlay plein écran affiché pendant le démarrage : fond uni à la
     *  couleur de l'app (`@color/kairos_bg`, cohérent avec `themes.xml`) et le logo
     *  animé déjà utilisé pour le splash natif (`@drawable/kairos_splash_icon`,
     *  `AnimatedVectorDrawable` — même secteur qui balaie depuis midi). Démarré
     *  explicitement via `Animatable.start()` : contrairement au splash système, rien
     *  ne le joue automatiquement ici. */
    private View buildStartupOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(getColor(R.color.kairos_bg));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.kairos_splash_icon);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        overlay.addView(icon, lp);

        Drawable drawable = icon.getDrawable();
        if (drawable instanceof Animatable) {
            ((Animatable) drawable).start();
        }
        return overlay;
    }

    /** Masque l'overlay de démarrage en fondu. Appelé depuis `onPageFinished`
     *  (chemin normal) et depuis le filet de sécurité de `onCreate` (chemin de
     *  secours) — idempotent : un second appel (page suivante rechargée en plein,
     *  ou timeout après un `onPageFinished` déjà traité) est un no-op silencieux. */
    private void hideStartupOverlay() {
        if (startupOverlay == null || startupOverlay.getVisibility() == View.GONE) {
            return;
        }
        overlayHandler.removeCallbacksAndMessages(null);
        startupOverlay.animate()
                .alpha(0f)
                .setDuration(220)
                .withEndAction(() -> startupOverlay.setVisibility(View.GONE))
                .start();
    }

    /** Démarre Python/uvicorn si ce n'est pas déjà fait (voir la docstring de la
     *  classe : le port et le thread serveur survivent à une recréation d'activité
     *  tant que le process vit). Appelé depuis le thread `kairos-init` de
     *  `onCreate`, jamais le thread principal. */
    private void startServerIfNeeded() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        PyObject boot = Python.getInstance().getModule("kairos_boot");
        if (serverPort < 0) {
            serverPort = boot.callAttr("prepare", getFilesDir().getAbsolutePath()).toInt();
            int port = serverPort;
            Thread server = new Thread(() -> boot.callAttr("serve", port), "kairos-uvicorn");
            server.setDaemon(true);
            server.start();
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

    /** Sonde /favicon.ico (toujours 200 sur une instance réelle, même repère que
     *  le launcher de bureau) puis charge l'agenda du jour. Appelé depuis le thread
     *  `kairos-init` une fois `startServerIfNeeded` revenu (donc `serverPort` posé) —
     *  spawn son propre thread `kairos-probe`, comme avant ce correctif. */
    private void loadWhenServerReady() {
        final String base = "http://127.0.0.1:" + serverPort;
        new Thread(() -> {
            for (int attempt = 0; attempt < 100; attempt++) {
                try {
                    HttpURLConnection probe =
                            (HttpURLConnection) new URL(base + "/favicon.ico").openConnection();
                    probe.setConnectTimeout(500);
                    probe.setReadTimeout(500);
                    if (probe.getResponseCode() == 200) {
                        break;
                    }
                } catch (Exception ignored) {
                    // serveur pas encore prêt : on réessaie
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    return;
                }
            }
            runOnUiThread(() -> webView.loadUrl(base + "/kairos"));
        }, "kairos-probe").start();
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
