package com.skohscripts.kairos;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
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
    // Mises à jour (docs/spec/mises-a-jour.md) : canal séparé, pour que
    // l'utilisateur puisse couper l'un sans l'autre dans les réglages Android.
    private static final String UPDATE_CHANNEL_ID = "kairos-updates";
    private static final int UPDATE_NOTIFICATION_ID = 2;
    /** Extra de l'intention portée par la notification de mise à jour. */
    static final String EXTRA_ACTION = "com.skohscripts.kairos.ACTION";
    static final String ACTION_INSTALL_UPDATE = "install_update";

    private final Activity activity;
    private final WebView webView;
    /** Action demandée par un clic de notification au lancement à froid, lue
     *  une fois par la page (`takePendingAction`) dès qu'elle est chargée. */
    private volatile String pendingAction = "";

    public KairosNotificationBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
        createChannelIfNeeded(CHANNEL_ID, R.string.notification_channel_name,
                R.string.notification_channel_description);
        createChannelIfNeeded(UPDATE_CHANNEL_ID, R.string.update_channel_name,
                R.string.update_channel_description);
    }

    private void createChannelIfNeeded(String id, int nameRes, int descriptionRes) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getManager();
        if (manager.getNotificationChannel(id) != null) return;
        NotificationChannel channel = new NotificationChannel(
                id, activity.getString(nameRes), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(activity.getString(descriptionRes));
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

    /** Notification « nouvelle version » : un toucher rouvre Kairos et lance
     *  la mise à jour (extra {@link #EXTRA_ACTION}, relayé par MainActivity). */
    @JavascriptInterface
    public void notifyUpdate(String title, String body) {
        if (!hasPermission()) return;
        Intent openApp = new Intent(activity, MainActivity.class)
                .putExtra(EXTRA_ACTION, ACTION_INSTALL_UPDATE)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // requestCode distinct des alertes chrono : sinon FLAG_UPDATE_CURRENT
        // réécrirait l'extra de l'une avec celui de l'autre.
        PendingIntent contentIntent = PendingIntent.getActivity(
                activity, UPDATE_NOTIFICATION_ID, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(activity, UPDATE_CHANNEL_ID)
                : new Notification.Builder(activity);
        builder.setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(contentIntent)
                .setAutoCancel(true);
        getManager().notify(UPDATE_NOTIFICATION_ID, builder.build());
    }

    @JavascriptInterface
    public String takePendingAction() {
        String action = pendingAction;
        pendingAction = "";
        return action;
    }

    /** Toucher de la notification de mise à jour. Activité déjà affichée
     *  (`onNewIntent`) : la page est là, on la prévient directement. Lancement à
     *  froid : la page n'existe pas encore, elle lira l'action au chargement. */
    void handleAction(String action, boolean pageLoaded) {
        if (!ACTION_INSTALL_UPDATE.equals(action)) return;
        if (pageLoaded) {
            webView.post(() -> webView.evaluateJavascript(
                    "window.dispatchEvent(new Event('kairos-update-install'))", null));
        } else {
            pendingAction = action;
        }
    }

    /** Remet l'APK téléchargé et vérifié (app/updates.py) à l'installeur
     *  Android, qui demande confirmation à l'utilisateur et vérifie que la clé
     *  de signature est la même que celle de l'application installée.
     *
     *  @return "started", "permission" (réglage « installer des applications
     *  inconnues » ouvert : l'utilisateur doit l'accorder puis réessayer) ou
     *  "missing" (aucun APK vérifié à installer). */
    @JavascriptInterface
    public String installUpdate() {
        if (!UpdateApkProvider.apkFile(activity).isFile()) {
            return "missing";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.runOnUiThread(() -> activity.startActivity(settings));
            return "permission";
        }
        Intent install = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(UpdateApkProvider.apkUri(activity), UpdateApkProvider.APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.runOnUiThread(() -> {
            try {
                activity.startActivity(install);
            } catch (ActivityNotFoundException e) {
                webView.evaluateJavascript("window.dispatchEvent(new Event('kairos-update-install-failed'))", null);
            }
        });
        return "started";
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
