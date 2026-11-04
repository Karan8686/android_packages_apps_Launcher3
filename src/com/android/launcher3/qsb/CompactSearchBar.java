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

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.launcher3.LauncherPrefChangeListener;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherPrefsExt;
import com.android.launcher3.R;
import com.android.launcher3.Reorderable;
import com.android.launcher3.Utilities;
import com.android.launcher3.graphics.ThemeManager;
import com.android.launcher3.util.MultiTranslateDelegate;
import com.android.launcher3.util.Themes;

public class CompactSearchBar extends FrameLayout
        implements Reorderable {

    private static final String TAG = "CompactSearchBar";
    private static final String ACTION_GOOGLE_SEARCH = "google_search";
    private static final String ACTION_LENS = "lens";
    private static final String ACTION_MIC = "mic";
    private static final String ACTION_GEMINI = "gemini";

    private final MultiTranslateDelegate mTranslateDelegate = new MultiTranslateDelegate(this);
    private final Context mContext;
    private float mScaleForReorderBounce = 1f;

    private ImageView mActionIcon;
    private View mInner;
    private ThemeManager.ThemeChangeListener mThemeChangeListener;
    private final LauncherPrefChangeListener mPrefListener = key -> {
        if (LauncherPrefsExt.COMPACT_SEARCH_BAR_ACTION.getSharedPrefKey().equals(key)) {
            setIcon();
            setUpBackground();
        }
    };

    public CompactSearchBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public CompactSearchBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mActionIcon = findViewById(R.id.gemini_icon);
        mInner = findViewById(R.id.compact_search_bar_inner);

        setIcon();
        setUpBackground();
        setUpClick();

        mThemeChangeListener = () -> {
            setIcon();
            setUpBackground();
        };
        ThemeManager.INSTANCE.get(mContext).addChangeListener(mThemeChangeListener);
        LauncherPrefs.get(mContext).addListener(mPrefListener, LauncherPrefsExt.COMPACT_SEARCH_BAR_ACTION);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mThemeChangeListener != null) {
            ThemeManager.INSTANCE.get(mContext).removeChangeListener(mThemeChangeListener);
            mThemeChangeListener = null;
        }
        LauncherPrefs.get(mContext).removeListener(mPrefListener, LauncherPrefsExt.COMPACT_SEARCH_BAR_ACTION);
        if (mInner != null) {
            mInner.setOnClickListener(null);
            mInner.setBackground(null);
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
        boolean isThemed = ThemeManager.INSTANCE.get(mContext).isMonoThemeEnabled();
        int baseColor = isThemed
                ? Themes.getAttrColor(mContext, R.attr.qsbFillColorThemed)
                : Themes.getAttrColor(mContext, R.attr.qsbFillColor);

        if (baseColor == 0) {
            baseColor = Themes.getAttrColor(mContext, R.attr.allAppsSurfaceHighest);
        }

        PaintDrawable backgroundDrawable = new PaintDrawable(baseColor);
        backgroundDrawable.setCornerRadius(cornerRadius);
        mInner.setBackground(backgroundDrawable);
        mInner.setClipToOutline(cornerRadius > 0);
    }

    private float getCornerRadius() {
        Resources res = mContext.getResources();
        return res.getDimension(R.dimen.compact_search_bar_height) / 2f;
    }

    private void setUpClick() {
        View.OnClickListener listener = view -> launchAction();
        if (mInner != null) {
            mInner.setOnClickListener(listener);
        }
    }

    private void launchAction() {
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
        if (searchPackage != null) {
            try {
                mContext.startActivity(new Intent("android.search.action.GLOBAL_SEARCH")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .setPackage(searchPackage));
                return;
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "GLOBAL_SEARCH not found for " + searchPackage);
            }

            try {
                Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(searchPackage);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    mContext.startActivity(intent);
                    return;
                }
            } catch (ActivityNotFoundException e) {
                Log.d(TAG, "Launch intent not found for " + searchPackage);
            }
        }

        // Generic fallback to web search or browser
        try {
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            mContext.startActivity(intent);
            return;
        } catch (Exception e) {
            Log.d(TAG, "Web search fallback failed", e);
        }

        Toast.makeText(mContext, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
    }

    private void launchLens() {
        try {
            mContext.startActivity(new Intent(Intent.ACTION_VIEW)
                    .setComponent(new ComponentName(Utilities.GSA_PACKAGE, Utilities.LENS_ACTIVITY))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .setData(Uri.parse(Utilities.LENS_URI))
                    .putExtra("LensHomescreenShortcut", true));
        } catch (Exception e) {
            Log.e(TAG, "Lens launch failed", e);
            Toast.makeText(mContext, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void launchMic() {
        try {
            Intent intent = new Intent("android.intent.action.VOICE_COMMAND");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            mContext.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Mic launch failed", e);
            Toast.makeText(mContext, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void launchGemini() {
        try {
            Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(
                    Utilities.GEMINI_PACKAGE);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                mContext.startActivity(intent);
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Gemini launch failed", e);
        }
        Toast.makeText(mContext, R.string.activity_not_found, Toast.LENGTH_SHORT).show();
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
}
