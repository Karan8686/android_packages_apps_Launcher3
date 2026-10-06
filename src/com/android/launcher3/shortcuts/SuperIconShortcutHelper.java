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
import com.android.launcher3.pm.UserCache;
import com.android.launcher3.popup.PopupPopulator;
import com.android.launcher3.shortcuts.ShortcutRequest.QueryResult;
import com.android.launcher3.util.ApplicationInfoWrapper;
import com.android.launcher3.util.ComponentKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * Asynchronously loads up to 3 published shortcuts for the specified app item.
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
            if (cached != null && !cached.isEmpty()) {
                callback.onShortcutsLoaded(cached);
                return;
            }
        }

        final ComponentName activity = appInfo.getTargetComponent();
        final UserHandle user = appInfo.user;
        final String targetPackage = appInfo.getTargetPackage();
        if (targetPackage == null || user == null) {
            callback.onShortcutsLoaded(Collections.emptyList());
            return;
        }

        final Context appContext = context.getApplicationContext();

        // 2. Query in background thread
        MODEL_EXECUTOR.getHandler().postAtFrontOfQueue(() -> {
            if (!UserCache.INSTANCE.get(appContext).isUserUnlocked(user)) {
                MAIN_EXECUTOR.execute(() -> callback.onShortcutsLoaded(Collections.emptyList()));
                return;
            }

            List<ShortcutInfo> shortcuts = Collections.emptyList();
            boolean querySucceeded = false;
            try {
                if (activity != null) {
                    QueryResult actResult = new ShortcutRequest(appContext, user)
                            .withContainer(activity)
                            .query(ShortcutRequest.PUBLISHED);
                    if (actResult.wasSuccess()) {
                        querySucceeded = true;
                        shortcuts = actResult;
                    }
                }
                if (shortcuts.size() < 2) {
                    QueryResult pkgResult = new ShortcutRequest(appContext, user)
                            .forPackage(targetPackage)
                            .query(ShortcutRequest.PUBLISHED);
                    if (pkgResult.wasSuccess()) {
                        querySucceeded = true;
                        if (!pkgResult.isEmpty()) {
                            shortcuts = pkgResult;
                        }
                    }
                }
                if (querySucceeded && !shortcuts.isEmpty()) {
                    shortcuts = PopupPopulator.sortAndFilterShortcuts(shortcuts);
                }
            } catch (Exception e) {
                // Ignore query exceptions
            }

            final List<WorkspaceItemInfo> resultList = new ArrayList<>();
            if (querySucceeded && shortcuts != null && !shortcuts.isEmpty()) {
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

            if (!resultList.isEmpty()) {
                synchronized (sCache) {
                    sCache.put(key, resultList);
                }
            }

            MAIN_EXECUTOR.execute(() -> callback.onShortcutsLoaded(resultList));
        });
    }

    /**
     * Clears cached shortcuts for a specific package and user.
     */
    public static void clearCacheForPackage(@Nullable String packageName, @Nullable UserHandle user) {
        if (packageName == null) {
            return;
        }
        synchronized (sCache) {
            Map<ComponentKey, List<WorkspaceItemInfo>> snapshot = sCache.snapshot();
            for (ComponentKey key : snapshot.keySet()) {
                if (packageName.equals(key.componentName.getPackageName())
                        && (user == null || user.equals(key.user))) {
                    sCache.remove(key);
                }
            }
        }
    }

    /**
     * Clears cached shortcuts for a set of packages and user.
     */
    public static void clearCacheForPackages(
            @Nullable Set<String> packageNames, @Nullable UserHandle user) {
        if (packageNames == null || packageNames.isEmpty()) {
            return;
        }
        synchronized (sCache) {
            Map<ComponentKey, List<WorkspaceItemInfo>> snapshot = sCache.snapshot();
            for (ComponentKey key : snapshot.keySet()) {
                if (packageNames.contains(key.componentName.getPackageName())
                        && (user == null || user.equals(key.user))) {
                    sCache.remove(key);
                }
            }
        }
    }

    /**
     * Clears all cached shortcuts.
     */
    public static void clearCache() {
        synchronized (sCache) {
            sCache.evictAll();
        }
    }
}
