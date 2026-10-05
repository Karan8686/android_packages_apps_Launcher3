/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static android.graphics.fonts.FontStyle.FONT_WEIGHT_BOLD;
import static android.graphics.fonts.FontStyle.FONT_WEIGHT_NORMAL;
import static android.text.Layout.Alignment.ALIGN_NORMAL;

import static com.android.launcher3.BubbleTextView.RunningAppState.MINIMIZED;
import static com.android.launcher3.BubbleTextView.RunningAppState.RUNNING;
import static com.android.launcher3.Flags.enableContrastTiles;
import static com.android.launcher3.Flags.enableScalabilityForDesktopExperience;
import static com.android.launcher3.LauncherPrefsExt.ALLAPPS_THEMED_ICONS;
import static com.android.launcher3.LauncherPrefsExt.SHOW_DESKTOP_LABELS;
import static com.android.launcher3.LauncherPrefsExt.SHOW_DRAWER_LABELS;
import static com.android.launcher3.graphics.PreloadIconDelegate.extractPreloadDelegate;
import static com.android.launcher3.graphics.PreloadIconDelegate.hasPendingAnimationCompleted;
import static com.android.launcher3.graphics.PreloadIconDelegate.newPendingIcon;
import static com.android.launcher3.icons.BitmapInfo.FLAG_NO_BADGE;
import static com.android.launcher3.icons.BitmapInfo.FLAG_SKIP_USER_BADGE;
import static com.android.launcher3.icons.BitmapInfo.FLAG_THEMED;
import static com.android.launcher3.icons.GraphicsUtils.setColorAlphaBound;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;
import static com.android.launcher3.icons.cache.CacheLookupFlag.DEFAULT_LOOKUP_FLAG;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_INCREMENTAL_DOWNLOAD_ACTIVE;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_INSTALL_SESSION_ACTIVE;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_SHOW_DOWNLOAD_PROGRESS_MASK;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;
import static com.android.launcher3.util.MultiTranslateDelegate.INDEX_TASKBAR_APP_RUNNING_STATE_ANIM;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.icu.text.MessageFormat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.style.ImageSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.util.Property;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewDebug;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.annotation.VisibleForTesting;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.android.launcher3.accessibility.BaseAccessibilityDelegate;
import com.android.launcher3.celllayout.CellLayoutLayoutParams;
import com.android.launcher3.dot.DotInfo;
import com.android.launcher3.dragndrop.DragOptions.PreDragCondition;
import com.android.launcher3.dragndrop.DraggableView;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.graphics.PreloadIconDelegate;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.icons.BitmapInfo.DrawableCreationFlags;
import com.android.launcher3.icons.DotRenderer;
import com.android.launcher3.icons.DotRenderer.IconShapeInfo;
import com.android.launcher3.icons.FastBitmapDrawable;
import com.android.launcher3.icons.IconCache.ItemInfoUpdateReceiver;
import com.android.launcher3.icons.LauncherIcons;
import com.android.launcher3.icons.PlaceHolderDrawableDelegate;
import com.android.launcher3.icons.cache.CacheLookupFlag;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.ItemInfoWithIcon;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.Poppable;
import com.android.launcher3.popup.PoppableType;
import com.android.launcher3.popup.Popup;
import com.android.launcher3.popup.PopupController;
import com.android.launcher3.search.StringMatcherUtility;
import com.android.launcher3.shortcuts.SuperIconShortcutHelper;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.util.CancellableTask;
import com.android.launcher3.util.IntArray;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.SafeCloseable;
import com.android.launcher3.util.ShortcutUtil;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.FloatingIconViewCompanion;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * TextView that draws a bubble behind the text. We cannot use a LineBackgroundSpan
 * because we want to make the bubble taller than the text and TextView's clip is
 * too aggressive.
 */
public class BubbleTextView extends TextView implements ItemInfoUpdateReceiver,
        FloatingIconViewCompanion, DraggableView, Reorderable, Poppable {

    public static final String TAG = "BubbleTextView";

    public static final int DISPLAY_WORKSPACE = 0;
    public static final int DISPLAY_ALL_APPS = 1;
    public static final int DISPLAY_FOLDER = 2;
    public static final int DISPLAY_TASKBAR = 5;
    public static final int DISPLAY_SEARCH_RESULT = 6;
    public static final int DISPLAY_SEARCH_RESULT_SMALL = 7;
    public static final int DISPLAY_PREDICTION_ROW = 8;
    public static final int DISPLAY_SEARCH_RESULT_APP_ROW = 9;

    private static final float MIN_LETTER_SPACING = -0.05f;
    private static final int MAX_SEARCH_LOOP_COUNT = 20;
    private static final Character NEW_LINE = '\n';
    private static final String EMPTY = "";
    private static final StringMatcherUtility.StringMatcher MATCHER =
            StringMatcherUtility.StringMatcher.getInstance();
    private static final int BOLD_TEXT_ADJUSTMENT = FONT_WEIGHT_BOLD - FONT_WEIGHT_NORMAL;

    private static final int[] STATE_PRESSED = new int[]{android.R.attr.state_pressed};

    private float mScaleForReorderBounce = 1f;

    private IntArray mBreakPointsIntArray;
    private CharSequence mLastOriginalText;
    private CharSequence mLastModifiedText;

    private static final Property<BubbleTextView, Float> DOT_SCALE_PROPERTY
            = new Property<BubbleTextView, Float>(Float.TYPE, "dotScale") {
        @Override
        public Float get(BubbleTextView bubbleTextView) {
            return bubbleTextView.mDotParams.scale;
        }

        @Override
        public void set(BubbleTextView bubbleTextView, Float value) {
            bubbleTextView.mDotParams.scale = value;
            bubbleTextView.invalidate();
        }
    };

    public static final Property<BubbleTextView, Float> TEXT_ALPHA_PROPERTY
            = new Property<BubbleTextView, Float>(Float.class, "textAlpha") {
        @Override
        public Float get(BubbleTextView bubbleTextView) {
            return bubbleTextView.mTextAlpha;
        }

        @Override
        public void set(BubbleTextView bubbleTextView, Float alpha) {
            bubbleTextView.setTextAlpha(alpha);
        }
    };

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    protected final ActivityContext mActivity;
    private FastBitmapDrawable mIcon;
    private DeviceProfile mDeviceProfile;
    private boolean mCenterVertically;

    protected int mDisplay;

    private final CheckLongPressHelper mLongPressHelper;
    private final Rect mTempIconBounds = new Rect();
    private com.android.launcher3.graphics.SelectionIndicatorRenderer mSelectionRenderer;

    private boolean mLayoutHorizontal;
    private final boolean mIsRtl;
    private final int mIconSize;

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mHideBadge = false;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mSkipUserBadge = false;
    @ViewDebug.ExportedProperty(category = "launcher")
    protected boolean mIsIconVisible = true;
    @ViewDebug.ExportedProperty(category = "launcher")
    private int mTextColor;
    @ViewDebug.ExportedProperty(category = "launcher")
    private ColorStateList mTextColorStateList;
    @ViewDebug.ExportedProperty(category = "launcher")
    private float mTextAlpha = 1;

    @ViewDebug.ExportedProperty(category = "launcher")
    private DotInfo mDotInfo;
    private final DotRenderer mDotRenderer;
    @ViewDebug.ExportedProperty(category = "launcher", deepExport = true)
    protected final DotRenderer.DrawParams mDotParams;
    private Animator mDotScaleAnim;
    private boolean mForceHideDot;
    private boolean mIsShowingMinimalPopup;

    // These fields, related to showing running apps, are only used for Taskbar.
    private final int mRunningAppIndicatorHeight;
    private final int mRunningAppIndicatorTopMargin;
    private final Paint mRunningAppIndicatorPaint;
    private final Rect mRunningAppIconBounds = new Rect();
    private RunningAppState mRunningAppState;

    @ViewDebug.ExportedProperty(category = "launcher")
    private int mLineIndicatorColor;
    @ViewDebug.ExportedProperty(category = "launcher")
    private float mLineIndicatorWidth;

    private final String mMinimizedStateDescription;
    private final String mRunningStateDescription;

    @NonNull
    @Override
    public PoppableType getPoppableType() {
        return PoppableType.APP;
    }

    /**
     * Various options for the running state of an app.
     */
    public enum RunningAppState {
        NOT_RUNNING,
        RUNNING,
        MINIMIZED,
    }

    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mStayPressed;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mIgnorePressedStateChange;
    @ViewDebug.ExportedProperty(category = "launcher")
    private boolean mDisableRelayout = false;

    private boolean mShouldShowLabel;
    private boolean mThemeAllAppsIcons;
    private boolean mIsInAllAppsFolder;

    private CancellableTask mIconLoadRequest;

    private boolean mHighResUpdateInProgress = false;

    private AdaptiveIconDrawable mSuperIconAdaptiveDrawable;
    private Drawable mSuperIconBadge;
    private boolean mIsLoadingSuperIcon = false;
    private List<WorkspaceItemInfo> mSuperIconShortcuts;
    private boolean mIsLoadingShortcuts = false;
    private final RectF[] mSlotBounds = new RectF[]{new RectF(), new RectF(), new RectF(), new RectF()};
    private final float[] mSlotPressScales = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
    private final ValueAnimator[] mSlotPressAnimators = new ValueAnimator[4];
    private int mActivePressedSlot = -1;
    private int mLastClickedSlot = -1;

    public BubbleTextView(Context context) {
        this(context, null, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mActivity = ActivityContext.lookupContext(context);
        mMinimizedStateDescription = getContext().getString(
                R.string.app_minimized_state_description);
        mRunningStateDescription = getContext().getString(R.string.app_running_state_description);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.BubbleTextView, defStyle, 0);
        mLayoutHorizontal = a.getBoolean(R.styleable.BubbleTextView_layoutHorizontal, false);
        mIsRtl = (getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL);
        mDeviceProfile = mActivity.getDeviceProfile();
        mCenterVertically = a.getBoolean(R.styleable.BubbleTextView_centerVertically, false);

        mDisplay = a.getInteger(R.styleable.BubbleTextView_iconDisplay, DISPLAY_WORKSPACE);
        final int defaultIconSize;
        if (mDisplay == DISPLAY_WORKSPACE) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    mDeviceProfile.getWorkspaceIconProfile().getIconTextSizePx());
            setCompoundDrawablePadding(
                    mDeviceProfile.getWorkspaceIconProfile().getIconDrawablePaddingPx());
            defaultIconSize = mDeviceProfile.getWorkspaceIconProfile().getIconSizePx();
            setCenterVertically(mDeviceProfile.getWorkspaceIconProfile().getIconCenterVertically());
            mShouldShowLabel = SHOW_DESKTOP_LABELS.get(context);
        } else if (mDisplay == DISPLAY_ALL_APPS || mDisplay == DISPLAY_PREDICTION_ROW
                || mDisplay == DISPLAY_SEARCH_RESULT_APP_ROW) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    mDeviceProfile.getAllAppsProfile().getIconTextSizePx());
            setCompoundDrawablePadding(
                    mDeviceProfile.getAllAppsProfile().getIconDrawablePaddingPx());
            defaultIconSize = mDeviceProfile.getAllAppsProfile().getIconSizePx();
            mShouldShowLabel = SHOW_DRAWER_LABELS.get(context);
            mThemeAllAppsIcons = ALLAPPS_THEMED_ICONS.get(context);
        } else if (mDisplay == DISPLAY_FOLDER) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    mDeviceProfile.getFolderProfile().getChildTextSizePx());
            setCompoundDrawablePadding(
                    mDeviceProfile.getFolderProfile().getChildDrawablePaddingPx());
            defaultIconSize = mDeviceProfile.getFolderProfile().getChildIconSizePx();
            mShouldShowLabel = SHOW_DESKTOP_LABELS.get(context);
            mThemeAllAppsIcons = ALLAPPS_THEMED_ICONS.get(context);
        } else if (mDisplay == DISPLAY_SEARCH_RESULT) {
            setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    mDeviceProfile.getAllAppsProfile().getIconTextSizePx());
            defaultIconSize = getResources().getDimensionPixelSize(R.dimen.search_row_icon_size);
            mShouldShowLabel = SHOW_DESKTOP_LABELS.get(context);
        } else if (mDisplay == DISPLAY_SEARCH_RESULT_SMALL) {
            defaultIconSize = getResources().getDimensionPixelSize(
                    R.dimen.search_row_small_icon_size);
            mShouldShowLabel = SHOW_DESKTOP_LABELS.get(context);
        } else if (mDisplay == DISPLAY_TASKBAR) {
            defaultIconSize = mDeviceProfile.getTaskbarProfile().getIconSize();
        } else {
            // widget_selection or shortcut_popup
            defaultIconSize = mDeviceProfile.getWorkspaceIconProfile().getIconSizePx();
            mShouldShowLabel = SHOW_DESKTOP_LABELS.get(context);
        }


        mIconSize = a.getDimensionPixelSize(R.styleable.BubbleTextView_iconSizeOverride,
                defaultIconSize);
        a.recycle();

        mRunningAppIndicatorHeight =
                getResources().getDimensionPixelSize(R.dimen.taskbar_running_app_indicator_height);
        mRunningAppIndicatorTopMargin =
                getResources().getDimensionPixelSize(
                        R.dimen.taskbar_running_app_indicator_top_margin);
        mRunningAppIndicatorPaint = new Paint();

        mLongPressHelper = new CheckLongPressHelper(this);

        mDotParams = new DotRenderer.DrawParams();
        mDotParams.setDotColor(Themes.getAttrColor(context, R.attr.notificationDotColor));

        if (mDisplay == DISPLAY_ALL_APPS) {
            mDotRenderer = mActivity.getDeviceProfile().mDotRendererAllApps;

            // Do not use normalized info, as we account for normalization in iconBounds
            mDotParams.shapeInfo = IconShapeInfo.DEFAULT;
        } else {
            mDotRenderer = mActivity.getDeviceProfile().mDotRendererWorkSpace;
            mDotParams.shapeInfo = ThemeManager.INSTANCE.get(context)
                    .getIconState().getIconShapeInfo();
        }

        setEllipsize(TruncateAt.END);
        setAccessibilityDelegate(mActivity.getAccessibilityDelegate());
        setTextAlpha(1f);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        // Disable marques when not focused to that, so that updating text does not cause relayout.
        setEllipsize(focused ? TruncateAt.MARQUEE : TruncateAt.END);
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    public void setHideBadge(boolean hideBadge) {
        mHideBadge = hideBadge;
    }

    public void setSkipUserBadge(boolean skipUserBadge) {
        mSkipUserBadge = skipUserBadge;
    }

    /**
     * Resets the view so it can be recycled.
     */
    public void reset() {
        mDotInfo = null;
        cancelDotScaleAnim();
        mDotParams.scale = 0f;
        mForceHideDot = false;
        setBackground(null);
        configureMinimalPopup(false);

        mLineIndicatorColor = Color.TRANSPARENT;
        mLineIndicatorWidth = 0;

        setTag(null);
        if (mIconLoadRequest != null) {
            mIconLoadRequest.cancel();
            mIconLoadRequest = null;
        }
        mSuperIconAdaptiveDrawable = null;
        mSuperIconBadge = null;
        mIsLoadingSuperIcon = false;
        mSuperIconShortcuts = null;
        mIsLoadingShortcuts = false;
        mActivePressedSlot = -1;
        mLastClickedSlot = -1;
        // Reset any shifty arrangements in case animation is disrupted.
        setPivotY(0);
        setAlpha(1);
        setScaleY(1);
        setTranslationY(0);
        setMaxLines(1);
        setVisibility(VISIBLE);
    }

    private void cancelDotScaleAnim() {
        if (mDotScaleAnim != null) {
            mDotScaleAnim.cancel();
        }
    }

    public void animateDotScale(float... dotScales) {
        cancelDotScaleAnim();
        mDotScaleAnim = ObjectAnimator.ofFloat(this, DOT_SCALE_PROPERTY, dotScales);
        mDotScaleAnim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mDotScaleAnim = null;
            }
        });
        mDotScaleAnim.start();
    }

    @Override
    public void setAccessibilityDelegate(AccessibilityDelegate delegate) {
        if (delegate instanceof BaseAccessibilityDelegate) {
            super.setAccessibilityDelegate(delegate);
        } else {
            // NO-OP
            // Workaround for b/129745295 where RecyclerView is setting our Accessibility
            // delegate incorrectly. There are no cases when we shouldn't be using the
            // LauncherAccessibilityDelegate for BubbleTextView.
        }
    }

    @UiThread
    public void applyFromWorkspaceItem(WorkspaceItemInfo info) {
        applyIconAndLabel(info);
        setItemInfo(info);

        applyDotState(info, false /* animate */);
        setDownloadStateContentDescription(info, info.getProgressLevel());
        if (isMultiSpan()) {
            loadSuperIconDrawableIfNecessary();
        }
    }

    @UiThread
    public void applyFromApplicationInfo(AppInfo info) {
        applyIconAndLabel(info);
        setItemInfo(info);

        // Verify high res immediately
        verifyHighRes();

        applyDotState(info, false /* animate */);
        setDownloadStateContentDescription(info, info.getProgressLevel());
    }

    /**
     * Apply label and tag using a generic {@link ItemInfoWithIcon}
     */
    @UiThread
    public void applyFromItemInfoWithIcon(ItemInfoWithIcon info) {
        applyIconAndLabel(info);
        // We don't need to check the info since it's not a WorkspaceItemInfo
        setItemInfo(info);

        // Verify high res immediately
        verifyHighRes();

        setDownloadStateContentDescription(info, info.getProgressLevel());
        if (isMultiSpan()) {
            loadSuperIconDrawableIfNecessary();
        }
    }

    /**
     * Directly set the icon and label.
     */
    @UiThread
    public void applyIconAndLabel(Drawable icon, CharSequence title, CharSequence description) {
        applyCompoundDrawables(icon);
        applyLabel(title, description, false, false);
    }

    public void setRunningAppState(RunningAppState runningAppState) {
        mRunningAppState = runningAppState;
    }

    public RunningAppState getRunningAppState() {
        return mRunningAppState;
    }

    public int getLineIndicatorColor() {
        return mLineIndicatorColor;
    }

    public void setLineIndicatorColor(int lineIndicatorColor) {
        mLineIndicatorColor = lineIndicatorColor;
        invalidate();
    }

    public float getLineIndicatorWidth() {
        return mLineIndicatorWidth;
    }

    public void setLineIndicatorWidth(float lineIndicatorWidth) {
        mLineIndicatorWidth = lineIndicatorWidth;
        invalidate();
    }

    /**
     * Returns state description of this icon.
     */
    public String getIconStateDescription() {
        if (mRunningAppState == MINIMIZED) {
            return mMinimizedStateDescription;
        } else if (mRunningAppState == RUNNING) {
            return mRunningStateDescription;
        } else {
            return "";
        }
    }

    protected void setItemInfo(ItemInfoWithIcon itemInfo) {
        setTag(itemInfo);
    }

    @VisibleForTesting
    @UiThread
    public void applyIconAndLabel(ItemInfoWithIcon info) {
        FastBitmapDrawable oldIcon = mIcon;
        // Check if we can reuse icon so that any animation is preserved
        if (hasPendingAnimationCompleted(mIcon) || !mIcon.isSameInfo(info.bitmap)) {
            setNonPendingIcon(info);
        }
        applyLabel(info);
        maybeApplyProgressLevel(info, oldIcon);
    }

    /**
     * Apply progress level to the icon if necessary
     */
    private void maybeApplyProgressLevel(ItemInfoWithIcon info, FastBitmapDrawable oldIcon) {
        if (!info.shouldShowPendingIcon() && hasPendingAnimationCompleted(oldIcon)) {
            return;
        }

        PreloadIconDelegate pendingIcon = applyProgressLevel(info);
        boolean isNoLongerPending = info instanceof WorkspaceItemInfo wii
                ? !wii.hasPromiseIconUi() : !info.isArchived();
        if (isNoLongerPending && info.getProgressLevel() == 100 && pendingIcon != null) {
            pendingIcon.maybePerformFinishedAnimation(oldIcon,
                    () -> setNonPendingIcon(
                            (getTag() instanceof ItemInfoWithIcon iiwi) ? iiwi : info));
        }
    }

    private void setNonPendingIcon(ItemInfoWithIcon info) {
        FastBitmapDrawable iconDrawable =
                info.newIcon(getContext(), getIconCreationFlagsForInfo(info));
        if (mIsShowingMinimalPopup) {
            iconDrawable.setAnimationEnabled(false);
        }
        mSuperIconAdaptiveDrawable = null;
        mSuperIconBadge = null;
        mIsLoadingSuperIcon = false;
        mSuperIconShortcuts = null;
        mIsLoadingShortcuts = false;
        mActivePressedSlot = -1;
        mLastClickedSlot = -1;
        setIcon(iconDrawable);
        if (isMultiSpan()) {
            loadSuperIconDrawableIfNecessary();
            loadSuperIconShortcutsIfNecessary();
        }
    }

    /**
     * Configures the BubbleTextView on long click disabling animations and hiding system shortcuts.
     *
     * @param shouldDisableAnimationAndShortcuts {@code true} to show the minimal popup and not show
     * long press animation and system shortcuts.
     * {@code false} to show long press animation and system shortcuts.
     */
    public void configureMinimalPopup(boolean shouldDisableAnimationAndShortcuts) {
        mIsShowingMinimalPopup = shouldDisableAnimationAndShortcuts;
    }

    public boolean getShowingMinimalPopup() {
        return mIsShowingMinimalPopup;
    }

    /**
     * Returns the creation flags to be used when generating icons for this view
     */
    @DrawableCreationFlags
    public int getIconCreationFlagsForInfo(ItemInfoWithIcon info) {
        // Set nonPendingIcon acts as a restart which should refresh the flag state when applicable.
        int flags = shouldUseTheme() ? FLAG_THEMED : 0;
        // Remove badge on icons smaller than 48dp.
        if (mHideBadge || mDisplay == DISPLAY_SEARCH_RESULT_SMALL) {
            flags |= FLAG_NO_BADGE;
        }
        if (mSkipUserBadge) {
            flags |= FLAG_SKIP_USER_BADGE;
        }
        return flags;
    }

    public void setIsInAllAppsFolder(boolean isInAllAppsFolder) {
        mIsInAllAppsFolder = isInAllAppsFolder;
    }

    protected boolean shouldUseTheme() {
        if (mDisplay == DISPLAY_FOLDER) {
            return mThemeAllAppsIcons || !mIsInAllAppsFolder;
        }
        boolean themeAllApps = mThemeAllAppsIcons
                && (mDisplay == DISPLAY_ALL_APPS || mDisplay == DISPLAY_PREDICTION_ROW);
        return mDisplay == DISPLAY_WORKSPACE || mDisplay == DISPLAY_TASKBAR || themeAllApps;
    }

    /**
     * Only if actual text can be displayed in two line, the {@code true} value will be effective.
     */
    protected boolean shouldUseTwoLine() {
        // For all apps and search UI, respect user selection in home settings. Note that all apps
        // cell spec can declare support for two line labels, but it's used primarily to decide
        // whether the BubbleTextView height needs to be increased to accommodate an extra line of
        // text.
        if (mDisplay == DISPLAY_ALL_APPS || mDisplay == DISPLAY_PREDICTION_ROW) {
            return mDeviceProfile.inv.enableTwoLinesInAllApps;
        }

        // Otherwise, show two lines if the cell declares it can fit two line label.
        return getCellSpecMaxTextLineCount() == 2;
    }

    /**
     * @return The number of lines the that the cell spec associated with the BubbleTextView
     * declared it can support.
     */
    private int getCellSpecMaxTextLineCount() {
        if (!enableScalabilityForDesktopExperience()) {
            return 1;
        }

        switch (mDisplay) {
            case DISPLAY_ALL_APPS, DISPLAY_PREDICTION_ROW -> {
                return mDeviceProfile.getAllAppsProfile().getMaxAllAppsTextLineCount();
            }
            case DISPLAY_WORKSPACE -> {
                return mDeviceProfile.getWorkspaceIconProfile().getMaxIconTextLineCount();
            }
            case DISPLAY_FOLDER -> {
                return mDeviceProfile.getFolderProfile().getMaxChildTextLineCount();
            }
        }
        return 1;
    }

    @UiThread
    public void applyLabel(ItemInfo info) {
        applyLabel(info.title, info.contentDescription, Flags.useNewIconForArchivedApps()
                && info instanceof ItemInfoWithIcon infoWithIcon
                && infoWithIcon.isInactiveArchive(), info.isDisabled());
    }

    /**
     * Directly sets the item label, without applying the icon.
     */
    @UiThread
    public void applyLabel(CharSequence label) {
        applyLabel(label, null, false, false);
    }

    private void applyLabel(@Nullable CharSequence label, @Nullable CharSequence contentDescription,
            boolean isTextWithArchivingIcon, boolean isItemDisabled) {
        if (mShouldShowLabel && label != null) {
            mLastOriginalText = label;
            mLastModifiedText = mLastOriginalText;
            mBreakPointsIntArray = StringMatcherUtility.getListOfBreakpoints(label, MATCHER);
            if (isTextWithArchivingIcon) {
                setTextWithArchivingIcon(label);
            } else {
                setText(label);
            }
        }
        if (contentDescription != null) {
            setContentDescription(isItemDisabled
                    ? getContext().getString(R.string.disabled_app_label, contentDescription)
                    : contentDescription);
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (getTag() instanceof ItemInfoWithIcon infoWithIcon && infoWithIcon.isInactiveArchive()) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                    AccessibilityNodeInfoCompat.ACTION_CLICK,
                    getContext().getString(R.string.app_unarchiving_action)));
        }
    }

    /** This is used for testing to forcefully set the display. */
    @VisibleForTesting
    public void setDisplay(int display) {
        mDisplay = display;
    }

    /**
     * Overrides the default long press timeout.
     */
    public void setLongPressTimeoutFactor(float longPressTimeoutFactor) {
        mLongPressHelper.setLongPressTimeoutFactor(longPressTimeoutFactor);
    }

    @Override
    public void refreshDrawableState() {
        if (!mIgnorePressedStateChange) {
            super.refreshDrawableState();
        }
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (mStayPressed) {
            mergeDrawableStates(drawableState, STATE_PRESSED);
        }
        return drawableState;
    }

    /** Returns the icon for this view. */
    public FastBitmapDrawable getIcon() {
        return mIcon;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // ignore events if they happen in padding area
        if (event.getAction() == MotionEvent.ACTION_DOWN
                && shouldIgnoreTouchDown(event.getX(), event.getY())) {
            return false;
        }

        if (hasQuickFunctions()) {
            int action = event.getActionMasked();
            float x = event.getX();
            float y = event.getY();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mActivePressedSlot = getSlotAt(x, y);
                    mLastClickedSlot = mActivePressedSlot;
                    if (mActivePressedSlot >= 0) {
                        animateSlotPress(mActivePressedSlot, true);
                    }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (mActivePressedSlot >= 0) {
                        int currentSlot = getSlotAt(x, y);
                        if (currentSlot != mActivePressedSlot) {
                            animateSlotPress(mActivePressedSlot, false);
                            mActivePressedSlot = -1;
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (mActivePressedSlot >= 0) {
                        mLastClickedSlot = mActivePressedSlot;
                        animateSlotPress(mActivePressedSlot, false);
                        mActivePressedSlot = -1;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                    if (mActivePressedSlot >= 0) {
                        animateSlotPress(mActivePressedSlot, false);
                        mActivePressedSlot = -1;
                        mLastClickedSlot = -1;
                    }
                    break;
            }
        }

        if (isLongClickable()) {
            super.onTouchEvent(event);
            mLongPressHelper.onTouchEvent(event);
            // Keep receiving the rest of the events
            return true;
        } else {
            return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        if (hasQuickFunctions() && mLastClickedSlot > 0) {
            int slot = mLastClickedSlot;
            mLastClickedSlot = -1;
            int shortcutIndex = slot - 1;
            if (mSuperIconShortcuts != null && shortcutIndex < mSuperIconShortcuts.size()) {
                WorkspaceItemInfo shortcutItem = mSuperIconShortcuts.get(shortcutIndex);
                Context context = getContext();
                if (context != null) {
                    Launcher launcher = Launcher.getLauncher(context);
                    if (launcher != null) {
                        playSoundEffect(SoundEffectConstants.CLICK);
                        ItemClickHandler.onClickAppShortcut(this, shortcutItem, launcher);
                        return true;
                    }
                }
            }
        }
        mLastClickedSlot = -1;
        return super.performClick();
    }

    @Override
    public boolean performLongClick() {
        mLastClickedSlot = -1;
        if (mActivePressedSlot >= 0) {
            animateSlotPress(mActivePressedSlot, false);
            mActivePressedSlot = -1;
        }
        return super.performLongClick();
    }

    public boolean hasQuickFunctions() {
        if (!isMultiSpan() || mSuperIconShortcuts == null || mSuperIconShortcuts.isEmpty()) {
            return false;
        }
        int sx = getSpanX();
        int sy = getSpanY();
        return (sx == 2 && sy == 1) || (sx == 1 && sy == 2) || (sx == 2 && sy == 2);
    }

    private int getSlotAt(float x, float y) {
        if (!hasQuickFunctions()) {
            return -1;
        }
        Rect bgBounds = getMultiSpanBackgroundBounds();
        if (!bgBounds.contains((int) x, (int) y)) {
            return -1;
        }
        int spanX = getSpanX();
        int spanY = getSpanY();

        if (spanX == 2 && spanY == 1) {
            int count = Math.min(3, 1 + (mSuperIconShortcuts != null ? mSuperIconShortcuts.size() : 0));
            if (count <= 1) return 0;
            float slotWidth = bgBounds.width() / (float) count;
            int slotIndex = (int) ((x - bgBounds.left) / slotWidth);
            slotIndex = Math.max(0, Math.min(count - 1, slotIndex));
            if (mIsRtl) slotIndex = count - 1 - slotIndex;
            return slotIndex;
        } else if (spanX == 1 && spanY == 2) {
            int count = Math.min(3, 1 + (mSuperIconShortcuts != null ? mSuperIconShortcuts.size() : 0));
            if (count <= 1) return 0;
            float slotHeight = bgBounds.height() / (float) count;
            int slotIndex = (int) ((y - bgBounds.top) / slotHeight);
            return Math.max(0, Math.min(count - 1, slotIndex));
        } else if (spanX == 2 && spanY == 2) {
            float midY = bgBounds.top + bgBounds.height() * 0.52f;
            if (y < midY) {
                return 0; // Top hero area -> Main app
            }
            int shortcutCount = mSuperIconShortcuts != null ? Math.min(3, mSuperIconShortcuts.size()) : 0;
            if (shortcutCount <= 0) return 0;
            float btnWidth = bgBounds.width() / (float) shortcutCount;
            int shortcutIdx = (int) ((x - bgBounds.left) / btnWidth);
            shortcutIdx = Math.max(0, Math.min(shortcutCount - 1, shortcutIdx));
            if (mIsRtl) shortcutIdx = shortcutCount - 1 - shortcutIdx;
            return shortcutIdx + 1; // Slot 1, 2, or 3
        }
        return 0;
    }

    private void animateSlotPress(int slotIndex, boolean pressed) {
        if (slotIndex < 0 || slotIndex >= mSlotPressScales.length) {
            return;
        }
        float targetScale = pressed ? 0.92f : 1.0f;
        if (mSlotPressAnimators[slotIndex] != null) {
            mSlotPressAnimators[slotIndex].cancel();
        }
        mSlotPressAnimators[slotIndex] = ValueAnimator.ofFloat(mSlotPressScales[slotIndex], targetScale);
        mSlotPressAnimators[slotIndex].setDuration(pressed ? 120 : 180);
        mSlotPressAnimators[slotIndex].setInterpolator(OOS_PRESS_INTERPOLATOR);
        final int index = slotIndex;
        mSlotPressAnimators[slotIndex].addUpdateListener(anim -> {
            mSlotPressScales[index] = (float) anim.getAnimatedValue();
            invalidate();
        });
        mSlotPressAnimators[slotIndex].start();
    }

    /**
     * Returns true if the touch down at the provided position be ignored
     */
    protected boolean shouldIgnoreTouchDown(float x, float y) {
        if (isMultiSpan()) {
            return !getMultiSpanBackgroundBounds().contains((int) x, (int) y);
        }
        if (mDisplay == DISPLAY_TASKBAR) {
            // Allow touching within padding on taskbar, given icon sizes are smaller.
            return false;
        }
        return y < getPaddingTop()
                || x < getPaddingLeft()
                || y > getHeight() - getPaddingBottom()
                || x > getWidth() - getPaddingRight();
    }

    void setStayPressed(boolean stayPressed) {
        mStayPressed = stayPressed;
        refreshDrawableState();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (mIcon != null) {
            mIcon.setVisible(isVisible, false);
        }
    }

    public void clearPressedBackground() {
        setPressed(false);
        setStayPressed(false);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Unlike touch events, keypress event propagate pressed state change immediately,
        // without waiting for onClickHandler to execute. Disable pressed state changes here
        // to avoid flickering.
        mIgnorePressedStateChange = true;
        boolean result = super.onKeyUp(keyCode, event);
        mIgnorePressedStateChange = false;
        refreshDrawableState();
        return result;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isMultiSpan() && mSuperIconAdaptiveDrawable == null && !mIsLoadingSuperIcon) {
            loadSuperIconDrawableIfNecessary();
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        checkForEllipsis();
        if (isMultiSpan() && mSuperIconAdaptiveDrawable == null && !mIsLoadingSuperIcon) {
            loadSuperIconDrawableIfNecessary();
        }
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        checkForEllipsis();
    }

    private void checkForEllipsis() {
        float width = getWidth() - getCompoundPaddingLeft() - getCompoundPaddingRight();
        if (width <= 0) {
            return;
        }
        setLetterSpacing(0);

        String text = getText().toString();
        TextPaint paint = getPaint();
        if (paint.measureText(text) < width) {
            return;
        }

        float spacing = findBestSpacingValue(paint, text, width, MIN_LETTER_SPACING);
        // Reset the paint value so that the call to TextView does appropriate diff.
        paint.setLetterSpacing(0);
        setLetterSpacing(spacing);
    }

    /**
     * Find the appropriate text spacing to display the provided text
     *
     * @param paint          the paint used by the text view
     * @param text           the text to display
     * @param allowedWidthPx available space to render the text
     * @param minSpacingEm   minimum spacing allowed between characters
     * @return the final textSpacing value
     * @see #setLetterSpacing(float)
     */
    private float findBestSpacingValue(TextPaint paint, String text, float allowedWidthPx,
            float minSpacingEm) {
        paint.setLetterSpacing(minSpacingEm);
        if (paint.measureText(text) > allowedWidthPx) {
            // If there is no result at high limit, we can do anything more
            return minSpacingEm;
        }

        float lowLimit = 0;
        float highLimit = minSpacingEm;

        for (int i = 0; i < MAX_SEARCH_LOOP_COUNT; i++) {
            float value = (lowLimit + highLimit) / 2;
            paint.setLetterSpacing(value);
            if (paint.measureText(text) < allowedWidthPx) {
                highLimit = value;
            } else {
                lowLimit = value;
            }
        }

        // At the end error on the higher side
        return highLimit;
    }

    @SuppressWarnings("wrongcall")
    protected void drawWithoutDot(Canvas canvas) {
        if (isMultiSpan()) {
            drawMultiSpanSuperIcon(canvas);
            drawMultiSpanLabel(canvas);
            return;
        }
        if (mIcon != null && mIconSize > 0) {
            Rect b = mIcon.getBounds();
            if (b.left != 0 || b.top != 0 || b.width() != mIconSize || b.height() != mIconSize) {
                mIcon.setBounds(0, 0, mIconSize, mIconSize);
            }
        }
        super.onDraw(canvas);
    }

    public void onResizeChanged() {
        if (mSuperIconPressAnimator != null) {
            mSuperIconPressAnimator.cancel();
        }
        mSuperIconPressScale = 1.0f;
        for (int i = 0; i < mSlotPressScales.length; i++) {
            if (mSlotAnimators[i] != null) {
                mSlotAnimators[i].cancel();
            }
            mSlotPressScales[i] = 1.0f;
        }
        if (mIcon != null && mIconSize > 0) {
            mIcon.setBounds(0, 0, mIconSize, mIconSize);
        }
        applyCompoundDrawables(getIconOrTransparentColor());
        if (isMultiSpan()) {
            loadSuperIconDrawableIfNecessary();
            loadSuperIconShortcutsIfNecessary();
        }
        requestLayout();
        invalidate();
    }

    @Nullable
    public AdaptiveIconDrawable getSuperIconAdaptiveDrawable() {
        return mSuperIconAdaptiveDrawable;
    }

    @Nullable
    public Drawable getSuperIconBadge() {
        return mSuperIconBadge;
    }

    public boolean isMultiSpan() {
        if (mDisplay != DISPLAY_WORKSPACE) return false;
        return getSpanX() > 1 || getSpanY() > 1;
    }

    public int getSpanX() {
        if (getLayoutParams() instanceof CellLayoutLayoutParams lp) {
            return lp.cellHSpan;
        }
        if (getTag() instanceof ItemInfo itemInfo) {
            return itemInfo.spanX;
        }
        return 1;
    }

    public int getSpanY() {
        if (getLayoutParams() instanceof CellLayoutLayoutParams lp) {
            return lp.cellVSpan;
        }
        if (getTag() instanceof ItemInfo itemInfo) {
            return itemInfo.spanY;
        }
        return 1;
    }

    public Rect getMultiSpanBackgroundBounds() {
        Rect outBounds = new Rect();
        int spanX = getSpanX();
        int spanY = getSpanY();
        if (spanX <= 1 && spanY <= 1) {
            getIconBounds(outBounds);
            return outBounds;
        }

        int availableSpaceX = getWidth() > 0 ? getWidth()
                : (getMeasuredWidth() > 0 ? getMeasuredWidth() : 0);
        int availableSpaceY = getHeight() > 0 ? getHeight()
                : (getMeasuredHeight() > 0 ? getMeasuredHeight() : 0);

        com.android.launcher3.folder.PreviewBackground.calculateBackgroundBounds(
                mDeviceProfile,
                availableSpaceX,
                availableSpaceY,
                getPaddingTop(),
                spanX,
                spanY,
                outBounds);
        return outBounds;
    }

    public float getIconBackgroundCornerRadius() {
        int spanX = getSpanX();
        int spanY = getSpanY();
        Rect bgBounds = getMultiSpanBackgroundBounds();
        if (spanX == 2 && spanY == 1) {
            return bgBounds.height() / 2f;
        } else if (spanX == 1 && spanY == 2) {
            return bgBounds.width() / 2f;
        } else if (spanX == 2 && spanY == 2) {
            return mDeviceProfile.folderIconSizePx * 0.44f;
        }
        return mIconSize / 2f;
    }

    public void getIconBackgroundPath(Path outPath) {
        outPath.reset();
        Rect bgBounds = getMultiSpanBackgroundBounds();
        float radius = getIconBackgroundCornerRadius();
        outPath.addRoundRect(new RectF(bgBounds), radius, radius, Path.Direction.CW);
    }

    private float mSuperIconPressScale = 1.0f;
    private ValueAnimator mSuperIconPressAnimator;
    private static final Interpolator OOS_PRESS_INTERPOLATOR =
            new PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f);

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        if (isMultiSpan()) {
            if (hasQuickFunctions()) {
                return;
            }
            float targetScale = pressed ? 0.96f : 1.0f;
            if (mSuperIconPressAnimator != null) {
                mSuperIconPressAnimator.cancel();
            }
            mSuperIconPressAnimator = ValueAnimator.ofFloat(mSuperIconPressScale, targetScale);
            mSuperIconPressAnimator.setDuration(pressed ? 120 : 180);
            mSuperIconPressAnimator.setInterpolator(OOS_PRESS_INTERPOLATOR);
            mSuperIconPressAnimator.addUpdateListener(anim -> {
                mSuperIconPressScale = (float) anim.getAnimatedValue();
                invalidate();
            });
            mSuperIconPressAnimator.start();
        }
    }

    public void loadSuperIconDrawableIfNecessary() {
        if (!isMultiSpan()) {
            return;
        }
        if (mSuperIconAdaptiveDrawable != null || mIsLoadingSuperIcon) {
            return;
        }
        final ItemInfo itemInfo = (getTag() instanceof ItemInfo) ? (ItemInfo) getTag() : null;
        if (itemInfo == null) {
            return;
        }
        mIsLoadingSuperIcon = true;
        final int w = getWidth() > 0 ? getWidth() : mIconSize;
        final int h = getHeight() > 0 ? getHeight() : mIconSize;
        final boolean useTheme = ThemeManager.INSTANCE.get(getContext()).isIconThemeEnabled();
        final Context context = getContext();

        MODEL_EXECUTOR.getHandler().postAtFrontOfQueue(() -> {
            Pair<AdaptiveIconDrawable, Drawable> fullDrawable = null;
            try {
                fullDrawable = Utilities.getFullDrawable(
                        ActivityContext.lookupContext(context),
                        itemInfo, w, h, useTheme);
            } catch (Exception e) {
                Log.e(TAG, "Error loading full drawable for super icon", e);
            }
            final Pair<AdaptiveIconDrawable, Drawable> result = fullDrawable;
            post(() -> {
                mIsLoadingSuperIcon = false;
                if (result != null && result.first != null) {
                    mSuperIconAdaptiveDrawable = result.first;
                    mSuperIconBadge = result.second;
                    invalidate();
                }
            });
        });
    }

    public void loadSuperIconShortcutsIfNecessary() {
        if (!isMultiSpan()) {
            return;
        }
        int sx = getSpanX();
        int sy = getSpanY();
        if (!((sx == 2 && sy == 1) || (sx == 1 && sy == 2) || (sx == 2 && sy == 2))) {
            return;
        }
        if (mSuperIconShortcuts != null || mIsLoadingShortcuts) {
            return;
        }
        final ItemInfo itemInfo = (getTag() instanceof ItemInfo) ? (ItemInfo) getTag() : null;
        if (itemInfo == null) {
            return;
        }
        mIsLoadingShortcuts = true;
        SuperIconShortcutHelper.loadShortcutsForApp(getContext(), itemInfo, shortcuts -> {
            mIsLoadingShortcuts = false;
            if (shortcuts != null && !shortcuts.isEmpty()) {
                mSuperIconShortcuts = shortcuts;
                invalidate();
            }
        });
    }

    protected void drawMultiSpanSuperIcon(Canvas canvas) {
        if (!mIsIconVisible) {
            return;
        }
        Rect bgBounds = getMultiSpanBackgroundBounds();
        RectF bgRectF = new RectF(bgBounds);
        float radius = getIconBackgroundCornerRadius();

        canvas.save();
        if (!hasQuickFunctions() && mSuperIconPressScale != 1.0f) {
            canvas.scale(mSuperIconPressScale, mSuperIconPressScale, bgRectF.centerX(), bgRectF.centerY());
        }

        // Clip to the capsule / card shape
        Path clipPath = new Path();
        clipPath.addRoundRect(bgRectF, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        if (mSuperIconAdaptiveDrawable == null && !mIsLoadingSuperIcon) {
            loadSuperIconDrawableIfNecessary();
        }
        if (mSuperIconShortcuts == null && !mIsLoadingShortcuts) {
            loadSuperIconShortcutsIfNecessary();
        }

        // 1. Draw Background: stretch the app's icon background across the entire capsule/card
        boolean drewAdaptiveBg = false;
        if (mSuperIconAdaptiveDrawable != null) {
            Drawable bg = mSuperIconAdaptiveDrawable.getBackground();
            if (bg != null) {
                bg.setBounds(bgBounds.left, bgBounds.top, bgBounds.right, bgBounds.bottom);
                bg.draw(canvas);
                drewAdaptiveBg = true;
            }
        }

        if (!drewAdaptiveBg) {
            int fallbackBgColor = 0;
            if (getTag() instanceof ItemInfoWithIcon iiwi) {
                fallbackBgColor = iiwi.bitmap.color;
            }
            if (fallbackBgColor == 0 || fallbackBgColor == Color.TRANSPARENT) {
                fallbackBgColor = 0xFF2B2B2B;
            }
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(fallbackBgColor);
            bgPaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(bgRectF, bgPaint);
        }

        // 2. Draw Content: Quick Functions or Standard Foreground Glyph
        if (hasQuickFunctions()) {
            drawQuickFunctionsContent(canvas, bgBounds);
        } else {
            drawStandardSuperIconGlyph(canvas, bgBounds, bgRectF);
        }

        // 3. Draw Badge if present
        if (mSuperIconBadge != null) {
            int baseIconSize = mIconSize > 0 ? mIconSize
                    : mDeviceProfile.getWorkspaceIconProfile().getIconSizePx();
            int badgeSize = LauncherIcons.getBadgeSizeForIconSize(baseIconSize);
            int badgePadding = Math.round(8f * getResources().getDisplayMetrics().density);
            int badgeLeft = bgBounds.right - badgeSize - badgePadding;
            int badgeTop = bgBounds.bottom - badgeSize - badgePadding;
            mSuperIconBadge.setBounds(badgeLeft, badgeTop, badgeLeft + badgeSize, badgeTop + badgeSize);
            mSuperIconBadge.draw(canvas);
        }

        canvas.restore();
    }

    private void drawQuickFunctionsContent(Canvas canvas, Rect bgBounds) {
        int spanX = getSpanX();
        int spanY = getSpanY();

        int capsuleBgColor = 0;
        if (mSuperIconAdaptiveDrawable != null
                && mSuperIconAdaptiveDrawable.getBackground() instanceof ColorDrawable cd) {
            capsuleBgColor = cd.getColor();
        }
        if ((capsuleBgColor == 0 || capsuleBgColor == Color.TRANSPARENT)
                && getTag() instanceof ItemInfoWithIcon iiwi) {
            capsuleBgColor = iiwi.bitmap.color;
        }
        if (capsuleBgColor == 0 || capsuleBgColor == Color.TRANSPARENT) {
            capsuleBgColor = 0xFF2B2B2B;
        }
        double lum = ColorUtils.calculateLuminance(capsuleBgColor);
        boolean isDarkBg = lum < 0.5;
        boolean isLightBg = lum > 0.68;

        int buttonFillColor = isLightBg
                ? 0xFFE4E8F0
                : ColorUtils.blendARGB(Color.WHITE, capsuleBgColor, 0.16f);
        int strokeColor = isLightBg ? 0x1F000000 : 0x38FFFFFF;
        int appSlotFillColor = isLightBg
                ? 0xFFEDEFF5
                : ColorUtils.setAlphaComponent(Color.WHITE, 36);

        Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        if (spanX == 2 && spanY == 1) {
            draw2x1QuickFunctions(canvas, bgBounds, appSlotFillColor, buttonFillColor, strokeColor, bitmapPaint);
        } else if (spanX == 1 && spanY == 2) {
            draw1x2QuickFunctions(canvas, bgBounds, appSlotFillColor, buttonFillColor, strokeColor, bitmapPaint);
        } else if (spanX == 2 && spanY == 2) {
            draw2x2QuickFunctions(canvas, bgBounds, isDarkBg, appSlotFillColor, buttonFillColor, strokeColor, bitmapPaint);
        }
    }

    private void draw2x1QuickFunctions(Canvas canvas, Rect bgBounds, int appSlotFillColor,
            int buttonFillColor, int strokeColor, Paint bitmapPaint) {
        int shortcutCount = mSuperIconShortcuts != null ? Math.min(2, mSuperIconShortcuts.size()) : 0;
        int totalSlots = Math.min(3, 1 + shortcutCount);
        float slotWidth = bgBounds.width() / (float) totalSlots;
        float slotDiameter = Math.min(bgBounds.height() * 0.72f, slotWidth * 0.85f);

        Paint appSlotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        appSlotPaint.setStyle(Paint.Style.FILL);
        appSlotPaint.setColor(appSlotFillColor);

        for (int i = 0; i < totalSlots; i++) {
            float cx = mIsRtl
                    ? (bgBounds.right - slotWidth * (i + 0.5f))
                    : (bgBounds.left + slotWidth * (i + 0.5f));
            float cy = bgBounds.centerY();
            mSlotBounds[i].set(cx - slotDiameter / 2f, cy - slotDiameter / 2f,
                    cx + slotDiameter / 2f, cy + slotDiameter / 2f);

            canvas.save();
            if (mSlotPressScales[i] != 1.0f) {
                canvas.scale(mSlotPressScales[i], mSlotPressScales[i], cx, cy);
            }

            if (i == 0) {
                canvas.drawCircle(cx, cy, slotDiameter / 2f, appSlotPaint);
                drawAppIconGlyph(canvas, cx, cy, Math.round(slotDiameter * 0.82f));
            } else {
                int shortcutIndex = i - 1;
                if (shortcutIndex < mSuperIconShortcuts.size()) {
                    WorkspaceItemInfo shortcutItem = mSuperIconShortcuts.get(shortcutIndex);
                    drawShortcutCircleButton(canvas, cx, cy, slotDiameter, shortcutItem,
                            buttonFillColor, strokeColor, bitmapPaint);
                }
            }
            canvas.restore();
        }
    }

    private void draw1x2QuickFunctions(Canvas canvas, Rect bgBounds, int appSlotFillColor,
            int buttonFillColor, int strokeColor, Paint bitmapPaint) {
        int shortcutCount = mSuperIconShortcuts != null ? Math.min(2, mSuperIconShortcuts.size()) : 0;
        int totalSlots = Math.min(3, 1 + shortcutCount);
        float slotHeight = bgBounds.height() / (float) totalSlots;
        float slotDiameter = Math.min(bgBounds.width() * 0.72f, slotHeight * 0.85f);

        Paint appSlotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        appSlotPaint.setStyle(Paint.Style.FILL);
        appSlotPaint.setColor(appSlotFillColor);

        for (int i = 0; i < totalSlots; i++) {
            float cx = bgBounds.centerX();
            float cy = bgBounds.top + slotHeight * (i + 0.5f);
            mSlotBounds[i].set(cx - slotDiameter / 2f, cy - slotDiameter / 2f,
                    cx + slotDiameter / 2f, cy + slotDiameter / 2f);

            canvas.save();
            if (mSlotPressScales[i] != 1.0f) {
                canvas.scale(mSlotPressScales[i], mSlotPressScales[i], cx, cy);
            }

            if (i == 0) {
                canvas.drawCircle(cx, cy, slotDiameter / 2f, appSlotPaint);
                drawAppIconGlyph(canvas, cx, cy, Math.round(slotDiameter * 0.82f));
            } else {
                int shortcutIndex = i - 1;
                if (shortcutIndex < mSuperIconShortcuts.size()) {
                    WorkspaceItemInfo shortcutItem = mSuperIconShortcuts.get(shortcutIndex);
                    drawShortcutCircleButton(canvas, cx, cy, slotDiameter, shortcutItem,
                            buttonFillColor, strokeColor, bitmapPaint);
                }
            }
            canvas.restore();
        }
    }

    private void draw2x2QuickFunctions(Canvas canvas, Rect bgBounds, boolean isDarkBg,
            int appSlotFillColor, int buttonFillColor, int strokeColor, Paint bitmapPaint) {
        float cardW = bgBounds.width();
        float cardH = bgBounds.height();

        // 1. Top Hero Area (Slot 0: Main App)
        mSlotBounds[0].set(bgBounds.left, bgBounds.top, bgBounds.right, bgBounds.top + cardH * 0.52f);
        canvas.save();
        if (mSlotPressScales[0] != 1.0f) {
            canvas.scale(mSlotPressScales[0], mSlotPressScales[0], mSlotBounds[0].centerX(), mSlotBounds[0].centerY());
        }

        float iconDiameter = cardH * 0.32f;
        float iconCx = mIsRtl ? (bgBounds.right - cardW * 0.20f) : (bgBounds.left + cardW * 0.20f);
        float iconCy = bgBounds.top + cardH * 0.26f;
        Paint appSlotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        appSlotPaint.setStyle(Paint.Style.FILL);
        appSlotPaint.setColor(appSlotFillColor);
        canvas.drawCircle(iconCx, iconCy, iconDiameter / 2f, appSlotPaint);
        drawAppIconGlyph(canvas, iconCx, iconCy, Math.round(iconDiameter * 0.88f));

        // Draw App Label in Hero Area
        CharSequence title = getText();
        if (TextUtils.isEmpty(title) && getTag() instanceof ItemInfo ii) {
            title = ii.title;
        }
        if (!TextUtils.isEmpty(title)) {
            TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(isDarkBg ? Color.WHITE : 0xFF1F1F1F);
            textPaint.setTextSize(getResources().getDisplayMetrics().density * 15f);
            textPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));

            float textStartX = mIsRtl ? (bgBounds.left + cardW * 0.08f) : (bgBounds.left + cardW * 0.38f);
            float maxTextW = cardW * 0.54f;
            CharSequence ellipTitle = TextUtils.ellipsize(title, textPaint, maxTextW, TextUtils.TruncateAt.END);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textBaseline = iconCy - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(ellipTitle, 0, ellipTitle.length(), textStartX, textBaseline, textPaint);
        }
        canvas.restore();

        // 2. Bottom Row: Quick Functions Circular Buttons
        int shortcutCount = mSuperIconShortcuts != null ? Math.min(3, mSuperIconShortcuts.size()) : 0;
        if (shortcutCount > 0) {
            float btnWidth = cardW / (float) shortcutCount;
            float btnDiameter = Math.min(cardH * 0.34f, btnWidth * 0.72f);
            float btnCy = bgBounds.top + cardH * 0.74f;

            for (int j = 0; j < shortcutCount; j++) {
                int slotIdx = j + 1;
                float btnCx = mIsRtl
                        ? (bgBounds.right - btnWidth * (j + 0.5f))
                        : (bgBounds.left + btnWidth * (j + 0.5f));
                mSlotBounds[slotIdx].set(btnCx - btnDiameter / 2f, btnCy - btnDiameter / 2f,
                        btnCx + btnDiameter / 2f, btnCy + btnDiameter / 2f);

                canvas.save();
                if (mSlotPressScales[slotIdx] != 1.0f) {
                    canvas.scale(mSlotPressScales[slotIdx], mSlotPressScales[slotIdx], btnCx, btnCy);
                }

                WorkspaceItemInfo shortcutItem = mSuperIconShortcuts.get(j);
                drawShortcutCircleButton(canvas, btnCx, btnCy, btnDiameter, shortcutItem,
                        buttonFillColor, strokeColor, bitmapPaint);
                canvas.restore();
            }
        }
    }

    private void drawShortcutCircleButton(Canvas canvas, float cx, float cy, float diameter,
            WorkspaceItemInfo shortcutItem, int buttonFillColor, int strokeColor, Paint bitmapPaint) {
        float radius = diameter / 2f;
        Path circlePath = new Path();
        circlePath.addCircle(cx, cy, radius, Path.Direction.CW);

        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(buttonFillColor);
        canvas.drawPath(circlePath, fillPaint);

        if (shortcutItem != null && shortcutItem.bitmap != null && shortcutItem.bitmap.icon != null) {
            Bitmap shortcutIcon = shortcutItem.bitmap.icon;
            canvas.save();
            canvas.clipPath(circlePath);

            boolean whitePlate = hasWhiteBackgroundPlate(shortcutIcon);
            float drawRadius = whitePlate ? (radius * 1.08f) : (radius * 1.04f);
            RectF dest = new RectF(cx - drawRadius, cy - drawRadius, cx + drawRadius, cy + drawRadius);

            if (whitePlate) {
                bitmapPaint.setColorFilter(
                        new PorterDuffColorFilter(buttonFillColor, PorterDuff.Mode.MULTIPLY));
                canvas.drawBitmap(shortcutIcon, null, dest, bitmapPaint);
                bitmapPaint.setColorFilter(null);
            } else {
                canvas.drawBitmap(shortcutIcon, null, dest, bitmapPaint);
            }
            canvas.restore();
        }

        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density * 0.9f));
        strokePaint.setColor(strokeColor);
        canvas.drawCircle(cx, cy, radius, strokePaint);
    }

    private static boolean hasWhiteBackgroundPlate(Bitmap bmp) {
        if (bmp == null || bmp.getWidth() < 8 || bmp.getHeight() < 8) {
            return false;
        }
        try {
            int w = bmp.getWidth();
            int h = bmp.getHeight();
            int[][] pts = {
                { w / 2, Math.max(1, Math.round(h * 0.12f)) },
                { w / 2, Math.min(h - 2, Math.round(h * 0.88f)) },
                { Math.max(1, Math.round(w * 0.12f)), h / 2 },
                { Math.min(w - 2, Math.round(w * 0.88f)), h / 2 }
            };
            int whiteCount = 0;
            for (int[] pt : pts) {
                int c = bmp.getPixel(pt[0], pt[1]);
                int a = (c >>> 24) & 0xFF;
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                if (a > 200 && r > 235 && g > 235 && b > 235) {
                    whiteCount++;
                }
            }
            return whiteCount >= 3;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void drawAppIconGlyph(Canvas canvas, float cx, float cy, int glyphTargetSize) {
        boolean drewAdaptiveFg = false;
        if (mSuperIconAdaptiveDrawable != null) {
            Drawable fg = mSuperIconAdaptiveDrawable.getForeground();
            if (fg != null) {
                boolean isLegacyWrapped = fg.getClass().getName().contains("FixedScaleDrawable");
                int fullFgSize = Math.round(glyphTargetSize * (isLegacyWrapped ? 2.15f : 1.5f));
                int fgLeft = Math.round(cx - fullFgSize / 2f);
                int fgTop = Math.round(cy - fullFgSize / 2f);
                canvas.save();
                if (isLegacyWrapped) {
                    Path clipCircle = new Path();
                    clipCircle.addCircle(cx, cy, glyphTargetSize / 2f, Path.Direction.CW);
                    canvas.clipPath(clipCircle);
                }
                fg.setBounds(fgLeft, fgTop, fgLeft + fullFgSize, fgTop + fullFgSize);
                fg.draw(canvas);
                canvas.restore();
                drewAdaptiveFg = true;
            }
        }
        if (!drewAdaptiveFg && mIcon != null) {
            Rect prevBounds = new Rect(mIcon.getBounds());
            int iconLeft = Math.round(cx - glyphTargetSize / 2f);
            int iconTop = Math.round(cy - glyphTargetSize / 2f);
            mIcon.setBounds(iconLeft, iconTop, iconLeft + glyphTargetSize, iconTop + glyphTargetSize);
            mIcon.draw(canvas);
            if (mIconSize > 0) {
                mIcon.setBounds(0, 0, mIconSize, mIconSize);
            } else {
                mIcon.setBounds(prevBounds);
            }
        }
    }

    private void drawStandardSuperIconGlyph(Canvas canvas, Rect bgBounds, RectF bgRectF) {
        int spanX = getSpanX();
        int spanY = getSpanY();
        int glyphTargetSize;

        if (spanX == 2 && spanY == 1) {
            glyphTargetSize = Math.round(bgRectF.height() * 0.62f);
        } else if (spanX == 1 && spanY == 2) {
            glyphTargetSize = Math.round(bgRectF.width() * 0.62f);
        } else if (spanX == 2 && spanY == 2) {
            glyphTargetSize = Math.round(Math.min(bgRectF.width(), bgRectF.height()) * 0.52f);
        } else {
            glyphTargetSize = Math.round(Math.min(bgRectF.width(), bgRectF.height()) * 0.62f);
        }

        boolean drewAdaptiveFg = false;
        if (mSuperIconAdaptiveDrawable != null) {
            Drawable fg = mSuperIconAdaptiveDrawable.getForeground();
            if (fg != null) {
                boolean isLegacyWrapped = fg.getClass().getName().contains("FixedScaleDrawable");
                int fullFgSize = Math.round(glyphTargetSize * (isLegacyWrapped ? 2.15f : 1.5f));
                int fgLeft = Math.round(bgRectF.centerX() - fullFgSize / 2f);
                int fgTop = Math.round(bgRectF.centerY() - fullFgSize / 2f);
                canvas.save();
                if (isLegacyWrapped) {
                    Path clipCircle = new Path();
                    clipCircle.addCircle(bgRectF.centerX(), bgRectF.centerY(),
                            glyphTargetSize / 2f, Path.Direction.CW);
                    canvas.clipPath(clipCircle);
                }
                fg.setBounds(fgLeft, fgTop, fgLeft + fullFgSize, fgTop + fullFgSize);
                fg.draw(canvas);
                canvas.restore();
                drewAdaptiveFg = true;
            }
        }

        if (!drewAdaptiveFg) {
            if (mIcon != null) {
                Rect prevBounds = new Rect(mIcon.getBounds());
                int iconLeft = Math.round(bgRectF.centerX() - glyphTargetSize / 2f);
                int iconTop = Math.round(bgRectF.centerY() - glyphTargetSize / 2f);
                mIcon.setBounds(iconLeft, iconTop, iconLeft + glyphTargetSize, iconTop + glyphTargetSize);
                mIcon.draw(canvas);
                if (mIconSize > 0) {
                    mIcon.setBounds(0, 0, mIconSize, mIconSize);
                } else {
                    mIcon.setBounds(prevBounds);
                }
            }
        }
    }

    protected void drawMultiSpanLabel(Canvas canvas) {
        if (hasQuickFunctions() && getSpanX() == 2 && getSpanY() == 2) {
            return;
        }
        if (!shouldTextBeVisible() || TextUtils.isEmpty(getText()) || getLayout() == null) {
            return;
        }
        Rect bgBounds = getMultiSpanBackgroundBounds();
        int spanY = getSpanY();
        int targetRow = spanY - 1;
        DeviceProfile dp = mDeviceProfile;
        Point borderSpace = dp.getWorkspaceIconProfile().getCellLayoutBorderSpacePx();
        Point cellSize = dp.getWorkspaceIconProfile().getCellSize();
        int availableHeight = getHeight() > 0 ? getHeight() : getMeasuredHeight();
        int cellHeight = availableHeight > 0
                ? (spanY > 1 ? (availableHeight - (spanY - 1) * borderSpace.y) / spanY
                        : availableHeight)
                : (cellSize.y > 0 ? cellSize.y
                        : dp.getWorkspaceIconProfile().getCellHeightPx());
        int iconSize = dp.getWorkspaceIconProfile().getIconSizePx();
        int iconPadding = getCompoundDrawablePadding();

        int cellPaddingY = getPaddingTop();
        if (cellPaddingY <= 0) {
            cellPaddingY = dp.getWorkspaceIconProfile().getCellYPaddingPx();
        }
        if (cellPaddingY <= 0) {
            int iconTextHeight = Utilities.calculateTextHeight(
                    dp.getWorkspaceIconProfile().getIconTextSizePx());
            int contentHeight = iconSize + iconPadding + iconTextHeight;
            cellPaddingY = Math.max(0, cellHeight - contentHeight) / 2;
        }

        int standardIconLabelTop = targetRow * (cellHeight + borderSpace.y)
                + cellPaddingY + iconSize + iconPadding;

        int textTop = Math.max(standardIconLabelTop, bgBounds.bottom + Math.max(1, iconPadding / 2));
        int textWidth = getLayout().getWidth();
        int textLeft = bgBounds.left + (bgBounds.width() - textWidth) / 2;

        canvas.save();
        canvas.translate(getScrollX() + textLeft, getScrollY() + textTop);
        getPaint().setColor(getCurrentTextColor());
        getPaint().drawableState = getDrawableState();
        getLayout().draw(canvas);
        canvas.restore();
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (isMultiSpan()) {
            drawMultiSpanSuperIcon(canvas);
            drawMultiSpanLabel(canvas);
            drawDotIfNecessary(canvas);
            drawSelectionIndicatorIfNecessary(canvas);
            return;
        }
        if (mIcon != null && mIconSize > 0) {
            Rect b = mIcon.getBounds();
            if (b.left != 0 || b.top != 0 || b.width() != mIconSize || b.height() != mIconSize) {
                mIcon.setBounds(0, 0, mIconSize, mIconSize);
            }
        }
        super.onDraw(canvas);
        drawDotIfNecessary(canvas);
        drawRunningAppIndicatorIfNecessary(canvas);
        drawSelectionIndicatorIfNecessary(canvas);
    }

    /**
     * Helper method to render selection indicator.
     */
    public void drawSelectionIndicatorIfNecessary(Canvas canvas) {
        if (mActivity instanceof Launcher launcher && launcher.isInState(LauncherState.EDIT_MODE)) {
            Object tag = getTag();
            if (tag instanceof ItemInfo info) {
                if (info.container != com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP &&
                    info.container != com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
                    return;
                }
                if (mSelectionRenderer == null) {
                    mSelectionRenderer = new com.android.launcher3.graphics.SelectionIndicatorRenderer(getContext());
                }
                boolean isSelected = launcher.getMultiSelectController().isSelected(info);
                getIconBounds(mTempIconBounds);

                final int scrollX = getScrollX();
                final int scrollY = getScrollY();
                canvas.translate(scrollX, scrollY);
                mSelectionRenderer.draw(canvas, mTempIconBounds, isSelected, mDeviceProfile.getWorkspaceIconProfile().getIconSizePx());
                canvas.translate(-scrollX, -scrollY);
            }
        }
    }

    /**
     * Draws the notification dot in the top right corner of the icon bounds.
     *
     * @param canvas The canvas to draw to.
     */
    protected void drawDotIfNecessary(Canvas canvas) {
        if (!mForceHideDot && (hasDot() || mDotParams.scale > 0)) {
            if (isMultiSpan()) {
                Rect bgBounds = getMultiSpanBackgroundBounds();
                mDotParams.iconBounds.set(
                        bgBounds.right - mIconSize,
                        bgBounds.top,
                        bgBounds.right,
                        bgBounds.top + mIconSize);
            } else {
                getIconBounds(mDotParams.iconBounds);
            }
            Utilities.scaleRectAboutCenter(mDotParams.iconBounds, ICON_VISIBLE_AREA_FACTOR);
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            canvas.translate(scrollX, scrollY);
            mDotRenderer.draw(canvas, mDotParams);
            canvas.translate(-scrollX, -scrollY);
        }
    }

    /** Draws a background behind the App Title label when required. **/
    public void drawAppContrastTile(Canvas canvas) {
        RectF appTitleBounds;
        Paint.FontMetrics fm = getPaint().getFontMetrics();
        Rect tmpRect = new Rect();
        getDrawingRect(tmpRect);
        CharSequence text = getText();

        int mAppTitleHorizontalPadding = getResources().getDimensionPixelSize(
                R.dimen.app_title_pill_horizontal_padding);
        int mRoundRectPadding = getResources().getDimensionPixelSize(
                R.dimen.app_title_pill_round_rect_padding);

        float titleLength = (getPaint().measureText(text, 0, text.length())
                + (mAppTitleHorizontalPadding + mRoundRectPadding) * 2);
        titleLength = Math.min(titleLength, tmpRect.width());
        appTitleBounds = new RectF((tmpRect.width() - titleLength) / 2.f - getCompoundPaddingLeft(),
                0, (tmpRect.width() + titleLength) / 2.f + getCompoundPaddingRight(),
                (int) Math.ceil(fm.bottom - fm.top));
        appTitleBounds.inset((mAppTitleHorizontalPadding) * 2, 0);


        if (mIcon != null) {
            Rect iconBounds = new Rect();
            getIconBounds(iconBounds);
            int textStart = iconBounds.bottom + getCompoundDrawablePadding();
            appTitleBounds.offset(0, textStart);
        }

        canvas.drawRoundRect(appTitleBounds, appTitleBounds.height() / 2,
                appTitleBounds.height() / 2,
                PillColorProvider.getInstance(getContext()).getAppTitlePillPaint());
    }

    /** Draws a line under the app icon if this is representing a running app in Desktop Mode. */
    protected void drawRunningAppIndicatorIfNecessary(Canvas canvas) {
        if (mDisplay != DISPLAY_TASKBAR
                || Float.compare(mLineIndicatorWidth, 0) == 0
                || mLineIndicatorColor == Color.TRANSPARENT) {
            return;
        }
        getIconBounds(mRunningAppIconBounds);
        Utilities.scaleRectAboutCenter(mRunningAppIconBounds, ICON_VISIBLE_AREA_FACTOR);

        float taskbarAppRunningStateAnimOffset =
                mTranslateDelegate.getTranslationY(INDEX_TASKBAR_APP_RUNNING_STATE_ANIM).getValue();
        final float indicatorTop = mRunningAppIconBounds.bottom
                + mRunningAppIndicatorTopMargin
                - taskbarAppRunningStateAnimOffset;
        final float cornerRadius = mRunningAppIndicatorHeight / 2f;
        mRunningAppIndicatorPaint.setColor(mLineIndicatorColor);

        canvas.drawRoundRect(
                mRunningAppIconBounds.centerX() - mLineIndicatorWidth / 2f,
                indicatorTop,
                mRunningAppIconBounds.centerX() + mLineIndicatorWidth / 2f,
                indicatorTop + mRunningAppIndicatorHeight,
                cornerRadius,
                cornerRadius,
                mRunningAppIndicatorPaint);
    }

    @Override
    public void setForceHideDot(boolean forceHideDot) {
        if (mForceHideDot == forceHideDot) {
            return;
        }
        mForceHideDot = forceHideDot;

        if (forceHideDot) {
            invalidate();
        } else if (hasDot()) {
            animateDotScale(0, 1);
        }
    }

    @VisibleForTesting
    public boolean getForceHideDot() {
        return mForceHideDot;
    }

    public boolean hasDot() {
        return mDotInfo != null;
    }

    /**
     * Get the icon bounds on the view depending on the layout type.
     */
    public void getIconBounds(Rect outBounds) {
        if (isMultiSpan()) {
            outBounds.set(getMultiSpanBackgroundBounds());
            return;
        }
        getIconBounds(mIconSize, outBounds);
    }

    /**
     * Get the icon bounds on the view depending on the layout type.
     */
    public void getIconBounds(int iconSize, Rect outBounds) {
        outBounds.set(0, 0, iconSize, iconSize);
        if (mLayoutHorizontal) {
            int top = (getHeight() - iconSize) / 2;
            if (mIsRtl) {
                outBounds.offsetTo(getWidth() - iconSize - getPaddingRight(), top);
            } else {
                outBounds.offsetTo(getPaddingLeft(), top);
            }
        } else {
            outBounds.offset((getWidth() - iconSize) / 2, getPaddingTop());
        }
    }

    /**
     * Sets whether the layout is horizontal.
     */
    public void setLayoutHorizontal(boolean layoutHorizontal) {
        if (mLayoutHorizontal == layoutHorizontal) {
            return;
        }

        mLayoutHorizontal = layoutHorizontal;
        applyCompoundDrawables(getIconOrTransparentColor());
    }

    /**
     * Sets whether to vertically center the content.
     */
    public void setCenterVertically(boolean centerVertically) {
        mCenterVertically = centerVertically;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int availableHeight = height;
        if (isMultiSpan() && getSpanY() > 1) {
            int rowGap = mDeviceProfile.getWorkspaceIconProfile().getCellLayoutBorderSpacePx().y;
            availableHeight = (height - (getSpanY() - 1) * rowGap) / getSpanY();
        }
        if ((mCenterVertically || !shouldShowLabel()) && !mLayoutHorizontal) {
            Paint.FontMetrics fm = getPaint().getFontMetrics();
            int textHeight = shouldShowLabel()
                    ? (int) Math.ceil(fm.bottom - fm.top) * getCellSpecMaxTextLineCount() : 0;
            int cellHeightPx = mIconSize + getCompoundDrawablePadding() + textHeight;
            int cellYPadding;
            if (mDisplay == DISPLAY_WORKSPACE) {
                cellYPadding = mDeviceProfile.getWorkspaceIconProfile().getCellYPaddingPx();
                if (cellYPadding <= 0) {
                    cellYPadding = Math.max(0, (availableHeight - cellHeightPx) / 2);
                }
            } else {
                cellYPadding = Math.max(0, (availableHeight - cellHeightPx) / 2);
            }
            setPadding(getPaddingLeft(), cellYPadding,
                    getPaddingRight(), getPaddingBottom());
        }
        if (shouldDrawAppContrastTile()) {
            int mAppTitleHorizontalPadding = getResources().getDimensionPixelSize(
                    R.dimen.app_title_pill_horizontal_padding);
            int mRoundRectPadding = getResources().getDimensionPixelSize(
                    R.dimen.app_title_pill_round_rect_padding);

            setPadding(mAppTitleHorizontalPadding + mRoundRectPadding, getPaddingTop(),
                    mAppTitleHorizontalPadding + mRoundRectPadding,
                    getPaddingBottom());
        }

        if (shouldUseTwoLine() && (mLastOriginalText != null)) {
            int allowedVerticalSpace = availableHeight - getPaddingTop() - getPaddingBottom()
                    - (mIcon != null ? mIconSize + getCompoundDrawablePadding() : 0);
            CharSequence modifiedString = modifyTitleToSupportMultiLine(
                    MeasureSpec.getSize(widthMeasureSpec) - getCompoundPaddingLeft()
                            - getCompoundPaddingRight(),
                    allowedVerticalSpace,
                    mLastOriginalText,
                    getPaint(),
                    mBreakPointsIntArray,
                    getLineSpacingMultiplier(),
                    getLineSpacingExtra());
            if (!TextUtils.equals(modifiedString, mLastModifiedText)) {
                mLastModifiedText = modifiedString;
                if (Flags.useNewIconForArchivedApps()
                        && getTag() instanceof ItemInfoWithIcon infoWithIcon
                        && infoWithIcon.isInactiveArchive()) {
                    setTextWithArchivingIcon(modifiedString);
                } else {
                    setText(modifiedString);
                }
                // if text contains NEW_LINE, set max lines to 2
                if (TextUtils.indexOf(modifiedString, NEW_LINE) != -1) {
                    setSingleLine(false);
                    setMaxLines(2);
                } else {
                    setSingleLine(true);
                    setMaxLines(1);
                }
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setTextColor(int color) {
        mTextColor = color;
        mTextColorStateList = null;
        super.setTextColor(getModifiedColor());
    }

    /**
     * Sets text with a start icon for App Archiving.
     * Uses a bolded drawable if text is bolded.
     * @param text
     */
    private void setTextWithArchivingIcon(CharSequence text) {
        var drawableId = R.drawable.cloud_download_24px;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                && getResources().getConfiguration().fontWeightAdjustment >= BOLD_TEXT_ADJUSTMENT) {
            // If System bold text setting is on, then use a bolded icon
            drawableId = R.drawable.cloud_download_semibold_24px;
        }
        setTextWithStartIcon(text, drawableId);
    }

    /**
     * Uses a SpannableString to set text with a Drawable at the start of the TextView
     * @param text text to use for TextView
     * @param drawableId Drawable Resource to use for drawing image at start of text
     */
    @VisibleForTesting
    public void setTextWithStartIcon(CharSequence text, @DrawableRes int drawableId) {
        Drawable drawable = getContext().getDrawable(drawableId);
        if (drawable == null) {
            setText(text);
            Log.w(TAG, "setTextWithStartIcon: start icon Drawable not found from resources"
                    + ", will just set text instead.");
            return;
        }
        drawable.setTint(getCurrentTextColor());
        drawable.setBounds(0, 0, Math.round(getTextSize()), Math.round(getTextSize()));
        ImageSpan imageSpan = new ImageSpan(drawable, ImageSpan.ALIGN_CENTER);
        // First space will be replaced with Drawable, second space is for space before text.
        SpannableString spannable = new SpannableString("  " + text);
        spannable.setSpan(imageSpan, 0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        setText(spannable);
    }

    @Override
    public void setTextColor(ColorStateList colors) {
        if (shouldDrawAppContrastTile()) {
            mTextColor = PillColorProvider.getInstance(
                    getContext()).getAppTitleTextPaint().getColor();
        } else {
            mTextColor = colors.getDefaultColor();
            mTextColorStateList = colors;
        }

        if (Float.compare(mTextAlpha, 1) == 0) {
            super.setTextColor(colors);
        } else {
            super.setTextColor(getModifiedColor());
        }
    }

    public boolean shouldShowLabel() {
        return mShouldShowLabel;
    }

    public boolean shouldTextBeVisible() {
        // Text should be visible everywhere but the hotseat.
        Object tag = getParent() instanceof FolderIcon ? ((View) getParent()).getTag() : getTag();
        ItemInfo info = tag instanceof ItemInfo ? (ItemInfo) tag : null;
        return info == null || (info.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT
                && info.container != LauncherSettings.Favorites.CONTAINER_HOTSEAT_PREDICTION);
    }

    /**
     * Whether or not an App title contrast tile should be drawn for this element.
     **/
    public boolean shouldDrawAppContrastTile() {
        return mDisplay == DISPLAY_WORKSPACE && shouldTextBeVisible()
                && PillColorProvider.getInstance(getContext()).isMatchaEnabled()
                && enableContrastTiles();
    }

    public void setTextVisibility(boolean visible) {
        setTextAlpha(visible ? 1 : 0);
    }

    private void setTextAlpha(float alpha) {
        mTextAlpha = alpha;
        if (mTextColorStateList != null) {
            setTextColor(mTextColorStateList);
        } else {
            super.setTextColor(getModifiedColor());
        }
    }

    private int getModifiedColor() {
        if (mTextAlpha == 0) {
            // Special case to prevent text shadows in high contrast mode
            return Color.TRANSPARENT;
        }
        return setColorAlphaBound(mTextColor, Math.round(Color.alpha(mTextColor) * mTextAlpha));
    }

    /**
     * Creates an animator to fade the text in or out.
     *
     * @param fadeIn Whether the text should fade in or fade out.
     */
    public ObjectAnimator createTextAlphaAnimator(boolean fadeIn) {
        float toAlpha = shouldTextBeVisible() && fadeIn ? 1 : 0;
        return ObjectAnimator.ofFloat(this, TEXT_ALPHA_PROPERTY, toAlpha);
    }

    /**
     * Generate a new string that will support two line text depending on the current string.
     * This method calculates the limited width of a text view and creates a string to fit as
     * many words as it can until the limit is reached. Once the limit is reached, we decide to
     * either return the original title or continue on a new line. How to get the new string is by
     * iterating through the list of break points and determining if the strings between the break
     * points can fit within the line it is in. We will show the modified string if there is enough
     * horizontal and vertical space, otherwise this method will just return the original string.
     * Example assuming each character takes up one spot:
     * title = "Battery Stats", breakpoint = [6], stringPtr = 0, limitedWidth = 7
     * We get the current word -> from sublist(0, breakpoint[i]+1) so sublist (0,7) -> Battery,
     * now stringPtr = 7 then from sublist(7) the current string is " Stats" and the runningWidth
     * at this point exceeds limitedWidth and so we put " Stats" onto the next line (after checking
     * if the first char is a SPACE, we trim to append "Stats". So resulting string would be
     * "Battery\nStats"
     */
    public static CharSequence modifyTitleToSupportMultiLine(int limitedWidth, int limitedHeight,
            CharSequence title, TextPaint paint, IntArray breakPoints, float spacingMultiplier,
            float spacingExtra) {
        // current title is less than the width allowed so we can just skip
        if (title == null || paint.measureText(title, 0, title.length()) <= limitedWidth) {
            return title;
        }
        float currentWordWidth, runningWidth = 0;
        CharSequence currentWord;
        StringBuilder newString = new StringBuilder();
        paint.setLetterSpacing(MIN_LETTER_SPACING);
        int stringPtr = 0;
        for (int i = 0; i < breakPoints.size() + 1; i++) {
            if (i < breakPoints.size()) {
                currentWord = title.subSequence(stringPtr, breakPoints.get(i) + 1);
            } else {
                // last word from recent breakpoint until the end of the string
                currentWord = title.subSequence(stringPtr, title.length());
            }
            currentWordWidth = paint.measureText(currentWord, 0, currentWord.length());
            runningWidth += currentWordWidth;
            if (runningWidth <= limitedWidth) {
                newString.append(currentWord);
            } else {
                if (i != 0) {
                    // If putting word onto a new line, make sure there is no space or new line
                    // character in the beginning of the current word and just put in the rest of
                    // the characters.
                    CharSequence lastCharacters = title.subSequence(stringPtr, title.length());
                    int beginningLetterType =
                            Character.getType(Character.codePointAt(lastCharacters, 0));
                    if (beginningLetterType == Character.SPACE_SEPARATOR
                            || beginningLetterType == Character.LINE_SEPARATOR) {
                        lastCharacters = lastCharacters.length() > 1
                                ? lastCharacters.subSequence(1, lastCharacters.length())
                                : EMPTY;
                    }
                    newString.append(NEW_LINE).append(lastCharacters);
                    StaticLayout staticLayout = new StaticLayout(newString, paint, limitedWidth,
                            ALIGN_NORMAL, spacingMultiplier, spacingExtra, false);
                    if (staticLayout.getHeight() < limitedHeight) {
                        return newString.toString();
                    }
                }
                // if the first words exceeds width, just return as the first line will ellipse
                return title;
            }
            if (i >= breakPoints.size()) {
                // no need to look forward into the string if we've already finished processing
                break;
            }
            stringPtr = breakPoints.get(i) + 1;
        }
        return newString.toString();
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mLongPressHelper.cancelLongPress();
    }

    /** Applies the given progress level to the this icon's progress bar. */
    @Nullable
    private PreloadIconDelegate applyProgressLevel(ItemInfoWithIcon info) {
        int progressLevel = info.getProgressLevel();
        if (progressLevel >= 100) {
            setContentDescription(info.contentDescription != null
                    ? info.contentDescription : "");
        } else if (progressLevel > 0) {
            setDownloadStateContentDescription(info, progressLevel);
        } else {
            setContentDescription(getContext()
                    .getString(R.string.app_waiting_download_title, info.title));
        }
        PreloadIconDelegate pid = extractPreloadDelegate(mIcon);
        if (pid != null) {
            pid.reapplyProgress(info);
        } else {
            setIcon(newPendingIcon(info, getContext(), getIconCreationFlagsForInfo(info)));
            pid = extractPreloadDelegate(mIcon);
        }
        return pid;
    }

    public void applyDotState(ItemInfo itemInfo, boolean animate) {
        if (mIcon != null) {
            boolean wasDotted = mDotInfo != null;
            mDotInfo = mActivity.getDotInfoForItem(itemInfo);
            boolean isDotted = mDotInfo != null;
            float newDotScale = isDotted ? 1f : 0;
            if (wasDotted || isDotted) {
                // Animate when a dot is first added or when it is removed.
                if (animate && (wasDotted ^ isDotted) && isShown()) {
                    animateDotScale(newDotScale);
                } else {
                    cancelDotScaleAnim();
                    mDotParams.scale = newDotScale;
                    invalidate();
                }
            }
            if (!TextUtils.isEmpty(itemInfo.contentDescription)) {
                if (itemInfo.isDisabled()) {
                    setContentDescription(getContext().getString(R.string.disabled_app_label,
                            itemInfo.contentDescription));
                } else if (itemInfo instanceof WorkspaceItemInfo wai && wai.isArchived()) {
                    setContentDescription(
                            getContext().getString(R.string.app_archived_title, itemInfo.title));
                } else if (hasDot()) {
                    int count = mDotInfo.getNotificationCount();
                    setContentDescription(
                            getAppLabelPluralString(itemInfo.contentDescription.toString(), count));
                } else {
                    setContentDescription(itemInfo.contentDescription);
                }
            }
        }
    }

    private void setDownloadStateContentDescription(ItemInfoWithIcon info, int progressLevel) {
        if ((info.runtimeStatusFlags & ItemInfoWithIcon.FLAG_ARCHIVED) != 0
                && progressLevel == 0) {
            if (info.shouldShowPendingIcon()) {
                // Tell user that download is pending and not to tap to download again.
                setContentDescription(getContext().getString(
                        R.string.app_waiting_download_title, info.title));
            } else {
                setContentDescription(getContext().getString(
                        R.string.app_archived_title, info.title));
            }
        } else if ((info.runtimeStatusFlags & FLAG_SHOW_DOWNLOAD_PROGRESS_MASK)
                != 0) {
            String percentageString = NumberFormat.getPercentInstance()
                    .format(progressLevel * 0.01);
            if ((info.runtimeStatusFlags & FLAG_INSTALL_SESSION_ACTIVE) != 0) {
                setContentDescription(getContext()
                        .getString(
                                R.string.app_installing_title, info.title, percentageString));
            } else if ((info.runtimeStatusFlags
                    & FLAG_INCREMENTAL_DOWNLOAD_ACTIVE) != 0) {
                setContentDescription(getContext()
                        .getString(
                                R.string.app_downloading_title, info.title, percentageString));
            }
        }
    }

    /**
     * Sets the icon for this view based on the layout direction.
     */
    protected void setIcon(FastBitmapDrawable icon) {
        if (mIsIconVisible) {
            applyCompoundDrawables(icon);
        }
        mIcon = icon;
        if (mIcon != null) {
            mIcon.setVisible(getWindowVisibility() == VISIBLE && isShown(), false);
            mIcon.setHoverScaleEnabledForDisplay(mDisplay != DISPLAY_TASKBAR);
        }
    }

    @Override
    public void setIconVisible(boolean visible) {
        mIsIconVisible = visible;
        if (!mIsIconVisible) {
            resetIconScale();
        }
        Drawable icon = getIconOrTransparentColor();
        applyCompoundDrawables(icon);
        if (isMultiSpan()) {
            invalidate();
        }
    }

    private Drawable getIconOrTransparentColor() {
        return mIsIconVisible ? mIcon : new ColorDrawable(Color.TRANSPARENT);
    }

    /** Sets the icon visual state to disabled or not. */
    public void setIconDisabled(boolean isDisabled) {
        if (mIcon != null) {
            mIcon.setDisabled(isDisabled);
        }
    }

    protected void applyCompoundDrawables(Drawable icon) {
        if (icon == null) {
            // Icon can be null when we use the BubbleTextView for text only.
            return;
        }

        // If we had already set an icon before, disable relayout as the icon size is the
        // same as before.
        mDisableRelayout = mIcon != null;

        icon.setBounds(0, 0, mIconSize, mIconSize);

        updateIcon(icon);

        // If the current icon is a placeholder color, animate its update.
        if (mIcon != null
                && (mIcon.getDelegate() instanceof PlaceHolderDrawableDelegate delegate)
                && mHighResUpdateInProgress) {
            delegate.animateIconUpdate(icon);
        }

        mDisableRelayout = false;
    }

    @Override
    public void requestLayout() {
        if (!mDisableRelayout) {
            super.requestLayout();
        }
    }

    /**
     * Applies the item info if it is same as what the view is pointing to currently.
     */
    @Override
    public void reapplyItemInfo(ItemInfoWithIcon info) {
        if (getTag() == info) {
            mIconLoadRequest = null;
            mDisableRelayout = true;
            mHighResUpdateInProgress = true;

            // Optimization: Starting in N, pre-uploads the bitmap to RenderThread.
            info.bitmap.icon.prepareToDraw();

            if (info instanceof AppInfo) {
                applyFromApplicationInfo((AppInfo) info);
            } else if (info instanceof WorkspaceItemInfo) {
                applyFromWorkspaceItem((WorkspaceItemInfo) info);
            } else if (info != null) {
                applyFromItemInfoWithIcon(info);
            }

            mDisableRelayout = false;
            mHighResUpdateInProgress = false;
        }
    }

    /**
     * Verifies that the current icon is high-res otherwise posts a request to load the icon.
     */
    public void verifyHighRes() {
        CacheLookupFlag expectedFlag = DEFAULT_LOOKUP_FLAG.withThemeIcon(shouldUseTheme());
        if (getTag() instanceof ItemInfoWithIcon info && !mHighResUpdateInProgress
                && info.getMatchingLookupFlag().isVisuallyLessThan(expectedFlag)) {
            if (mIconLoadRequest != null) {
                mIconLoadRequest.cancel();
            }
            mIconLoadRequest = LauncherAppState.getInstance(getContext()).getIconCache()
                    .updateIconInBackground(BubbleTextView.this, info, expectedFlag);
        }
    }

    public int getIconSize() {
        return mIconSize;
    }

    public boolean isDisplaySearchResult() {
        return mDisplay == DISPLAY_SEARCH_RESULT
                || mDisplay == DISPLAY_SEARCH_RESULT_SMALL
                || mDisplay == DISPLAY_SEARCH_RESULT_APP_ROW;
    }

    public int getIconDisplay() {
        return mDisplay;
    }

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

    @Override
    public int getViewType() {
        return DRAGGABLE_ICON;
    }

    @Override
    public void getWorkspaceVisualDragBounds(Rect bounds) {
        if (isMultiSpan()) {
            bounds.set(getMultiSpanBackgroundBounds());
            return;
        }
        getIconBounds(mIconSize, bounds);
    }

    public void getSourceVisualDragBounds(Rect bounds) {
        if (isMultiSpan()) {
            bounds.set(getMultiSpanBackgroundBounds());
            return;
        }
        getIconBounds(mIconSize, bounds);
    }

    @Override
    public SafeCloseable prepareDrawDragView() {
        resetIconScale();
        setForceHideDot(true);
        return () -> {
        };
    }

    private void resetIconScale() {
        if (mIcon != null) {
            mIcon.resetScale();
        }
    }

    private void updateIcon(Drawable newIcon) {
        if (mLayoutHorizontal) {
            setCompoundDrawablesRelative(newIcon, null, null, null);
        } else {
            setCompoundDrawables(null, newIcon, null, null);
        }
    }

    private String getAppLabelPluralString(String appName, int notificationCount) {
        MessageFormat icuCountFormat = new MessageFormat(
                getResources().getString(R.string.dotted_app_label),
                Locale.getDefault());
        HashMap<String, Object> args = new HashMap();
        args.put("app_name", appName);
        args.put("count", notificationCount);
        return icuCountFormat.format(args);
    }

    /**
     * Starts a long press action and returns the corresponding pre-drag condition
     */
    public PreDragCondition startLongPressAction(PopupController<?> popupController) {
        Popup popup = popupController.show(this);
        return popup != null ? popup.createPreDragCondition() : null;
    }

    /**
     * Returns true if the view can show long-press popup
     */
    public boolean canShowLongPressPopup() {
        return getTag() instanceof ItemInfo && ShortcutUtil.supportsShortcuts((ItemInfo) getTag());
    }
}
