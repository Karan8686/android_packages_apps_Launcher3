/*
 * Copyright (C) 2025 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.qsb;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.LauncherPrefChangeListener;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherPrefsExt;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

public class CompactSearchBar extends FrameLayout implements LauncherPrefChangeListener {

    public static final String ACTION_GOOGLE_SEARCH = "google_search";
    public static final String ACTION_LENS = "lens";
    public static final String ACTION_MIC = "mic";
    public static final String ACTION_GEMINI = "gemini";

    private static final float CLICK_FEEDBACK_SCALE = 0.95f;
    private static final long CLICK_FEEDBACK_DURATION = 100L;

    private final Context mContext;
    private LinearLayout mInnerContainer;
    private TextView mSearchText;
    private ImageButton mActionButton;

    public CompactSearchBar(Context context) {
        this(context, null);
    }

    public CompactSearchBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CompactSearchBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mInnerContainer = findViewById(R.id.compact_search_bar_inner);
        mSearchText = findViewById(R.id.compact_search_text);
        mActionButton = findViewById(R.id.compact_action_button);

        setupViews();
        updateActionIcon();
        updateBackground();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        LauncherPrefs.get(mContext).addListener(
                this,
                LauncherPrefsExt.COMPACT_SEARCH_BAR_ACTION,
                LauncherPrefsExt.DOCK_THEME,
                LauncherPrefsExt.DOCK_MUSIC_SEARCH,
                LauncherPrefsExt.HOTSEAT_QSB_OPACITY,
                LauncherPrefsExt.HOTSEAT_QSB_STROKE_WIDTH,
                LauncherPrefsExt.SEARCH_RADIUS_SIZE
        );
        updateActionIcon();
        updateBackground();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        LauncherPrefs.get(mContext).removeListener(
                this,
                LauncherPrefsExt.COMPACT_SEARCH_BAR_ACTION,
                LauncherPrefsExt.DOCK_THEME,
                LauncherPrefsExt.DOCK_MUSIC_SEARCH,
                LauncherPrefsExt.HOTSEAT_QSB_OPACITY,
                LauncherPrefsExt.HOTSEAT_QSB_STROKE_WIDTH,
                LauncherPrefsExt.SEARCH_RADIUS_SIZE
        );
    }

    @Override
    public void onPrefChanged(String key) {
        if (LauncherPrefsExt.COMPACT_SEARCH_BAR_ACTION.getSharedPrefKey().equals(key)
                || LauncherPrefsExt.DOCK_THEME.getSharedPrefKey().equals(key)
                || LauncherPrefsExt.DOCK_MUSIC_SEARCH.getSharedPrefKey().equals(key)) {
            updateActionIcon();
            updateBackground();
        } else if (LauncherPrefsExt.HOTSEAT_QSB_OPACITY.getSharedPrefKey().equals(key)
                || LauncherPrefsExt.HOTSEAT_QSB_STROKE_WIDTH.getSharedPrefKey().equals(key)
                || LauncherPrefsExt.SEARCH_RADIUS_SIZE.getSharedPrefKey().equals(key)) {
            updateBackground();
        }
    }

    private void setupViews() {
        if (mInnerContainer != null) {
            mInnerContainer.setOnClickListener(v -> {
                animateClickFeedback(mInnerContainer, () -> openSearch(v));
            });
        }

        if (mActionButton != null) {
            mActionButton.setOnClickListener(v -> {
                animateClickFeedback(mActionButton, () -> executeAction(v));
            });
        }
    }

    private void animateClickFeedback(View view, Runnable onComplete) {
        // Trigger vibration feedback
        Vibrator vibrator = mContext.getSystemService(Vibrator.class);
        if (vibrator != null) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK));
        }

        view.animate()
                .scaleX(CLICK_FEEDBACK_SCALE)
                .scaleY(CLICK_FEEDBACK_SCALE)
                .setDuration(CLICK_FEEDBACK_DURATION)
                .withEndAction(() -> {
                    view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(CLICK_FEEDBACK_DURATION)
                            .withEndAction(onComplete)
                            .start();
                })
                .start();
    }

    private void updateActionIcon() {
        if (mActionButton == null) return;

        String action = LauncherPrefsExt.COMPACT_SEARCH_BAR_ACTION.get(mContext);
        boolean isThemed = LauncherPrefsExt.DOCK_THEME.get(mContext);

        // Hide action button for actions that require GSA if GSA is not enabled
        boolean requiresGsa = ACTION_LENS.equals(action) || ACTION_MIC.equals(action);
        if (requiresGsa && !Utilities.isGSAEnabled(mContext)) {
            mActionButton.setVisibility(View.GONE);
            return;
        }

        // Hide Gemini button if Gemini is not installed and GSA is not enabled
        if (ACTION_GEMINI.equals(action)
                && !Utilities.isPackageInstalled(mContext, Utilities.GEMINI_PACKAGE)
                && !Utilities.isGSAEnabled(mContext)) {
            mActionButton.setVisibility(View.GONE);
            return;
        }

        mActionButton.setVisibility(View.VISIBLE);
        int iconRes;

        switch (action) {
            case ACTION_LENS:
                iconRes = isThemed ? R.drawable.ic_lens_themed : R.drawable.ic_lens_color;
                break;
            case ACTION_MIC:
                if (Utilities.isMusicSearchEnabled(mContext)) {
                    iconRes = isThemed ? R.drawable.ic_music_themed : R.drawable.ic_music_color;
                } else {
                    iconRes = isThemed ? R.drawable.ic_mic_themed : R.drawable.ic_mic_color;
                }
                break;
            case ACTION_GEMINI:
                iconRes = isThemed ? R.drawable.ic_gemini_themed : R.drawable.ic_gemini_color;
                break;
            case ACTION_GOOGLE_SEARCH:
            default:
                iconRes = isThemed ? R.drawable.ic_super_g_themed : R.drawable.ic_super_g_color;
                break;
        }

        mActionButton.setImageResource(iconRes);
    }

    private void updateBackground() {
        if (mInnerContainer == null) return;

        boolean isThemed = LauncherPrefsExt.DOCK_THEME.get(mContext);
        int opacity = LauncherPrefsExt.HOTSEAT_QSB_OPACITY.get(mContext);
        int alpha = Math.round(opacity * 255f / 100f);
        int strokeWidthDp = LauncherPrefsExt.HOTSEAT_QSB_STROKE_WIDTH.get(mContext);

        Resources res = mContext.getResources();
        float cornerRadius = getCornerRadius();

        int bgColor = isThemed
                ? Themes.getAttrColor(mContext, R.attr.qsbFillColorThemed)
                : Themes.getAttrColor(mContext, R.attr.qsbFillColor);
        bgColor = ColorUtils.setAlphaComponent(bgColor, alpha);

        int strokeColor = ColorUtils.setAlphaComponent(
                Themes.getAttrColor(mContext, R.attr.qsbIconTintQuaternary),
                (int) (255 * 0.6f));
        float strokeWidthPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, strokeWidthDp, res.getDisplayMetrics());

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(cornerRadius);
        background.setColor(bgColor);
        if (strokeWidthPx > 0f) {
            background.setStroke(Math.round(strokeWidthPx), strokeColor);
        }

        // Create ripple wrapper
        int rippleColor = ContextCompat.getColor(mContext, R.color.accent_ripple_color);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.RECTANGLE);
        mask.setCornerRadius(cornerRadius);
        mask.setColor(Color.WHITE);

        RippleDrawable ripple = new RippleDrawable(
                android.content.res.ColorStateList.valueOf(rippleColor),
                background,
                mask
        );

        mInnerContainer.setBackground(ripple);

        // Update action button background with ripple
        if (mActionButton != null) {
            float buttonRadius = res.getDimension(R.dimen.compact_search_bar_icon_size) / 2f;
            GradientDrawable buttonMask = new GradientDrawable();
            buttonMask.setShape(GradientDrawable.OVAL);
            buttonMask.setColor(Color.WHITE);

            RippleDrawable buttonRipple = new RippleDrawable(
                    android.content.res.ColorStateList.valueOf(rippleColor),
                    null,
                    buttonMask
            );
            mActionButton.setBackground(buttonRipple);
        }
    }

    private float getCornerRadius() {
        Resources res = mContext.getResources();
        float defaultRadius = res.getDimension(R.dimen.compact_search_bar_height) / 2f;
        int customRadius = LauncherPrefsExt.SEARCH_RADIUS_SIZE.get(mContext);
        if (customRadius >= 0) {
            return TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, customRadius, res.getDisplayMetrics());
        }
        return defaultRadius;
    }

    private void openSearch(View v) {
        String searchPackage = OseWidgetManager.getSearchWidgetPackageName(mContext);
        if (searchPackage == null && Utilities.isGSAEnabled(mContext)) {
            searchPackage = Utilities.GSA_PACKAGE;
        }
        if (searchPackage != null) {
            Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(searchPackage);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                ActivityContext.lookupContext(mContext).startActivitySafely(v, intent, null);
                return;
            }
            Intent searchIntent = new Intent(Intent.ACTION_SEARCH)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    .setPackage(searchPackage);
            if (searchIntent.resolveActivity(mContext.getPackageManager()) != null) {
                ActivityContext.lookupContext(mContext).startActivitySafely(v, searchIntent, null);
                return;
            }
        }
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        ActivityContext.lookupContext(mContext).startActivitySafely(v, browserIntent, null);
    }

    private void executeAction(View v) {
        String action = LauncherPrefsExt.COMPACT_SEARCH_BAR_ACTION.get(mContext);

        switch (action) {
            case ACTION_LENS:
                openLens(v);
                break;
            case ACTION_MIC:
                openVoiceSearch(v);
                break;
            case ACTION_GEMINI:
                openGemini(v);
                break;
            case ACTION_GOOGLE_SEARCH:
            default:
                openSearch(v);
                break;
        }
    }

    private void openLens(View v) {
        Intent lensIntent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putString("caller_package", Utilities.GSA_PACKAGE);
        bundle.putLong("start_activity_time_nanos", SystemClock.elapsedRealtimeNanos());
        lensIntent.setComponent(new android.content.ComponentName(
                Utilities.GSA_PACKAGE, Utilities.LENS_ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setPackage(Utilities.GSA_PACKAGE)
                .setData(Uri.parse(Utilities.LENS_URI))
                .putExtra("lens_activity_params", bundle);
        ActivityContext.lookupContext(mContext).startActivitySafely(v, lensIntent, null);
    }

    private void openVoiceSearch(View v) {
        if (Utilities.isMusicSearchEnabled(mContext)) {
            Intent musicIntent = new Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .setPackage(Utilities.GSA_PACKAGE);
            ActivityContext.lookupContext(mContext).startActivitySafely(v, musicIntent, null);
        } else {
            Intent voiceIntent = new Intent(Intent.ACTION_VOICE_COMMAND)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .setPackage(Utilities.GSA_PACKAGE);
            ActivityContext.lookupContext(mContext).startActivitySafely(v, voiceIntent, null);
        }
    }

    private void openGemini(View v) {
        // First try to launch the standalone Gemini app
        Intent geminiIntent = mContext.getPackageManager()
                .getLaunchIntentForPackage(Utilities.GEMINI_PACKAGE);
        if (geminiIntent != null) {
            geminiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            ActivityContext.lookupContext(mContext).startActivitySafely(v, geminiIntent, null);
            return;
        }

        // Fallback to Assistant / voice command via Google app
        Intent assistantIntent = new Intent(Intent.ACTION_VOICE_COMMAND)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .setPackage(Utilities.GSA_PACKAGE);
        try {
            mContext.startActivity(assistantIntent,
                    ActivityOptions.makeCustomAnimation(mContext, 0, 0).toBundle());
        } catch (Exception e) {
            // Final fallback: open regular search
            openSearch(v);
        }
    }
}
