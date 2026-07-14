package com.skohscripts.kairos;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

/**
 * Pont natif entre la WebView et l'API Android de notifications (issue #16) :
 * android.webkit.WebView n'implémente pas window.Notification, donc les alertes
 * chrono de templates/kairos.html restent bloquées à « indisponibles » sans ce
 * pont. Exposé en JS sous window.KairosAndroid (voir MainActivity#onCreate).
 *
 * Volontairement sans AndroidX (cohérent avec MainActivity, voir
 * docs/ANDROID_PACKAGING.md) : uniquement des API de plateforme, disponibles au
 * minSdk 24 (NotificationManager#areNotificationsEnabled, Activity#requestPermissions)
 * ou gardées par Build.VERSION.SDK_INT (canal de notification API 26+, permission
 * POST_NOTIFICATIONS API 33+).
 */
public class KairosNotificationBridge {

    static final int REQUEST_CODE_POST_NOTIFICATIONS = 4201;

    private static final String CHANNEL_ID = "kairos-chrono-alerts";
    private static final int NOTIFICATION_ID = 1;

    private final Activity activity;
    private final WebView webView;

    public KairosNotificationBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        createChannelIfNeeded();
    }

    private void createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getManager();
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                activity.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(activity.getString(R.string.notification_channel_description));
        manager.createNotificationChannel(channel);
    }

    private NotificationManager getManager() {
        return (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /** Toujours vrai : l'existence même du pont (window.KairosAndroid) prouve la
     *  capacité — contrairement à 'Notification' in window côté navigateur, absent
     *  de android.webkit.WebView. */
    @JavascriptInterface
    public boolean canNotify() {
        return true;
    }

    /** Unifie les deux régimes de permission : avant l'API 33 il n'y a pas de
     *  permission runtime (seul le réglage système « notifications activées » pour
     *  l'appli compte) ; depuis l'API 33, ce même indicateur reflète aussi
     *  POST_NOTIFICATIONS. Une seule méthode plateforme couvre les deux cas depuis
     *  l'API 24 — pas besoin de distinguer les régimes côté appelant. */
    @JavascriptInterface
    public boolean hasPermission() {
        return getManager().areNotificationsEnabled();
    }

    /** Déclenche la boîte de dialogue système sur API 33+ (rien à demander avant :
     *  le réglage système gère seul, cf. hasPermission()). Appelé depuis le bouton
     *  d'opt-in existant, jamais au démarrage. Reboucle vers le JS pour qu'il
     *  ré-interroge hasPermission() et rafraîchisse son état — nécessaire car
     *  requestPermissions() est asynchrone (résout plus tard via
     *  MainActivity#onRequestPermissionsResult, pas de valeur de retour exploitable
     *  directement par le JS appelant). */
    @JavascriptInterface
    public void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.runOnUiThread(() -> activity.requestPermissions(
                    new String[]{"android.permission.POST_NOTIFICATIONS"},
                    REQUEST_CODE_POST_NOTIFICATIONS));
        } else {
            notifyPermissionChanged();
        }
    }

    @JavascriptInterface
    public void notify(String title, String body, String tag) {
        if (!hasPermission()) return;
        Intent openApp = new Intent(activity, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                activity, 0, openApp, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(activity, CHANNEL_ID)
                : new Notification.Builder(activity);
        builder.setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(contentIntent)
                .setAutoCancel(true);
        getManager().notify(tag, NOTIFICATION_ID, builder.build());
    }

    /** Appelé depuis MainActivity#onRequestPermissionsResult : prévient le JS via
     *  un évènement DOM personnalisé, faute de canal message natif→JS synchrone
     *  (pas d'AndroidX, pas de retour direct possible depuis un callback système
     *  asynchrone). webView.post(...) est sûr depuis n'importe quel thread appelant
     *  (JS bridge worker thread inclus) : il route vers le thread UI de la WebView. */
    void notifyPermissionChanged() {
        webView.post(() -> webView.evaluateJavascript(
                "window.dispatchEvent(new Event('kairos-android-permission-changed'))", null));
    }
}
