/*
 * Copyright (C) 2025-2026 AxionOS
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.CrossWindowBlurListeners;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import com.android.app.animation.Interpolators;
import com.android.launcher3.graphics.AxBackdropBlurSurface;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherPrefsExt;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.FloatingIconViewCompanion;

import java.util.function.Consumer;

public class CompactSearchBar extends FrameLayout
        implements Reorderable, FloatingIconViewCompanion,
        SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "CompactSearchBar";
    private static final String ACTION_GOOGLE_SEARCH = "google_search";
    private static final String ACTION_LENS = "lens";
    private static final String ACTION_MIC = "mic";
    private static final String ACTION_GEMINI = "gemini";

    private static final long SQUISH_DURATION_MS = 400L;
    private static final float LANDING_SQUISH_INTENSITY = 0.55f;
    private boolean mLandingSquish;
    private static final long ACTION_LAUNCH_DELAY_MS = 120L;

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    private final Context mContext;
    private float mScaleForReorderBounce = 1f;

    private ImageView mActionIcon;
    private TextView mActionText;
    private View mInner;
    private ObjectAnimator mSquishAnimator;
    private boolean mIsLaunching;
    private final Runnable mLaunchRunnable = this::launchActionInternal;
    private final Runnable mResetLaunchingRunnable = () -> mIsLaunching = false;
    private final ThemeManager.ThemeChangeListener mThemeChangeListener = () -> {
        setIcon();
        setUpBackground();
    };

    private final AxBackdropBlurSurface mBlurSurface;
    private boolean mBlurDrawn;
    private boolean mCrossWindowBlurEnabled;
    private boolean mBlurEnabled = true;

    private final Consumer<Boolean> mCrossWindowBlurListener =
            this::onCrossWindowBlurEnabledChanged;

    public CompactSearchBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CompactSearchBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        mBlurSurface = new AxBackdropBlurSurface(this, ActivityContext.lookupContext(context));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mActionIcon = findViewById(R.id.gemini_icon);
        mActionText = findViewById(R.id.compact_search_bar_text);
        mInner = findViewById(R.id.compact_search_bar_inner);

        ItemInfo qsbInfo = createQsbItemInfo(new Intent("android.search.action.GLOBAL_SEARCH")
                .setPackage(Utilities.GSA_PACKAGE));
        setTag(qsbInfo);
        if (mInner != null) {
            mInner.setTag(qsbInfo);
        }

        updateBlurPreferences();
        setIcon();
        setUpBackground();
        setUpClick();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        cancelSearchClick();
        ThemeManager.INSTANCE.get(mContext).addChangeListener(mThemeChangeListener);
        LauncherPrefs.getPrefs(mContext).registerOnSharedPreferenceChangeListener(this);
        mCrossWindowBlurEnabled = CrossWindowBlurListeners.getInstance().isCrossWindowBlurEnabled();
        CrossWindowBlurListeners.getInstance().addListener(
                getContext().getMainExecutor(), mCrossWindowBlurListener);
        updateBlurPreferences();
        setUpBackground();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelSearchClick();
        ThemeManager.INSTANCE.get(mContext).removeChangeListener(mThemeChangeListener);
        LauncherPrefs.getPrefs(mContext).unregisterOnSharedPreferenceChangeListener(this);
        mBlurSurface.release();
        CrossWindowBlurListeners.getInstance().removeListener(mCrossWindowBlurListener);
        if (mInner != null) {
            mInner.setOnClickListener(null);
            mInner.setBackground(null);
        }
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
    }

    @Override
    protected boolean verifyDrawable(Drawable who) {
        return super.verifyDrawable(who);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawBlur(canvas);
        super.dispatchDraw(canvas);
    }

    private void drawBlur(Canvas canvas) {
        if (!canvas.isHardwareAccelerated() || mInner == null) {
            return;
        }

        boolean blurDrawn = false;
        if (mBlurEnabled && mBlurSurface.isActive() && mInner.isShown()
                && mInner.getWidth() > 0 && mInner.getHeight() > 0) {
            blurDrawn = mBlurSurface.drawRect(canvas, mInner.getLeft(), mInner.getTop(),
                    mInner.getRight(), mInner.getBottom(), getCornerRadius());
        }
        if (mBlurDrawn != blurDrawn) {
            mBlurDrawn = blurDrawn;
            setUpBackground();
        }
    }

    private boolean isBlurAvailable() {
        return mBlurEnabled && mBlurSurface.isActive();
    }

    private void onCrossWindowBlurEnabledChanged(boolean enabled) {
        mCrossWindowBlurEnabled = enabled;
        postInvalidate();
    }

    private void updateBlurPreferences() {
        mBlurEnabled = LauncherPrefs.get(mContext).get(LauncherPrefsExt.SEARCH_BAR_BLUR);
        postInvalidate();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (LauncherPrefsExt.COMPACT_SEARCH_BAR_ACTION.getSharedPrefKey().equals(key)
                || LauncherPrefsExt.HOTSEAT_QSB_OPACITY.getSharedPrefKey().equals(key)
                || LauncherPrefsExt.SEARCH_BAR_BLUR.getSharedPrefKey().equals(key)
                || LauncherPrefsExt.LAUNCHER_BLUR_RADIUS_PCT.getSharedPrefKey().equals(key)) {
            updateBlurPreferences();
            setIcon();
            setUpBackground();
        }
    }

    private void setIcon() {
        if (mActionIcon == null) return;
        boolean isThemed = ThemeManager.INSTANCE.get(mContext).isMonoThemeEnabled();
        switch (getAction()) {
            case ACTION_LENS:
                mActionIcon.setImageResource(isThemed
                        ? R.drawable.ic_lens_themed
                        : R.drawable.ic_lens_color);
                break;
            case ACTION_MIC:
                mActionIcon.setImageResource(isThemed
                        ? R.drawable.ic_mic_themed
                        : R.drawable.ic_mic_color);
                break;
            case ACTION_GEMINI:
                mActionIcon.setImageResource(isThemed
                        ? R.drawable.ic_gemini_themed
                        : R.drawable.ic_gemini_color);
                break;
            case ACTION_GOOGLE_SEARCH:
            default:
                mActionIcon.setImageResource(isThemed
                        ? R.drawable.ic_super_g_themed
                        : R.drawable.ic_super_g_color);
                break;
        }
    }

    private String getAction() {
        return LauncherPrefs.get(mContext).get(LauncherPrefsExt.COMPACT_SEARCH_BAR_ACTION);
    }

    private void setUpBackground() {
        if (mInner == null) return;

        float cornerRadius = getCornerRadius();
        boolean canBlur = mBlurDrawn && isBlurAvailable();
        int opacity = Utilities.boundToRange(
                LauncherPrefs.get(mContext).get(LauncherPrefsExt.HOTSEAT_QSB_OPACITY), 0, 100);
        int alphaValue = Math.round(255f * opacity / 100f * (canBlur ? 0.4f : 1f));

        boolean isThemed = ThemeManager.INSTANCE.get(mContext).isMonoThemeEnabled();
        int baseColor = isThemed
                ? Themes.getAttrColor(mContext, R.attr.qsbFillColorThemed)
                : Themes.getAttrColor(mContext, R.attr.qsbFillColor);

        if (baseColor == 0) {
            baseColor = Themes.getAttrColor(mContext, R.attr.allAppsSurfaceHighest);
        }

        PaintDrawable backgroundDrawable = new PaintDrawable(
                ColorUtils.setAlphaComponent(baseColor, alphaValue));
        backgroundDrawable.setCornerRadius(cornerRadius);

        PaintDrawable strokeDrawable = new PaintDrawable(Color.TRANSPARENT);
        strokeDrawable.setCornerRadius(cornerRadius);
        int strokeWidth = Math.max(1, Utilities.dpToPx(1f));
        strokeDrawable.getPaint().setStyle(Paint.Style.STROKE);
        strokeDrawable.getPaint().setStrokeWidth(strokeWidth);
        int strokeAlpha = Math.round((canBlur ? 35f : 20f) * (opacity / 100f));
        strokeDrawable.getPaint().setColor(Color.argb(strokeAlpha, 255, 255, 255));

        mInner.setBackground(new LayerDrawable(new Drawable[]{backgroundDrawable, strokeDrawable}));
        mInner.setClipToOutline(cornerRadius > 0);
    }

    private float getCornerRadius() {
        Resources res = mContext.getResources();
        return res.getDimension(R.dimen.compact_search_bar_height) / 2f;
    }

    private void setUpClick() {
        if (mInner != null) {
            mInner.setOnClickListener(v -> launchAction());
        }
    }

    public void playLandingSquishAnimation() {
        playSquishAnimation(true);
    }

    public void playSquishAnimation(boolean isLanding) {
        if (mInner == null) return;
        if (mSquishAnimator != null && mSquishAnimator.isRunning()) {
            mSquishAnimator.cancel();
        }
        mLandingSquish = isLanding;

        float bounceX = isLanding ? (1f + 0.08f * LANDING_SQUISH_INTENSITY) : 1.08f;
        float bounceY = isLanding ? (1f - 0.08f * LANDING_SQUISH_INTENSITY) : 0.92f;
        float reboundX = isLanding ? (1f - 0.03f * LANDING_SQUISH_INTENSITY) : 0.97f;
        float reboundY = isLanding ? (1f + 0.03f * LANDING_SQUISH_INTENSITY) : 1.03f;

        Keyframe kf0X = Keyframe.ofFloat(0f, 1f);
        Keyframe kf1X = Keyframe.ofFloat(0.35f, bounceX);
        kf1X.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        Keyframe kf2X = Keyframe.ofFloat(0.70f, reboundX);
        kf2X.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        Keyframe kf3X = Keyframe.ofFloat(1f, 1f);
        kf3X.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        PropertyValuesHolder pvhX = PropertyValuesHolder.ofKeyframe(View.SCALE_X, kf0X, kf1X, kf2X, kf3X);

        Keyframe kf0Y = Keyframe.ofFloat(0f, 1f);
        Keyframe kf1Y = Keyframe.ofFloat(0.35f, bounceY);
        kf1Y.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        Keyframe kf2Y = Keyframe.ofFloat(0.70f, reboundY);
        kf2Y.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        Keyframe kf3Y = Keyframe.ofFloat(1f, 1f);
        kf3Y.setInterpolator(Interpolators.FAST_OUT_SLOW_IN);
        PropertyValuesHolder pvhY = PropertyValuesHolder.ofKeyframe(View.SCALE_Y, kf0Y, kf1Y, kf2Y, kf3Y);

        mSquishAnimator = ObjectAnimator.ofPropertyValuesHolder(mInner, pvhX, pvhY);
        mSquishAnimator.setDuration(SQUISH_DURATION_MS);
        mSquishAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mInner != null) {
                    mInner.setScaleX(1f);
                    mInner.setScaleY(1f);
                }
                mSquishAnimator = null;
                mLandingSquish = false;
            }
        });
        mSquishAnimator.start();
    }

    private void cancelSearchClick() {
        removeCallbacks(mLaunchRunnable);
        removeCallbacks(mResetLaunchingRunnable);
        mIsLaunching = false;
    }

    private ItemInfo createQsbItemInfo(Intent intent) {
        return QsbActivityLauncher.createItemInfo(intent);
    }

    private void startActivitySafely(View view, Intent intent) {
        QsbActivityLauncher.startActivity(mContext, view, intent);
    }

    private void launchAction() {
        if (mIsLaunching) return;
        mIsLaunching = true;
        playSquishAnimation(false);
        postDelayed(mLaunchRunnable, ACTION_LAUNCH_DELAY_MS);
        postDelayed(mResetLaunchingRunnable, 1000L);
    }

    private void launchActionInternal() {
        switch (getAction()) {
            case ACTION_LENS:
                launchLens();
                break;
            case ACTION_MIC:
                launchMic();
                break;
            case ACTION_GEMINI:
                launchGemini();
                break;
            case ACTION_GOOGLE_SEARCH:
            default:
                launchSearchActivity();
                break;
        }
    }

    private void launchSearchActivity() {
        String searchPackage = QsbContainerView.getSearchWidgetPackageName(mContext);
        View target = mInner != null ? mInner : this;
        if (searchPackage != null) {
            try {
                Intent intent = new Intent("android.search.action.GLOBAL_SEARCH")
                        .setPackage(searchPackage);
                startActivitySafely(target, intent);
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.d(TAG, "GLOBAL_SEARCH not found for " + searchPackage);
            }

            try {
                Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(searchPackage);
                if (intent != null) {
                    startActivitySafely(target, intent);
                    return;
                }
            } catch (ActivityNotFoundException | SecurityException e) {
                Log.d(TAG, "Launch intent not found for " + searchPackage);
            }
        }

        // Generic fallback to web search or browser
        try {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            startActivitySafely(target, intent);
            return;
        } catch (Exception e) {
            Log.d(TAG, "Web search fallback failed", e);
        }

        Toast.makeText(mContext, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
    }

    private void launchLens() {
        View target = mInner != null ? mInner : this;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setComponent(new ComponentName(Utilities.GSA_PACKAGE, Utilities.LENS_ACTIVITY))
                    .setData(Uri.parse(Utilities.LENS_URI))
                    .putExtra("LensHomescreenShortcut", true);
            startActivitySafely(target, intent);
        } catch (Exception e) {
            Log.e(TAG, "Lens launch failed", e);
            Toast.makeText(mContext, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void launchMic() {
        View target = mInner != null ? mInner : this;
        try {
            Intent intent = new Intent();
            if (Utilities.isMusicSearchEnabled(mContext)) {
                intent.setAction("com.google.android.googlequicksearchbox.MUSIC_SEARCH");
                intent.setPackage(QsbContainerView.getSearchWidgetPackageName(mContext));
            } else {
                intent.setAction("android.intent.action.VOICE_COMMAND");
            }
            startActivitySafely(target, intent);
        } catch (Exception e) {
            Log.e(TAG, "Mic launch failed", e);
            Toast.makeText(mContext, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void launchGemini() {
        View target = mInner != null ? mInner : this;
        try {
            Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(
                    Utilities.GEMINI_PACKAGE);
            if (intent != null) {
                startActivitySafely(target, intent);
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Gemini launch failed", e);
        }
        Toast.makeText(mContext, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void setIconVisible(boolean visible) {
    }

    @Override
    public void setForceHideDot(boolean hide) {}

    @Override
    public MultiTranslateDelegate getTranslateDelegate() {
        return mTranslateDelegate;
    }

    @Override
    public void setReorderBounceScale(float scale) {
        mScaleForReorderBounce = scale;
        super.setScaleX(scale);
        super.setScaleY(scale);
    }

    @Override
    public float getReorderBounceScale() {
        return mScaleForReorderBounce;
    }

    /**
     * Cross-fades the compact search bar content (icon & text) with the workspace page dots.
     * When dotsReveal is 0, the search pill content is fully shown.
     * When dotsReveal is 1, the search pill content is fully hidden.
     */
    public void setDotsReveal(float dotsReveal) {
        float shown = 1f - Utilities.boundToRange(dotsReveal, 0f, 1f);
        float contentScale = 0.85f + 0.15f * shown;

        if (mActionIcon != null) {
            mActionIcon.setAlpha(shown);
            mActionIcon.setScaleX(contentScale);
            mActionIcon.setScaleY(contentScale);
        }
        if (mActionText != null) {
            mActionText.setAlpha(shown);
            mActionText.setScaleX(contentScale);
            mActionText.setScaleY(contentScale);
        }
        if (mInner != null) {
            mInner.setClickable(shown > 0.5f);
            mInner.setFocusable(shown > 0.5f);
            mInner.setImportantForAccessibility(shown > 0.5f
                    ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
    }
}
