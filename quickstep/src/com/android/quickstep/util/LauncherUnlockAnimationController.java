/*
 * Copyright 2025-2026 AxionOS
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

package com.android.quickstep.util;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.app.KeyguardManager;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;

import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.ScreenOnTracker;
import com.android.systemui.shared.system.smartspace.ILauncherUnlockAnimationController;

public final class LauncherUnlockAnimationController
        extends ILauncherUnlockAnimationController.Stub {

    private static final String TAG = "LauncherUnlock";
    private static final long DEFAULT_TOTAL_DURATION_MS = 500L;

    private final QuickstepLauncher mLauncher;
    private final ScreenOnTracker.ScreenOnListener mScreenOnListener =
            new ScreenOnTracker.ScreenOnListener() {
                @Override
                public void onScreenOnChanged(boolean isOn) {
                    if (!isOn) {
                        mWaitingForWakeAndUnlock = true;
                        mRetryCount = 0;
                    } else {
                        checkAndPlayWakeAndUnlock();
                    }
                }

                @Override
                public void onUserPresent() {
                    checkAndPlayWakeAndUnlock();
                }
            };

    private long mStartDelay;
    private long mTotalDuration = DEFAULT_TOTAL_DURATION_MS;
    private boolean mDestroyed;
    private boolean mWaitingForWakeAndUnlock;
    private long mWakeAndUnlockTriggerTime;
    private int mRetryCount;

    public LauncherUnlockAnimationController(QuickstepLauncher launcher) {
        mLauncher = launcher;
        ScreenOnTracker.INSTANCE.get(launcher).addListener(mScreenOnListener);
    }

    public void onResume() {
        checkAndPlayWakeAndUnlock();
    }

    public void onPause() {
        KeyguardManager km = mLauncher.getSystemService(KeyguardManager.class);
        if ((km != null && km.isKeyguardLocked())
                || !ScreenOnTracker.INSTANCE.get(mLauncher).isScreenOn()) {
            mWaitingForWakeAndUnlock = true;
            mRetryCount = 0;
        }
    }

    public void onStop() {
        mWaitingForWakeAndUnlock = true;
        mRetryCount = 0;
    }

    private void checkAndPlayWakeAndUnlock() {
        MAIN_EXECUTOR.execute(() -> {
            if (mDestroyed || !mWaitingForWakeAndUnlock) {
                return;
            }
            if (!mLauncher.isStarted() && !mLauncher.hasBeenResumed()) {
                return;
            }
            KeyguardManager km = mLauncher.getSystemService(KeyguardManager.class);
            if (km != null && km.isKeyguardLocked()) {
                if (mRetryCount < 6) {
                    mRetryCount++;
                    MAIN_EXECUTOR.getHandler().postDelayed(this::checkAndPlayWakeAndUnlock, 40);
                }
                return;
            }
            mRetryCount = 0;
            mWaitingForWakeAndUnlock = false;
            mWakeAndUnlockTriggerTime = SystemClock.uptimeMillis();
            playUnlockAnimation(true, DEFAULT_TOTAL_DURATION_MS, 0);
        });
    }

    @Override
    public void prepareForUnlock(boolean animateSmartspace, Rect lockscreenSmartspaceBounds,
            int selectedPage) {
        MAIN_EXECUTOR.execute(() -> {
            if (!mDestroyed) {
                mWaitingForWakeAndUnlock = false;
                prepareAnimation(true);
            }
        });
    }

    @Override
    public void playUnlockAnimation(boolean unlocked, long duration, long startDelay) {
        MAIN_EXECUTOR.execute(() -> {
            if (mDestroyed) {
                return;
            }
            mWaitingForWakeAndUnlock = false;
            long now = SystemClock.uptimeMillis();
            AnimatorPlaybackController existing = getPlaybackController();
            if (existing != null && existing.getAnimationPlayer().isRunning()
                    && (now - mWakeAndUnlockTriggerTime < mTotalDuration)) {
                return;
            }

            long totalDuration = duration + startDelay;
            boolean durationChanged = totalDuration != mTotalDuration || startDelay != mStartDelay;
            mStartDelay = startDelay;
            mTotalDuration = totalDuration > 0 ? totalDuration : DEFAULT_TOTAL_DURATION_MS;

            AnimatorPlaybackController controller = getPlaybackController();
            if (controller == null || durationChanged) {
                prepareAnimation(true);
                controller = getPlaybackController();
            }
            if (controller != null) {
                controller.start();
            }
        });
    }

    @Override
    public void setUnlockAmount(float amount, boolean forceIfAnimating) {
        MAIN_EXECUTOR.execute(() -> {
            if (mDestroyed) {
                return;
            }
            if (amount != 1f) {
                Log.e(TAG, "setUnlockAmount called with unsupported value " + amount);
                return;
            }
            // If wake-and-unlock animation was recently started, don't let SysUI abort it
            if (SystemClock.uptimeMillis() - mWakeAndUnlockTriggerTime < mTotalDuration) {
                return;
            }
            AnimatorPlaybackController controller = getPlaybackController();
            if (controller == null) {
                return;
            }
            if (!controller.getAnimationPlayer().isRunning() || forceIfAnimating) {
                controller.pause();
                controller.setPlayFraction(1f);
                controller.dispatchOnEnd();
            }
        });
    }

    @Override
    public void setSmartspaceSelectedPage(int selectedPage) { }

    @Override
    public void setSmartspaceVisibility(int visibility) { }

    @Override
    public void dispatchSmartspaceStateToSysui() { }

    public void destroy() {
        MAIN_EXECUTOR.execute(() -> {
            mDestroyed = true;
            ScreenOnTracker.INSTANCE.get(mLauncher).removeListener(mScreenOnListener);
            AnimatorPlaybackController controller = getPlaybackController();
            if (controller != null) {
                controller.dispatchOnCancel();
            }
        });
    }

    private void prepareAnimation(boolean force) {
        if (!mLauncher.isInState(LauncherState.NORMAL)) {
            mLauncher.getStateManager().goToState(LauncherState.NORMAL, false);
        }
        if (!mLauncher.isStarted() || force) {
            mLauncher.getAnimationCoordinator().setAnimation(
                    this, this::addUnlockAnimators, mTotalDuration);
        }
    }

    private void addUnlockAnimators(PendingAnimation animation) {
        float startFraction = mTotalDuration == 0 ? 0f : (float) mStartDelay / mTotalDuration;
        RingAppearAnimation.addAnimators(animation, mLauncher, startFraction);
    }

    private AnimatorPlaybackController getPlaybackController() {
        return mLauncher.getAnimationCoordinator().getPlaybackController(this);
    }
}
