/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.launcher3.shortcuts;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.os.UserHandle;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.icons.IconCache;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.popup.PopupPopulator;
import com.android.launcher3.util.ApplicationInfoWrapper;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper to discover, filter, and cache deep shortcuts for multi-span Super Icons (Quick Functions).
 */
public class SuperIconShortcutHelper {

    private static final int MAX_CACHE_SIZE = 50;
    private static final LruCache<ComponentKey, List<WorkspaceItemInfo>> sCache =
            new LruCache<>(MAX_CACHE_SIZE);

    public interface Callback {
        void onShortcutsLoaded(@NonNull List<WorkspaceItemInfo> shortcuts);
    }

    @Nullable
    public static List<WorkspaceItemInfo> getCachedShortcuts(@Nullable ItemInfo appInfo) {
        if (appInfo == null) {
            return null;
        }
        final ComponentKey key = appInfo.getComponentKey();
        if (key == null) {
            return null;
        }
        synchronized (sCache) {
            return sCache.get(key);
        }
    }

    /**
     * Asynchronously loads up to 2 published shortcuts for the specified app item.
     */
    public static void loadShortcutsForApp(
            @NonNull Context context,
            @NonNull ItemInfo appInfo,
            @NonNull Callback callback) {
        final ComponentKey key = appInfo.getComponentKey();
        if (key == null) {
            callback.onShortcutsLoaded(Collections.emptyList());
            return;
        }

        // 1. Check memory cache first
        synchronized (sCache) {
            List<WorkspaceItemInfo> cached = sCache.get(key);
            if (cached != null) {
                callback.onShortcutsLoaded(cached);
                return;
            }
        }

        final ComponentName activity = appInfo.getTargetComponent();
        final UserHandle user = appInfo.user;
        final String targetPackage = appInfo.getTargetPackage();
        if (targetPackage == null) {
            callback.onShortcutsLoaded(Collections.emptyList());
            return;
        }

        final Context appContext = context.getApplicationContext();

        // 2. Query in background thread
        MODEL_EXECUTOR.getHandler().postAtFrontOfQueue(() -> {
            List<ShortcutInfo> shortcuts = Collections.emptyList();
            try {
                if (activity != null) {
                    shortcuts = new ShortcutRequest(appContext, user)
                            .withContainer(activity)
                            .query(ShortcutRequest.PUBLISHED);
                }
                if (shortcuts.size() < 2) {
                    List<ShortcutInfo> pkgShortcuts = new ShortcutRequest(appContext, user)
                            .forPackage(targetPackage)
                            .query(ShortcutRequest.PUBLISHED);
                    if (pkgShortcuts != null && !pkgShortcuts.isEmpty()) {
                        shortcuts = pkgShortcuts;
                    }
                }
                shortcuts = PopupPopulator.sortAndFilterShortcuts(shortcuts);
            } catch (Exception e) {
                // Ignore query exceptions
            }

            final List<WorkspaceItemInfo> resultList = new ArrayList<>();
            if (shortcuts != null && !shortcuts.isEmpty()) {
                ApplicationInfoWrapper infoWrapper =
                        new ApplicationInfoWrapper(appContext, targetPackage, user);
                IconCache cache = LauncherAppState.getInstance(appContext).getIconCache();

                for (int i = 0; i < shortcuts.size() && i < 3; i++) {
                    ShortcutInfo si = shortcuts.get(i);
                    WorkspaceItemInfo item = new WorkspaceItemInfo(si, appContext);
                    item.rank = i;
                    item.container = LauncherSettings.Favorites.CONTAINER_SHORTCUTS;
                    cache.getShortcutIcon(item, si, infoWrapper);
                    resultList.add(item);
                }
            }

            synchronized (sCache) {
                sCache.put(key, resultList);
            }

            MAIN_EXECUTOR.execute(() -> callback.onShortcutsLoaded(resultList));
        });
    }

    /**
     * Clears cached shortcuts (e.g. on package update).
     */
    public static void clearCache() {
        synchronized (sCache) {
            sCache.evictAll();
        }
    }
}
