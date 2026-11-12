/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.launcher3;

import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_BUBBLE_ADJUSTMENT_ANIM;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.os.Process;
import android.view.CrossWindowBlurListeners;

import com.android.launcher3.graphics.AxBackdropBlurSurface;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.qsb.CompactSearchBar;
import com.android.launcher3.util.Themes;

import java.util.function.Consumer;

import com.android.launcher3.ShortcutAndWidgetContainer.TranslationProvider;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.util.HorizontalInsettableView;
import com.android.launcher3.util.LauncherBindableItemsContainer.ItemOperator;
import com.android.launcher3.util.MultiPropertyFactory;
import com.android.launcher3.util.MultiPropertyFactory.MultiProperty;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.views.ActivityContext;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * View class that represents the bottom row of the home screen.
 */
public class Hotseat extends CellLayout implements Insettable {

    public static final int ALPHA_CHANNEL_TASKBAR_ALIGNMENT = 0;
    public static final int ALPHA_CHANNEL_PREVIEW_RENDERER = 1;
    public static final int ALPHA_CHANNEL_TASKBAR_STASH = 2;
    public static final int ALPHA_CHANNEL_ASSISTANT_VISIBILITY = 3;
    public static final int ALPHA_CHANNEL_STATE_VISIBILITY = 4;
    public static final int ALPHA_CHANNEL_CHANNELS_COUNT = 5;

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ALPHA_CHANNEL_TASKBAR_ALIGNMENT, ALPHA_CHANNEL_PREVIEW_RENDERER,
            ALPHA_CHANNEL_TASKBAR_STASH, ALPHA_CHANNEL_ASSISTANT_VISIBILITY,
            ALPHA_CHANNEL_STATE_VISIBILITY})
    public @interface HotseatQsbAlphaId {
    }

    public static final int ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT = 0;
    public static final int ICONS_TRANSLATION_X_CHANNELS_COUNT = 1;

    @Retention(RetentionPolicy.RUNTIME)
    @IntDef({ICONS_TRANSLATION_X_NAV_BAR_ALIGNMENT})
    public @interface IconsTranslationX {
    }

    // Ratio of empty space, qsb should take up to appear visually centered.
    public static final float QSB_CENTER_FACTOR = .325f;
    private static final int BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS = 250;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHasVerticalHotseat;
    private Workspace<?> mWorkspace;
    private boolean mSendTouchToWorkspace;
    private final MultiValueAlpha mIconsAlphaChannels;
    private final MultiValueAlpha mQsbAlphaChannels;

    private @Nullable MultiProperty mQsbTranslationX;

    private final MultiPropertyFactory mIconsTranslationXFactory;

    private final View mQsb;
    private final Rect mInsets = new Rect();

    private static final float BACKGROUND_ICON_PADDING_DP = 10f;
    private static final float BACKGROUND_END_PADDING_DP = 14f;

    public static final String HOTSEAT_SHAPE_CAPSULE = "capsule";
    public static final String HOTSEAT_SHAPE_ROUNDED_SQUARE = "rounded_square";

    private final AxBackdropBlurSurface mBlurSurface;
    private final GradientDrawable mBackgroundTintDrawable = new GradientDrawable();
    private final Rect mPillRect = new Rect();
    private final Rect mTempCellRect1 = new Rect();
    private final Rect mTempCellRect2 = new Rect();

    private boolean mShowHotseatBg;
    private boolean mHotseatBlur;
    private String mHotseatShape = HOTSEAT_SHAPE_CAPSULE;
    private int mBlurDepth;
    private boolean mCrossWindowBlurEnabled;
    private final Consumer<Boolean> mCrossWindowBlurListener = this::onCrossWindowBlurEnabledChanged;
    private final LauncherPrefChangeListener mPrefChangeListener = key -> updateBackgroundPreferences();

    public Hotseat(Context context) {
        this(context, null);
    }

    public Hotseat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Hotseat(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mBlurSurface = new AxBackdropBlurSurface(this, mActivity);
        mQsb = createQsbView(context);
        bindQsbTag(mQsb);

        addView(mQsb);
        mIconsAlphaChannels = new MultiValueAlpha(getShortcutsAndWidgets(),
                ALPHA_CHANNEL_CHANNELS_COUNT);
        mIconsAlphaChannels.setUpdateVisibility(true);
        if (mQsb instanceof Reorderable qsbReorderable) {
            mQsbTranslationX = qsbReorderable.getTranslateDelegate()
                    .getTranslationX(MultiTranslateDelegate.INDEX_NAV_BAR_ANIM);
        }
        mIconsTranslationXFactory = new MultiPropertyFactory<>(getShortcutsAndWidgets(),
                VIEW_TRANSLATE_X, ICONS_TRANSLATION_X_CHANNELS_COUNT, Float::sum);
        mQsbAlphaChannels = new MultiValueAlpha(mQsb, ALPHA_CHANNEL_CHANNELS_COUNT);
        mQsbAlphaChannels.setUpdateVisibility(true);
    }

    private View createQsbView(Context context) {
        if (isCompactSearchBarEnabled()) {
            return LayoutInflater.from(context).inflate(
                    R.layout.search_container_hotseat_compact, this, false);
        }
        return LayoutInflater.from(context).inflate(
                R.layout.qsb_container_hotseat, this, false);
    }

    public boolean isCompactSearchBarEnabled() {
        return LauncherPrefs.get(getContext()).get(LauncherPrefsExt.COMPACT_SEARCH_BAR);
    }

    private void bindQsbTag(View view) {
        if (view == null) return;
        WorkspaceItemInfo qsbInfo = new WorkspaceItemInfo();
        qsbInfo.id = R.id.search_container_hotseat;
        qsbInfo.container = LauncherSettings.Favorites.CONTAINER_HOTSEAT;
        qsbInfo.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        qsbInfo.user = Process.myUserHandle();
        qsbInfo.intent = new Intent("android.search.action.GLOBAL_SEARCH")
                .setPackage(Utilities.GSA_PACKAGE)
                .setComponent(new ComponentName(Utilities.GSA_PACKAGE, ""));
        view.setTag(qsbInfo);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child.getTag() == null) {
                    child.setTag(qsbInfo);
                }
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mCrossWindowBlurEnabled = CrossWindowBlurListeners.getInstance().isCrossWindowBlurEnabled();
        CrossWindowBlurListeners.getInstance().addListener(
                getContext().getMainExecutor(), mCrossWindowBlurListener);
        LauncherPrefs.get(getContext()).addListener(mPrefChangeListener,
                LauncherPrefsExt.SHOW_HOTSEAT_BG,
                LauncherPrefsExt.HOTSEAT_BLUR,
                LauncherPrefsExt.HOTSEAT_SHAPE,
                LauncherPrefsExt.HOTSEAT_OPACITY,
                LauncherPrefsExt.LAUNCHER_BLUR_RADIUS_PCT);
        updateBackgroundPreferences();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mBlurSurface.release();
        CrossWindowBlurListeners.getInstance().removeListener(mCrossWindowBlurListener);
        LauncherPrefs.get(getContext()).removeListener(mPrefChangeListener,
                LauncherPrefsExt.SHOW_HOTSEAT_BG,
                LauncherPrefsExt.HOTSEAT_BLUR,
                LauncherPrefsExt.HOTSEAT_SHAPE,
                LauncherPrefsExt.HOTSEAT_OPACITY,
                LauncherPrefsExt.LAUNCHER_BLUR_RADIUS_PCT);
    }

    public void updateBackgroundPreferences() {
        Context context = getContext();
        mShowHotseatBg = LauncherPrefs.get(context).get(LauncherPrefsExt.SHOW_HOTSEAT_BG);
        mHotseatBlur = LauncherPrefs.get(context).get(LauncherPrefsExt.HOTSEAT_BLUR);
        mHotseatShape = LauncherPrefs.get(context).get(LauncherPrefsExt.HOTSEAT_SHAPE);
        mBlurDepth = Math.max(0, LauncherPrefs.get(context).get(LauncherPrefsExt.LAUNCHER_BLUR_RADIUS_PCT));
        postInvalidate();
    }

    private void onCrossWindowBlurEnabledChanged(boolean enabled) {
        mCrossWindowBlurEnabled = enabled;
        postInvalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!mShowHotseatBg || (mActivity != null && mActivity.getDeviceProfile().isVerticalBarLayout())) {
            super.dispatchDraw(canvas);
            return;
        }

        updatePillBounds();
        float cornerRadius;
        if (HOTSEAT_SHAPE_ROUNDED_SQUARE.equals(mHotseatShape)) {
            float maxRadius = Math.min(mPillRect.width(), mPillRect.height()) / 2f;
            float defaultRadius = getContext().getResources().getDimension(
                    R.dimen.hotseat_rounded_square_corner_radius);
            cornerRadius = Math.min(maxRadius, defaultRadius);
        } else {
            cornerRadius = Math.min(mPillRect.width(), mPillRect.height()) / 2f;
        }

        if (canvas.isHardwareAccelerated() && mHotseatBlur && mBlurSurface.isActive()) {
            mBlurSurface.drawRect(canvas, mPillRect.left, mPillRect.top,
                    mPillRect.right, mPillRect.bottom, cornerRadius);
        }

        if (mBackgroundTintDrawable.getCornerRadius() != cornerRadius) {
            mBackgroundTintDrawable.setCornerRadius(cornerRadius);
        }
        mBackgroundTintDrawable.setBounds(mPillRect);

        float iconAlpha = Utilities.boundToRange(mShortcutsAndWidgets.getAlpha(), 0f, 1f);
        Context context = getContext();
        int baseColor;
        try {
            baseColor = context.getColor(R.color.appseat_color);
        } catch (Exception e) {
            baseColor = Themes.getAttrColor(context, R.attr.allAppsSurfaceHighest);
        }

        int opacity = Utilities.boundToRange(LauncherPrefs.get(context).get(LauncherPrefsExt.HOTSEAT_OPACITY), 0, 100);
        int alpha = Math.round(255f * (opacity / 100f) * iconAlpha);
        int tintColor = ColorUtils.setAlphaComponent(baseColor, alpha);

        mBackgroundTintDrawable.setColor(tintColor);
        int strokeColor = ColorUtils.setAlphaComponent(baseColor, Math.round(alpha * 0.5f));
        mBackgroundTintDrawable.setStroke(Math.round(Utilities.dpToPx(1f, context.getResources().getDisplayMetrics())), strokeColor);
        mBackgroundTintDrawable.draw(canvas);

        super.dispatchDraw(canvas);
    }

    private void updatePillBounds() {
        if (mShortcutsAndWidgets.getChildCount() == 0 || mCountX == 0 || mCountY == 0) {
            mPillRect.setEmpty();
            return;
        }

        int minCellX = Integer.MAX_VALUE;
        int maxCellX = Integer.MIN_VALUE;
        int minCellY = Integer.MAX_VALUE;
        int maxCellY = Integer.MIN_VALUE;

        for (int i = 0; i < mShortcutsAndWidgets.getChildCount(); i++) {
            View child = mShortcutsAndWidgets.getChildAt(i);
            if (child.getVisibility() == GONE || !(child.getLayoutParams() instanceof CellLayoutLayoutParams lp)) {
                continue;
            }
            minCellX = Math.min(minCellX, lp.getCellX());
            maxCellX = Math.max(maxCellX, lp.getCellX() + lp.cellHSpan - 1);
            minCellY = Math.min(minCellY, lp.getCellY());
            maxCellY = Math.max(maxCellY, lp.getCellY() + lp.cellVSpan - 1);
        }

        if (minCellX > maxCellX || minCellY > maxCellY) {
            mPillRect.setEmpty();
            return;
        }

        cellToRect(minCellX, minCellY, 1, 1, mTempCellRect1);
        cellToRect(maxCellX, maxCellY, 1, 1, mTempCellRect2);

        Context context = getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int paddingX = Math.round(BACKGROUND_END_PADDING_DP * density);
        int paddingY = Math.round(BACKGROUND_ICON_PADDING_DP * density);

        int iconSize = mActivity.getDeviceProfile().getHotseatProfile().getIconSizePx();
        View container = mShortcutsAndWidgets;
        int containerTx = container != null ? Math.round(container.getTranslationX()) : 0;
        int containerTy = container != null ? Math.round(container.getTranslationY()) : 0;
        mPillRect.set(
                Math.round(Math.min(mTempCellRect1.exactCenterX(), mTempCellRect2.exactCenterX())
                        - iconSize / 2f - paddingX + containerTx),
                Math.round(Math.min(mTempCellRect1.exactCenterY(), mTempCellRect2.exactCenterY())
                        - iconSize / 2f - paddingY + containerTy),
                Math.round(Math.max(mTempCellRect1.exactCenterX(), mTempCellRect2.exactCenterX())
                        + iconSize / 2f + paddingX + containerTx),
                Math.round(Math.max(mTempCellRect1.exactCenterY(), mTempCellRect2.exactCenterY())
                        + iconSize / 2f + paddingY + containerTy));
    }

    @Nullable
    public View getQsbForAppClose() {
        if (mQsb == null || mQsb.getVisibility() != VISIBLE) {
            return null;
        }
        if (mQsb instanceof CompactSearchBar) {
            View inner = mQsb.findViewById(R.id.compact_search_bar_inner);
            return inner != null ? inner : mQsb;
        }
        View inner = mQsb.findViewById(R.id.inner);
        if (inner != null) return inner;
        return mQsb;
    }

    public boolean isQsbView(@Nullable View v) {
        if (v == null || getQsbForAppClose() == null) return false;
        while (v != null) {
            if (v == mQsb) return true;
            v = v.getParent() instanceof View parent ? parent : null;
        }
        return false;
    }

    public void setQsbItemInfo(ItemInfo info) {
        if (mQsb != null) {
            mQsb.setTag(info);
            View target = getQsbForAppClose();
            if (target != null) target.setTag(info);
        }
    }

    public void playQsbSquishAnimation() {
        if (mQsb instanceof CompactSearchBar compactBar) {
            compactBar.playLandingSquishAnimation();
        }
    }


    /** Provides translation X for hotseat icons for the channel. */
    public MultiProperty getIconsTranslationX(@IconsTranslationX int channelId) {
        return mIconsTranslationXFactory.get(channelId);
    }

    /** Provides translation X for hotseat Qsb. */
    @Nullable
    public MultiProperty getQsbTranslationX() {
        return mQsbTranslationX;
    }

    /**
     * Returns orientation specific cell X given invariant order in the hotseat
     */
    public int getCellXFromOrder(int rank) {
        return mHasVerticalHotseat ? 0 : rank;
    }

    /**
     * Returns orientation specific cell Y given invariant order in the hotseat
     */
    public int getCellYFromOrder(int rank) {
        return mHasVerticalHotseat ? (getCountY() - (rank + 1)) : 0;
    }

    boolean isHasVerticalHotseat() {
        return mHasVerticalHotseat;
    }

    public void resetLayout(boolean hasVerticalHotseat) {
        ActivityContext activityContext = ActivityContext.lookupContext(getContext());
        boolean bubbleBarEnabled = activityContext.isBubbleBarEnabled();
        boolean hasBubbles = activityContext.hasBubbles();
        removeAllViewsInLayout();
        mHasVerticalHotseat = hasVerticalHotseat;
        DeviceProfile dp = mActivity.getDeviceProfile();

        if (bubbleBarEnabled) {
            if (dp.shouldAdjustHotseatForBubbleBar(getContext(), hasBubbles)) {
                getShortcutsAndWidgets().setTranslationProvider(
                        cellX -> dp.getHotseatAdjustedTranslation(getContext(), cellX));
                if (dp.isHotseatQsbEnabled()
                        && dp.hotseatQsbWidth > 0
                        && mQsb instanceof HorizontalInsettableView) {
                    HorizontalInsettableView insettableQsb = (HorizontalInsettableView) mQsb;
                    final float insetFraction =
                            (float) dp.getWorkspaceIconProfile().getIconSizePx()
                                    / dp.hotseatQsbWidth;
                    // post this to the looper so that QSB has a chance to redraw itself, e.g.
                    // after device rotation
                    mQsb.post(() -> insettableQsb.setHorizontalInsets(insetFraction));
                }
            } else {
                getShortcutsAndWidgets().setTranslationProvider(null);
                if (mQsb instanceof HorizontalInsettableView) {
                    ((HorizontalInsettableView) mQsb).setHorizontalInsets(0);
                }
            }
        }

        resetCellSize(dp);
        if (hasVerticalHotseat) {
            setGridSize(1, dp.numShownHotseatIcons);
        } else {
            setGridSize(dp.numShownHotseatIcons, 1);
        }
    }

    /**
     * Adjust the hotseat icons for the bubble bar.
     *
     * <p>When the bubble bar becomes visible, if needed, this method animates the hotseat icons
     * to reduce the spacing between them and make room for the bubble bar. The QSB width is
     * animated as well to align with the hotseat icons.
     *
     * <p>When the bubble bar goes away, any adjustments that were previously made are reversed.
     */
    public void adjustForBubbleBar(boolean isBubbleBarVisible) {
        DeviceProfile dp = mActivity.getDeviceProfile();
        boolean shouldAdjust = isBubbleBarVisible
                && dp.shouldAdjustHotseatOrQsbForBubbleBar(getContext());
        boolean shouldAdjustHotseat = shouldAdjust
                && dp.shouldAlignBubbleBarWithHotseat();
        ShortcutAndWidgetContainer icons = getShortcutsAndWidgets();
        // update the translation provider for future layout passes of hotseat icons.
        if (shouldAdjustHotseat) {
            icons.setTranslationProvider(
                    cellX -> dp.getHotseatAdjustedTranslation(getContext(), cellX));
        } else {
            icons.setTranslationProvider(null);
        }
        AnimatorSet animatorSet = new AnimatorSet();
        for (int i = 0; i < icons.getChildCount(); i++) {
            View child = icons.getChildAt(i);
            if (child.getLayoutParams() instanceof CellLayoutLayoutParams lp) {
                float tx = shouldAdjustHotseat
                        ? dp.getHotseatAdjustedTranslation(getContext(), lp.getCellX()) : 0;
                if (child instanceof Reorderable) {
                    MultiTranslateDelegate mtd = ((Reorderable) child).getTranslateDelegate();
                    animatorSet.play(
                            mtd.getTranslationX(INDEX_BUBBLE_ADJUSTMENT_ANIM).animateToValue(tx));
                } else {
                    animatorSet.play(ObjectAnimator.ofFloat(child, VIEW_TRANSLATE_X, tx));
                }
            }
        }
        //TODO(b/381109832) refactor & simplify adjustment logic
        boolean shouldAdjustQsb =
                dp.isHotseatQsbEnabled()
                        && dp.hotseatQsbWidth > 0
                        && (shouldAdjustHotseat
                                || (shouldAdjust && dp.shouldAlignBubbleBarWithQSB()));
        if (mQsb instanceof HorizontalInsettableView horizontalInsettableQsb) {
            final float currentInsetFraction = horizontalInsettableQsb.getHorizontalInsets();
            final float targetInsetFraction = shouldAdjustQsb
                    ? (float) dp.getWorkspaceIconProfile().getIconSizePx() / dp.hotseatQsbWidth : 0;
            ValueAnimator qsbAnimator =
                    ValueAnimator.ofFloat(currentInsetFraction, targetInsetFraction);
            qsbAnimator.addUpdateListener(animation -> {
                float insetFraction = (float) animation.getAnimatedValue();
                horizontalInsettableQsb.setHorizontalInsets(insetFraction);
            });
            animatorSet.play(qsbAnimator);
        }
        animatorSet.setDuration(BUBBLE_BAR_ADJUSTMENT_ANIMATION_DURATION_MS).start();
    }

    @Override
    protected int getTranslationXForCell(int cellX, int cellY) {
        TranslationProvider translationProvider = getShortcutsAndWidgets().getTranslationProvider();
        if (translationProvider == null) return 0;
        return (int) translationProvider.getTranslationX(cellX);
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
        DeviceProfile grid = mActivity.getDeviceProfile();
        grid.updateHotseatQsbEnabled(getContext());
        int extraQsbTopInset = 0;

        if (grid.isVerticalBarLayout()) {
            mQsb.setVisibility(View.GONE);
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (grid.isSeascape()) {
                lp.gravity = Gravity.LEFT;
                lp.width = grid.hotseatBarSizePx + insets.left;
            } else {
                lp.gravity = Gravity.RIGHT;
                lp.width = grid.hotseatBarSizePx + insets.right;
            }
        } else {
            boolean isCompact = isCompactSearchBarEnabled();
            boolean showQsb = grid.isHotseatQsbEnabled() || isCompact;
            mQsb.setVisibility(showQsb ? View.VISIBLE : View.GONE);
            lp.gravity = Gravity.BOTTOM;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            if (showQsb && !isCompact) {
                extraQsbTopInset = Math.max(0,
                        (grid.getQsbOffsetY() + grid.getHotseatQsbHeight())
                                - grid.hotseatBarSizePx);
            }
            lp.height = grid.hotseatBarSizePx + extraQsbTopInset;
        }

        Rect padding = grid.getHotseatLayoutPadding(getContext());
        if (isCompactSearchBarEnabled() && !grid.isVerticalBarLayout()) {
            int compactBarHeight = getResources().getDimensionPixelSize(
                    R.dimen.compact_search_bar_height);

            int targetBottomSpace = Math.max(
                    insets.bottom + getResources().getDimensionPixelSize(
                            R.dimen.compact_search_bar_dock_bottom_inset),
                    getResources().getDimensionPixelSize(
                            R.dimen.compact_search_bar_dock_bottom_min));
            padding.bottom = targetBottomSpace;

            int pageIndicatorGap = getResources().getDimensionPixelSize(
                    R.dimen.workspace_page_indicator_height)
                    - getResources().getDimensionPixelSize(
                    R.dimen.workspace_page_indicator_overlap_workspace);

            int totalInterRowSpace = grid.hotseatBarSizePx + pageIndicatorGap
                    - grid.hotseatCellHeightPx - padding.bottom;

            int minGap = getResources().getDimensionPixelSize(
                    R.dimen.compact_search_bar_gap_min);
            int symmetricGap = Math.max(minGap, (totalInterRowSpace - compactBarHeight) / 2);

            padding.top = symmetricGap * 2 + compactBarHeight;
            lp.height = padding.top + grid.hotseatCellHeightPx + padding.bottom;
        }
        setPadding(padding.left, padding.top + extraQsbTopInset, padding.right, padding.bottom);
        setLayoutParams(lp);
        InsettableFrameLayout.dispatchInsets(this, insets);
    }

    public void refreshQsbLayout() {
        boolean changed = mActivity.getDeviceProfile().updateHotseatQsbEnabled(getContext());
        if (changed) {
            View rootView = mActivity.getRootView();
            if (rootView instanceof LauncherRootView launcherRootView) {
                launcherRootView.dispatchInsets();
            } else {
                setInsets(mInsets);
            }
            mActivity.dispatchDeviceProfileChanged();
        }
        requestLayout();
    }

    public void setWorkspace(Workspace<?> w) {
        mWorkspace = w;
        setCellLayoutContainer(w);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // We allow horizontal workspace scrolling from within the Hotseat. We do this by delegating
        // touch intercept the Workspace, and if it intercepts, delegating touch to the Workspace
        // for the remainder of the this input stream.
        int yThreshold = getMeasuredHeight() - getPaddingBottom();
        if (mWorkspace != null && ev.getY() <= yThreshold) {
            mSendTouchToWorkspace = mWorkspace.onInterceptTouchEvent(ev);
            return mSendTouchToWorkspace;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // See comment in #onInterceptTouchEvent
        if (mSendTouchToWorkspace) {
            final int action = event.getAction();
            switch (action & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mSendTouchToWorkspace = false;
            }
            return mWorkspace.onTouchEvent(event);
        }
        // Always let touch follow through to Workspace.
        return false;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        DeviceProfile dp = mActivity.getDeviceProfile();
        int qsbWidth = isCompactSearchBarEnabled() && !dp.isVerticalBarLayout()
                ? MeasureSpec.getSize(widthMeasureSpec)
                : (dp.isHotseatQsbEnabled() ? dp.hotseatQsbWidth : 0);
        int qsbHeight = isCompactSearchBarEnabled()
                ? getResources().getDimensionPixelSize(R.dimen.compact_search_bar_height)
                : (dp.isHotseatQsbEnabled() ? dp.getHotseatProfile().getQsbHeight() : dp.getHotseatQsbHeight());
        mQsb.measure(makeMeasureSpec(qsbWidth, MeasureSpec.EXACTLY),
                makeMeasureSpec(qsbHeight, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        int qsbMeasuredWidth = mQsb.getMeasuredWidth();
        int qsbMeasuredHeight = mQsb.getMeasuredHeight();
        if (qsbMeasuredWidth == 0 || qsbMeasuredHeight == 0) {
            mQsb.layout(0, 0, 0, 0);
            return;
        }
        int left;
        DeviceProfile dp = mActivity.getDeviceProfile();
        if (dp.isQsbInline) {
            int qsbSpace = dp.hotseatBorderSpace;
            left = Utilities.isRtl(getResources()) ? r - getPaddingRight() + qsbSpace
                    : l + getPaddingLeft() - qsbMeasuredWidth - qsbSpace;
        } else {
            left = (r - l - qsbMeasuredWidth) / 2;
        }
        int right = left + qsbMeasuredWidth;

        int top;
        int bottom;
        if (isCompactSearchBarEnabled() && !dp.isQsbInline) {
            int compactBarTopMargin = Math.max(0,
                    (getPaddingTop() - qsbMeasuredHeight) / 2);
            top = compactBarTopMargin;
            bottom = top + qsbMeasuredHeight;
        } else {
            bottom = b - t - dp.getQsbOffsetY();
            top = bottom - qsbMeasuredHeight;
        }
        mQsb.layout(left, top, right, bottom);
    }

    /**
     * Sets the alpha value of the specified alpha channel of just our ShortcutAndWidgetContainer.
     */
    public void setIconsAlpha(float alpha, @HotseatQsbAlphaId int channelId) {
        getIconsAlpha(channelId).setValue(alpha);
        invalidate();
    }

    /**
     * Sets the alpha value of just our QSB.
     */
    public void setQsbAlpha(float alpha, @HotseatQsbAlphaId int channelId) {
        getQsbAlpha(channelId).setValue(alpha);
    }

    /** Returns the alpha channel for ShortcutAndWidgetContainer */
    public MultiProperty getIconsAlpha(@HotseatQsbAlphaId int channelId) {
        return mIconsAlphaChannels.get(channelId);
    }

    /** Returns the alpha channel for Qsb */
    public MultiProperty getQsbAlpha(@HotseatQsbAlphaId int channelId) {
        return mQsbAlphaChannels.get(channelId);
    }

    /**
     * Returns the QSB inside hotseat
     */
    public View getQsb() {
        return mQsb;
    }

    @Nullable
    @Override
    public View mapOverItems(ItemOperator op) {
        if (mQsb != null
                && mQsb.getTag() instanceof ItemInfo info
                && op.evaluate(info, mQsb)) {
            return mQsb;
        }
        return super.mapOverItems(op);
    }

    /** Dumps the Hotseat internal state */
    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "Hotseat:");
        mIconsAlphaChannels.dump(
                prefix + "\t",
                writer,
                "mIconsAlphaChannels",
                "ALPHA_CHANNEL_TASKBAR_ALIGNMENT",
                "ALPHA_CHANNEL_PREVIEW_RENDERER",
                "ALPHA_CHANNEL_TASKBAR_STASH");
        mQsbAlphaChannels.dump(
                prefix + "\t",
                writer,
                "mQsbAlphaChannels",
                "ALPHA_CHANNEL_TASKBAR_ALIGNMENT",
                "ALPHA_CHANNEL_PREVIEW_RENDERER",
                "ALPHA_CHANNEL_TASKBAR_STASH"
        );
    }

}
