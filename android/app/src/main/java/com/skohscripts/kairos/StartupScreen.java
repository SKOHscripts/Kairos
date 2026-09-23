package com.skohscripts.kairos;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Écran de démarrage applicatif : couvre toute la fenêtre (ajouté à la
 * DecorView, pas au contenu) pendant que Python et uvicorn démarrent, puis
 * disparaît en fondu quand l'agenda est chargé.
 *
 * Le logo ({@code @drawable/kairos_splash_logo}, statique) est centré dans une
 * boîte de 288dp sur la fenêtre entière : même place et même taille que le logo
 * du fond de fenêtre (avant l'API 31) et du splash système (API 31+), d'où
 * aucun saut visible à la relève. L'animation de balayage n'est jamais rejouée
 * ici : le splash système l'a déjà jouée (API 31+), et la rejouer depuis midi
 * après un logo complet (fond de fenêtre, API 24-30) ferait clignoter le secteur.
 *
 * Sous le logo, une colonne porte l'état : indicateur de progression et étape
 * en cours, ligne d'explication si le démarrage dure, ou message d'erreur avec
 * détail technique (sélectionnable, pour un rapport de bug) et bouton
 * « Réessayer ». Vues construites en code, sans AndroidX ni layout XML (même
 * parti pris que le reste de l'APK).
 */
final class StartupScreen {

    private static final long FADE_OUT_MS = 220;
    /** Distance entre le centre de la fenêtre et le haut de la colonne d'état :
     *  juste sous le disque du logo (rayon ~89dp dans la boîte de 288dp). */
    private static final int COLUMN_OFFSET_DP = 104;

    private final Activity activity;
    private final FrameLayout root;
    private final LinearLayout column;
    private final ProgressBar spinner;
    private final TextView status;
    private final TextView hint;
    private final TextView detail;
    private final Button retry;
    private boolean showing = true;

    StartupScreen(Activity activity) {
        this.activity = activity;

        root = new FrameLayout(activity);
        root.setBackgroundColor(color(R.color.kairos_bg));
        // Absorbe les touchers : la WebView dessous ne doit rien recevoir tant
        // que l'écran de démarrage est affiché.
        root.setClickable(true);

        ImageView logo = new ImageView(activity);
        logo.setImageResource(R.drawable.kairos_splash_logo);
        logo.setContentDescription(activity.getString(R.string.app_name));
        root.addView(logo, new FrameLayout.LayoutParams(dp(288), dp(288), Gravity.CENTER));

        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(32), 0, dp(32), dp(16));

        spinner = new ProgressBar(activity);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(color(R.color.kairos_text_3)));
        column.addView(spinner, new LinearLayout.LayoutParams(dp(24), dp(24)));

        status = text(14, R.color.kairos_text_2);
        // Annoncé par TalkBack à chaque changement d'étape.
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        column.addView(status, wrap(12));

        hint = text(12, R.color.kairos_text_3);
        hint.setText(R.string.startup_slow_hint);
        hint.setVisibility(View.GONE);
        column.addView(hint, wrap(8));

        detail = text(12, R.color.kairos_text_3);
        detail.setTextIsSelectable(true);
        detail.setMaxLines(6);
        detail.setVisibility(View.GONE);
        column.addView(detail, wrap(8));

        retry = new Button(activity);
        retry.setText(R.string.startup_retry);
        retry.setAllCaps(false);
        retry.setTextColor(0xFFFFFFFF);
        retry.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        retry.setTypeface(Typeface.DEFAULT_BOLD);
        retry.setMinHeight(dp(44));   // cible tactile de la charte
        retry.setMinimumHeight(dp(44));
        retry.setPadding(dp(20), 0, dp(20), 0);
        retry.setBackground(buttonBackground());
        retry.setStateListAnimator(null);  // pas d'ombre portée (charte)
        retry.setVisibility(View.GONE);
        column.addView(retry, wrap(16));

        root.addView(column, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));
        // Colonne placée par translation (pas de relayout) sous le centre de la
        // fenêtre, remontée si elle déborderait en bas (paysage, message long).
        View.OnLayoutChangeListener place = (v, l, t, r, b, ol, ot, or, ob) -> {
            int height = root.getHeight();
            int top = height / 2 + dp(COLUMN_OFFSET_DP);
            column.setTranslationY(Math.max(0, Math.min(top, height - column.getHeight())));
        };
        root.addOnLayoutChangeListener(place);
        column.addOnLayoutChangeListener(place);
    }

    /** Couvre toute la fenêtre, barres système comprises (même repère de
     *  centrage que le fond de fenêtre et le splash système). */
    void attachTo(ViewGroup decorView) {
        decorView.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    boolean isShowing() {
        return showing;
    }

    /** Étape en cours ; quitte l'état d'erreur s'il était affiché (réessai). */
    void showProgress(int messageRes) {
        status.setText(messageRes);
        status.setTextColor(color(R.color.kairos_text_2));
        status.setTypeface(Typeface.DEFAULT);
        spinner.setVisibility(View.VISIBLE);
        detail.setVisibility(View.GONE);
        retry.setVisibility(View.GONE);
    }

    void showSlowHint() {
        hint.setVisibility(View.VISIBLE);
    }

    void showError(String title, String technicalDetail, Runnable onRetry) {
        spinner.setVisibility(View.GONE);
        hint.setVisibility(View.GONE);
        status.setText(title);
        status.setTextColor(color(R.color.kairos_critical));
        status.setTypeface(Typeface.DEFAULT_BOLD);
        if (technicalDetail != null && !technicalDetail.isEmpty()) {
            detail.setText(technicalDetail);
            detail.setVisibility(View.VISIBLE);
        } else {
            detail.setVisibility(View.GONE);
        }
        retry.setOnClickListener(v -> onRetry.run());
        retry.setVisibility(View.VISIBLE);
    }

    /** Fondu puis retrait de la hiérarchie. Idempotent. */
    void dismiss() {
        if (!showing) {
            return;
        }
        showing = false;
        root.animate()
                .alpha(0f)
                .setDuration(FADE_OUT_MS)
                .withEndAction(() -> {
                    ViewGroup parent = (ViewGroup) root.getParent();
                    if (parent != null) {
                        parent.removeView(root);
                    }
                })
                .start();
    }

    private TextView text(int sizeSp, int colorRes) {
        TextView view = new TextView(activity);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color(colorRes));
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        return view;
    }

    private LinearLayout.LayoutParams wrap(int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        return lp;
    }

    /** Bouton plein MD3 de la charte : primaire (miel), appui plus foncé,
     *  forme en pilule (docs/DESIGN_SYSTEM.md § Forme). */
    private StateListDrawable buttonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_pressed}, rounded(color(R.color.kairos_accent_hover)));
        states.addState(new int[] {}, rounded(color(R.color.kairos_accent)));
        return states;
    }

    private GradientDrawable rounded(int fill) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(999)); // pilule : GradientDrawable borne le rayon à la demi-hauteur
        return shape;
    }

    private int color(int res) {
        return activity.getColor(res);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
