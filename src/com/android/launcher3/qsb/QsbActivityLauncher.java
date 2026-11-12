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
package com.android.launcher3.qsb;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Process;
import android.view.View;

import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.views.ActivityContext;

/** Launches search activities with launcher transitions while preserving fallback handling. */
final class QsbActivityLauncher {

    private QsbActivityLauncher() {}

    static WorkspaceItemInfo createItemInfo(Intent intent) {
        WorkspaceItemInfo info = new WorkspaceItemInfo();
        info.intent = new Intent(intent);
        if (info.intent.getComponent() == null && info.intent.getPackage() != null) {
            info.intent.setComponent(new ComponentName(info.intent.getPackage(), ""));
        }
        info.id = R.id.search_container_hotseat;
        info.container = LauncherSettings.Favorites.CONTAINER_HOTSEAT;
        info.itemType = LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
        info.user = Process.myUserHandle();
        return info;
    }

    static void startActivity(Context context, View view, Intent intent) {
        if (intent == null) {
            throw new ActivityNotFoundException("No search intent");
        }
        ActivityInfo activity = intent.resolveActivityInfo(context.getPackageManager(), 0);
        if (activity == null) {
            throw new ActivityNotFoundException("No activity for " + intent);
        }
        if (activity.applicationInfo.uid != Process.myUid()
                && (!activity.exported || (activity.permission != null
                && context.checkSelfPermission(activity.permission)
                        != PackageManager.PERMISSION_GRANTED))) {
            throw new SecurityException("Search activity is not accessible: " + activity.name);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (view != null) {
            intent.setSourceBounds(Utilities.getViewBounds(view));
        }
        ActivityContext activityContext = ActivityContext.lookupContextNoThrow(context);
        if (activityContext != null) {
            WorkspaceItemInfo item = createItemInfo(intent);
            if (activityContext.startActivitySafely(view, intent, item) == null) {
                throw new ActivityNotFoundException("Search activity launch failed: " + intent);
            }
            if (activityContext instanceof Launcher launcher) {
                Hotseat hotseat = launcher.getHotseat();
                if (hotseat != null && hotseat.isQsbView(view)) {
                    hotseat.setQsbItemInfo(item);
                }
            }
        } else {
            Bundle options = view == null ? null : ActivityOptions.makeScaleUpAnimation(
                    view, 0, 0, view.getWidth(), view.getHeight()).toBundle();
            context.startActivity(intent, options);
        }
    }
}
