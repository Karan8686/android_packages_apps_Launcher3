/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.launcher3.views;

import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.Utilities.boundToRange;
import static com.android.launcher3.Utilities.mapToRange;
import static com.android.launcher3.anim.AnimatorListeners.forEndCallback;
import static com.android.launcher3.icons.IconNormalizer.ICON_VISIBLE_AREA_FACTOR;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;

import static java.lang.Math.max;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.ViewOutlineProvider;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.android.axion.iconloader.AdaptiveIconHelper;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.LauncherPrefsExt;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.dragndrop.FolderAdaptiveIcon;
import com.android.launcher3.graphics.ShapeDelegate;
import com.android.launcher3.graphics.ThemeManager;

/**
 * A view used to draw both layers of an {@link AdaptiveIconDrawable}.
 * Supports springing just the foreground layer.
 * Supports clipping the icon to/from its icon shape.
 */
public class ClipIconView extends View implements ClipPathView {

    private static final Rect sTmpRect = new Rect();

    private final int mBlurSizeOutline;
    private final boolean mIsRtl;

    private @Nullable Drawable mForeground;
    private @Nullable Drawable mBackground;
    private ShapeDelegate mCurrentShape;

    private boolean mIsAdaptiveIcon = false;
    private boolean mIsFolderIcon = false;
    private boolean mIsMultiSpanSuperIcon = false;
    private int mOriginalWidth = 0;
    private int mOriginalHeight = 0;
    private int mSuperIconSpanX = 1;
    private int mSuperIconSpanY = 1;
    private float mSuperIconCornerRadius = 0f;

    private ValueAnimator mRevealAnimator;

    private final Rect mStartRevealRect = new Rect();
    private final Rect mEndRevealRect = new Rect();
    private Path mClipPath;
    private float mTaskCornerRadius;

    private final Rect mOutline = new Rect();
    private final Rect mFinalDrawableBounds = new Rect();

    @Nullable private TaskViewArtist mTaskViewArtist;

    public ClipIconView(Context context) {
        this(context, null);
    }

    public ClipIconView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClipIconView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mBlurSizeOutline = getResources().getDimensionPixelSize(
                R.dimen.blur_size_medium_outline);
        mIsRtl = Utilities.isRtl(getResources());
    }

    public void setMultiSpanSuperIconParams(boolean isMultiSpan, int spanX, int spanY,
            float cornerRadius) {
        mIsMultiSpanSuperIcon = isMultiSpan;
        mSuperIconSpanX = spanX;
        mSuperIconSpanY = spanY;
        mSuperIconCornerRadius = cornerRadius;
    }

    /**
     * Sets a {@link TaskViewArtist} that will draw a {@link com.android.quickstep.views.TaskView}
     * within the clip bounds of this view.
     */
    public void setTaskViewArtist(TaskViewArtist taskViewArtist) {
        mTaskViewArtist = taskViewArtist;
        invalidate();
    }

    /**
     * Update the icon UI to match the provided parameters during an animation frame
     */
    public void update(RectF rect, float progress, float shapeProgressStart, float cornerRadius,
            boolean isOpening, View container, DeviceProfile dp) {
        update(rect, progress, shapeProgressStart, cornerRadius, isOpening, container, dp, 255);
    }

    /**
     * Update the icon UI to match the provided parameters during an animation frame, optionally
     * varying the alpha of the {@link TaskViewArtist}
     */
    public void update(RectF rect, float progress, float shapeProgressStart, float cornerRadius,
            boolean isOpening, View container, DeviceProfile dp, int taskViewDrawAlpha) {
        update(rect, progress, shapeProgressStart, cornerRadius, isOpening, container, dp,
                taskViewDrawAlpha, 1f, 1f);
    }

    void update(RectF rect, float progress, float shapeProgressStart, float cornerRadius,
            boolean isOpening, View container, DeviceProfile dp, int taskViewDrawAlpha,
            float foregroundScale, float foregroundAlpha) {
        updateInternal(rect, progress, shapeProgressStart, cornerRadius, isOpening, container, dp,
                taskViewDrawAlpha, foregroundScale, foregroundAlpha);
    }

    private void updateInternal(RectF rect, float progress, float shapeProgressStart,
            float cornerRadius, boolean isOpening, View container, DeviceProfile dp,
            int taskViewDrawAlpha, float foregroundScale, float foregroundAlpha) {
        MarginLayoutParams lp = (MarginLayoutParams) container.getLayoutParams();

        float dX = mIsRtl
                ? rect.left - (dp.getDeviceProperties().getWidthPx() - lp.getMarginStart() - lp.width)
                : rect.left - lp.getMarginStart();
        float dY = rect.top - lp.topMargin;
        container.setTranslationX(dX);
        container.setTranslationY(dY);

        float minSize = Math.min(lp.width, lp.height);
        final float scale;
        if (mIsMultiSpanSuperIcon && mOriginalWidth > 0 && mOriginalHeight > 0) {
            float scaleX = rect.width() / (float) mOriginalWidth;
            float scaleY = rect.height() / (float) mOriginalHeight;
            scale = Math.max(1f, Math.min(scaleX, scaleY));
        } else {
            float scaleX = rect.width() / minSize;
            float scaleY = rect.height() / minSize;
            scale = Math.max(1f, Math.min(scaleX, scaleY));
        }
        if (mTaskViewArtist != null) {
            mTaskViewArtist.taskViewDrawWidth = lp.width;
            mTaskViewArtist.taskViewDrawHeight = lp.height;
            mTaskViewArtist.taskViewDrawAlpha = taskViewDrawAlpha;
            mTaskViewArtist.taskViewDrawScale = (mTaskViewArtist.drawForPortraitLayout
                    ? Math.min(lp.height, lp.width) : Math.max(lp.height, lp.width))
                    / mTaskViewArtist.taskViewMinSize;
        }

        if (Float.isNaN(scale) || Float.isInfinite(scale)) {
            // Views are no longer laid out, do not update.
            return;
        }

        update(rect, progress, shapeProgressStart, cornerRadius, isOpening, scale, minSize, dp,
                foregroundScale, foregroundAlpha);

        container.setPivotX(0);
        container.setPivotY(0);
        container.setScaleX(scale);
        container.setScaleY(scale);

        container.invalidate();
    }

    private void update(RectF rect, float progress, float shapeProgressStart, float cornerRadius,
            boolean isOpening, float scale, float minSize, DeviceProfile dp,
            float foregroundScale, float foregroundAlpha) {
        // shapeRevealProgress = 1 when progress = shapeProgressStart + SHAPE_PROGRESS_DURATION
        float toMax = isOpening ? 1 / SHAPE_PROGRESS_DURATION : 1f;

        float shapeRevealProgress = boundToRange(mapToRange(max(shapeProgressStart, progress),
                shapeProgressStart, 1f, 0, toMax, LINEAR), 0, 1);

        if (mIsMultiSpanSuperIcon) {
            mOutline.set(0, 0, Math.round(rect.width() / scale), Math.round(rect.height() / scale));
            float windowRadius = cornerRadius / scale;
            float iconRadius = mSuperIconCornerRadius;
            float iconMorphFraction = isOpening ? (1f - shapeRevealProgress) : shapeRevealProgress;
            mTaskCornerRadius = windowRadius + iconMorphFraction * (iconRadius - windowRadius);
            if (mIsAdaptiveIcon) {
                updateMultiSpanSuperIconBounds();
                setForegroundAlpha(foregroundAlpha);
            }
            invalidate();
            invalidateOutline();
            return;
        }

        if (dp.getDeviceProperties().isLandscape()) {
            mOutline.right = (int) (rect.width() / scale);
        } else {
            mOutline.bottom = (int) (rect.height() / scale);
        }

        mTaskCornerRadius = cornerRadius / scale;
        if (mIsAdaptiveIcon) {
            if (shouldSkipShapeReveal()) {
                endReveal();
                setClipPath(null);
            } else if ((!isOpening || Flags.enableLauncherIconShapes())
                    && progress >= shapeProgressStart) {
                if (mRevealAnimator == null) {
                    ShapeDelegate shape;
                    if (Flags.enableLauncherIconShapes()) {
                        shape = mCurrentShape;
                    } else {
                        final ThemeManager themeManager = ThemeManager.INSTANCE.get(getContext());
                        shape = mIsFolderIcon ? themeManager.getFolderShape()
                                : themeManager.getIconShape();
                    }
                    mRevealAnimator = shape.createRevealAnimator(this, mStartRevealRect,
                            mOutline, mTaskCornerRadius, !isOpening);
                    mRevealAnimator.addListener(forEndCallback(() -> mRevealAnimator = null));
                    mRevealAnimator.start();
                    // We pause here so we can set the current fraction ourselves.
                    mRevealAnimator.pause();
                }
                mRevealAnimator.setCurrentFraction(shapeRevealProgress);
            }

            float drawableScale = (dp.getDeviceProperties().isLandscape() ? mOutline.width() : mOutline.height())
                    / minSize;
            setBackgroundDrawableBounds(drawableScale, dp.getDeviceProperties().isLandscape());

            // Center align foreground
            updateForegroundBounds(sTmpRect, mFinalDrawableBounds, drawableScale,
                    foregroundScale, dp.getDeviceProperties().isLandscape(), mIsRtl);
            setForegroundAlpha(foregroundAlpha);
            mForeground.setBounds(sTmpRect);
        }
        invalidate();
        invalidateOutline();
    }

    private void updateMultiSpanSuperIconBounds() {
        if (mBackground != null) {
            mBackground.setBounds(mOutline);
        }
        if (mForeground != null) {
            int baseW = mOriginalWidth > 0 ? mOriginalWidth : mOutline.width();
            int baseH = mOriginalHeight > 0 ? mOriginalHeight : mOutline.height();
            int glyphTargetSize;
            if (mSuperIconSpanX == 2 && mSuperIconSpanY == 1) {
                glyphTargetSize = Math.round(baseH * 0.62f);
            } else if (mSuperIconSpanX == 1 && mSuperIconSpanY == 2) {
                glyphTargetSize = Math.round(baseW * 0.62f);
            } else if (mSuperIconSpanX == 2 && mSuperIconSpanY == 2) {
                glyphTargetSize = Math.round(Math.min(baseW, baseH) * 0.52f);
            } else {
                glyphTargetSize = Math.round(Math.min(baseW, baseH) * 0.62f);
            }
            int fullFgSize = Math.round(glyphTargetSize * 1.5f);
            int fgLeft = Math.round(mOutline.centerX() - fullFgSize / 2f);
            int fgTop = Math.round(mOutline.centerY() - fullFgSize / 2f);
            mForeground.setBounds(fgLeft, fgTop, fgLeft + fullFgSize, fgTop + fullFgSize);
        }
    }

    void setForegroundAlpha(float alpha) {
        int drawableAlpha = Math.round(255f * boundToRange(alpha, 0f, 1f));
        if (mForeground != null) {
            mForeground.setAlpha(drawableAlpha);
        } else if (getBackground() != null) {
            getBackground().setAlpha(drawableAlpha);
        }
    }

    boolean isAdaptiveIcon() {
        return mIsAdaptiveIcon;
    }

    protected boolean shouldSkipShapeReveal() {
        return false;
    }

    protected float getForegroundScale(float foregroundScale, float drawableScale) {
        return foregroundScale;
    }

    protected void updateForegroundBounds(Rect outBounds, Rect finalDrawableBounds,
            float drawableScale, float foregroundScale, boolean isLandscape, boolean isRtl) {
        int height = finalDrawableBounds.height();
        int width = finalDrawableBounds.width();
        int diffY = isLandscape ? 0 : (int) (((height * drawableScale) - height) / 2);
        int diffX = isLandscape ? (int) (((width * drawableScale) - width) / 2) : 0;
        outBounds.set(finalDrawableBounds);
        outBounds.offset(diffX, diffY);
        float scaledForeground = getForegroundScale(foregroundScale, drawableScale);
        if (scaledForeground != 1f) {
            Utilities.scaleRectAboutCenter(outBounds, scaledForeground);
        }
    }

    private void setBackgroundDrawableBounds(float scale, boolean isLandscape) {
        sTmpRect.set(mFinalDrawableBounds);
        Utilities.scaleRectAboutCenter(sTmpRect, scale);
        // Since the drawable is at the top of the view, we need to offset to keep it centered.
        if (isLandscape) {
            sTmpRect.offsetTo((int) (mFinalDrawableBounds.left * scale), sTmpRect.top);
        } else {
            sTmpRect.offsetTo(sTmpRect.left, (int) (mFinalDrawableBounds.top * scale));
        }
        mBackground.setBounds(sTmpRect);
    }

    protected void endReveal() {
        if (mRevealAnimator != null) {
            mRevealAnimator.end();
        }
    }

    /**
     * Sets the icon for this view as part of initial setup
     */
    public void setIcon(@Nullable Drawable drawable, int iconOffset, MarginLayoutParams lp,
            boolean isOpening, boolean usingCustomShape, DeviceProfile dp) {
        boolean disableAdaptive =
                LauncherPrefsExt.isAdaptiveDisabled(getContext());
        mIsAdaptiveIcon = drawable instanceof AdaptiveIconDrawable
                && (!disableAdaptive || !AdaptiveIconHelper.canUnwrapAdaptiveIcon((AdaptiveIconDrawable) drawable));
        if (mIsAdaptiveIcon) {
            mIsFolderIcon = drawable instanceof FolderAdaptiveIcon;
            final ThemeManager themeManager = ThemeManager.INSTANCE.get(getContext());
            if (mIsFolderIcon) {
                mCurrentShape = themeManager.getFolderShape();
            } else if (usingCustomShape) {
                mCurrentShape = themeManager.getIconShape();
            } else {
                mCurrentShape = ThemeManager.DEFAULT_SHAPE_DELEGATE;
            }

            AdaptiveIconDrawable adaptiveIcon = (AdaptiveIconDrawable) drawable;
            Drawable background = adaptiveIcon.getBackground();
            if (background == null) {
                background = new ColorDrawable(Color.TRANSPARENT);
            }
            mBackground = background;
            Drawable foreground = adaptiveIcon.getForeground();
            if (foreground == null) {
                foreground = new ColorDrawable(Color.TRANSPARENT);
            }
            mForeground = foreground;

            final int originalHeight = lp.height;
            final int originalWidth = lp.width;
            mOriginalWidth = originalWidth;
            mOriginalHeight = originalHeight;

            int blurMargin = mBlurSizeOutline / 2;
            mFinalDrawableBounds.set(0, 0, originalWidth, originalHeight);

            if (!mIsFolderIcon && !mIsMultiSpanSuperIcon) {
                mFinalDrawableBounds.inset(iconOffset - blurMargin, iconOffset - blurMargin);
            }
            mForeground.setBounds(mFinalDrawableBounds);
            mBackground.setBounds(mFinalDrawableBounds);

            mStartRevealRect.set(0, 0, originalWidth, originalHeight);

            if (!mIsFolderIcon && !mIsMultiSpanSuperIcon) {
                Utilities.scaleRectAboutCenter(mStartRevealRect, ICON_VISIBLE_AREA_FACTOR);
            }

            float aspectRatio = dp.getDeviceProperties().getAspectRatio();
            if (dp.getDeviceProperties().isLandscape()) {
                lp.width = (int) Math.max(lp.width, lp.height * aspectRatio);
                if (mIsMultiSpanSuperIcon && aspectRatio > 0) {
                    lp.height = (int) Math.max(lp.height, Math.ceil(originalWidth / aspectRatio));
                }
            } else {
                lp.height = (int) Math.max(lp.height, lp.width * aspectRatio);
                if (mIsMultiSpanSuperIcon && aspectRatio > 0) {
                    lp.width = (int) Math.max(lp.width, Math.ceil(originalHeight / aspectRatio));
                }
            }

            int left = mIsRtl
                    ? dp.getDeviceProperties().getWidthPx() - lp.getMarginStart() - lp.width
                    : lp.leftMargin;
            layout(left, lp.topMargin, left + lp.width, lp.topMargin + lp.height);

            if (mIsMultiSpanSuperIcon) {
                if (isOpening) {
                    mOutline.set(0, 0, originalWidth, originalHeight);
                    mTaskCornerRadius = mSuperIconCornerRadius;
                } else {
                    mOutline.set(0, 0, lp.width, lp.height);
                }
                updateMultiSpanSuperIconBounds();
            } else {
                float scale = Math.max((float) lp.height / originalHeight,
                        (float) lp.width / originalWidth);
                float bgDrawableStartScale;
                if (isOpening) {
                    bgDrawableStartScale = 1f;
                    mOutline.set(0, 0, originalWidth, originalHeight);
                } else {
                    bgDrawableStartScale = scale;
                    mOutline.set(0, 0, lp.width, lp.height);
                }
                setBackgroundDrawableBounds(bgDrawableStartScale,
                        dp.getDeviceProperties().isLandscape());
            }
            mEndRevealRect.set(0, 0, lp.width, lp.height);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(mOutline, mTaskCornerRadius);
                }
            });
            setClipToOutline(true);
        } else {
            mIsFolderIcon = false;
            setBackground(drawable);
            setClipToOutline(false);
            int left = mIsRtl
                    ? dp.getDeviceProperties().getWidthPx() - lp.getMarginStart() - lp.width
                    : lp.leftMargin;
            layout(left, lp.topMargin, left + lp.width, lp.topMargin + lp.height);
        }

        invalidate();
        invalidateOutline();
    }

    @Override
    public void setClipPath(Path clipPath) {
        mClipPath = clipPath;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        int count1 = canvas.save();
        if (mClipPath != null) {
            canvas.clipPath(mClipPath);
        }
        int count2 = canvas.save();
        if (mBackground != null) {
            mBackground.draw(canvas);
        }
        if (mForeground != null) {
            mForeground.draw(canvas);
        }
        canvas.restoreToCount(count2);
        super.draw(canvas);
        if (mTaskViewArtist != null) {
            canvas.saveLayerAlpha(
                    0,
                    0,
                    mTaskViewArtist.taskViewDrawWidth,
                    mTaskViewArtist.taskViewDrawHeight,
                    mTaskViewArtist.taskViewDrawAlpha);
            float drawScale = mTaskViewArtist.taskViewDrawScale;
            canvas.translate(drawScale * mTaskViewArtist.taskViewTranslationX,
                    drawScale * mTaskViewArtist.taskViewTranslationY);
            canvas.scale(drawScale, drawScale);
            mTaskViewArtist.taskViewDrawCallback.accept(canvas);
        }
        canvas.restoreToCount(count1);
    }

    void recycle() {
        setBackground(null);
        mIsAdaptiveIcon = false;
        mIsFolderIcon = false;
        mIsMultiSpanSuperIcon = false;
        mOriginalWidth = 0;
        mOriginalHeight = 0;
        mSuperIconSpanX = 1;
        mSuperIconSpanY = 1;
        mSuperIconCornerRadius = 0f;
        mForeground = null;
        mBackground = null;
        mClipPath = null;
        mFinalDrawableBounds.setEmpty();
        if (mRevealAnimator != null) {
            mRevealAnimator.cancel();
        }
        mRevealAnimator = null;
        mTaskCornerRadius = 0;
        mOutline.setEmpty();
        mTaskViewArtist = null;
    }

    /**
     * Utility class to help draw a {@link com.android.quickstep.views.TaskView} within
     * a {@link ClipIconView} bounds.
     */
    public static class TaskViewArtist {

        public final Consumer<Canvas> taskViewDrawCallback;
        public final float taskViewTranslationX;
        public final float taskViewTranslationY;
        public final float taskViewMinSize;
        public final boolean drawForPortraitLayout;

        public int taskViewDrawAlpha;
        public float taskViewDrawScale;
        public int taskViewDrawWidth;
        public int taskViewDrawHeight;

        public TaskViewArtist(
                Consumer<Canvas> taskViewDrawCallback,
                float taskViewTranslationX,
                float taskViewTranslationY,
                float taskViewMinSize,
                boolean drawForPortraitLayout) {
            this.taskViewDrawCallback = taskViewDrawCallback;
            this.taskViewTranslationX = taskViewTranslationX;
            this.taskViewTranslationY = taskViewTranslationY;
            this.taskViewMinSize = taskViewMinSize;
            this.drawForPortraitLayout = drawForPortraitLayout;
        }
    }
}
