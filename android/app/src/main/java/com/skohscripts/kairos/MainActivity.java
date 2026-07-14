package com.skohscripts.kairos;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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

    private static int serverPort = -1;

    private WebView webView;
    private KairosNotificationBridge notificationBridge;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(this));
        }
        final PyObject boot = Python.getInstance().getModule("kairos_boot");
        if (serverPort < 0) {
            serverPort = boot.callAttr("prepare", getFilesDir().getAbsolutePath()).toInt();
            final int port = serverPort;
            Thread server = new Thread(() -> boot.callAttr("serve", port), "kairos-uvicorn");
            server.setDaemon(true);
            server.start();
        }

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);   // chrono vivant, alertes
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());      // navigation interne, pas de navigateur externe
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

        setContentView(webView);

        loadWhenServerReady();
    }

    /** Sonde /favicon.ico (toujours 200 sur une instance réelle, même repère que
     *  le launcher de bureau) puis charge l'agenda du jour. */
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
