/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.launcher3.popup

import android.content.Context
import android.view.View
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.dragndrop.LauncherDragController
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.widget.LauncherAppWidgetHostView

/**
 * Controller for home screen items: folders, app pairs, and widgets. This controller does not
 * handle apps or app shortcuts. This controller handles actions for the popups such as showing and
 * dismissing them.
 */
class PopupControllerForExtraHomeScreenItems<T>(
    private val popupDataRepository: PopupDataRepository,
    private val dragController: LauncherDragController,
) : PopupController<T> where T : Context, T : ActivityContext {
    override fun show(view: View): Popup {
        val itemInfo = view.tag as ItemInfo
        val activityContext: ActivityContext = ActivityContext.lookupContext<T>(view.context)
        val container =
            PopupContainer.create<T>(
                context = view.context,
                originalView = view,
                itemInfo = itemInfo,
            )
        dragController.addDragListener(container)
        addSystemShortcuts(container, itemInfo, itemView = view, activityContext)
        container.show()

        val launcher = Launcher.getLauncher(view.context)
        val cellLayout = launcher.workspace.getParentCellLayoutForView(view) ?: return container

        val resizeStrategy = DefaultPopupResizeStrategy()
        if (resizeStrategy.shouldShowResizeFrame(itemInfo, view, cellLayout)) {
            when (view) {
                is FolderIcon -> AppWidgetResizeFrame.showForFolder(view, cellLayout)

                is LauncherAppWidgetHostView -> AppWidgetResizeFrame.showForWidget(view, cellLayout)
            }
        }
        return container
    }

    private fun addSystemShortcuts(
        popup: PopupContainer<T>,
        itemInfo: ItemInfo,
        itemView: View,
        activityContext: ActivityContext,
    ) {
        popup.systemShortcutContainer =
            popup.inflateAndAdd(R.layout.system_shortcut_rows_container, popup)
        if (
            itemView is FolderIcon &&
                itemInfo is FolderInfo &&
                itemInfo.spanX == 2 &&
                itemInfo.spanY == 2
        ) {
            addBigFolderStyleSelector(popup, itemInfo, itemView)
        }
        val popupData = popupDataRepository.getPopupDataByItemInfo(itemInfo)?.toList()
        popupData?.forEach { systemShortcut ->
            val view: DeepShortcutView =
                popup.inflateAndAdd(R.layout.system_shortcut, popup.systemShortcutContainer)

            view.iconView.setBackgroundResource(systemShortcut.iconResId)
            view.bubbleText.setText(systemShortcut.labelResId)

            view.tag = systemShortcut
            view.setOnClickListener {
                systemShortcut.popupAction.invoke(activityContext, itemInfo, itemView)
            }
        }
    }

    private fun addBigFolderStyleSelector(
        popup: PopupContainer<T>,
        folderInfo: FolderInfo,
        folderIcon: FolderIcon,
    ) {
        val selectorRow: View =
            popup.inflateAndAdd(R.layout.folder_style_selector_row, popup.systemShortcutContainer)
        val btn3x3 = selectorRow.findViewById<View>(R.id.folder_style_3x3)
        val btn2x2 = selectorRow.findViewById<View>(R.id.folder_style_2x2)
        val btnFeatured = selectorRow.findViewById<View>(R.id.folder_style_featured)
        val icon3x3 = selectorRow.findViewById<View>(R.id.folder_style_3x3_icon)
        val icon2x2 = selectorRow.findViewById<View>(R.id.folder_style_2x2_icon)
        val iconFeatured = selectorRow.findViewById<View>(R.id.folder_style_featured_icon)

        fun updateSelectionUi(activeStyle: Int) {
            val is3x3 = activeStyle == FolderInfo.FOLDER_STYLE_3X3
            val is2x2 = activeStyle == FolderInfo.FOLDER_STYLE_2X2
            val isFeatured = activeStyle == FolderInfo.FOLDER_STYLE_FEATURED

            btn3x3.setBackgroundResource(
                if (is3x3) R.drawable.bg_folder_style_option_selected else 0
            )
            btn2x2.setBackgroundResource(
                if (is2x2) R.drawable.bg_folder_style_option_selected else 0
            )
            btnFeatured.setBackgroundResource(
                if (isFeatured) R.drawable.bg_folder_style_option_selected else 0
            )

            icon3x3.alpha = if (is3x3) 1.0f else 0.55f
            icon2x2.alpha = if (is2x2) 1.0f else 0.55f
            iconFeatured.alpha = if (isFeatured) 1.0f else 0.55f
        }

        updateSelectionUi(folderInfo.bigFolderStyle)

        btn3x3.setOnClickListener {
            folderIcon.setBigFolderStyle(FolderInfo.FOLDER_STYLE_3X3)
            updateSelectionUi(FolderInfo.FOLDER_STYLE_3X3)
        }
        btn2x2.setOnClickListener {
            folderIcon.setBigFolderStyle(FolderInfo.FOLDER_STYLE_2X2)
            updateSelectionUi(FolderInfo.FOLDER_STYLE_2X2)
        }
        btnFeatured.setOnClickListener {
            folderIcon.setBigFolderStyle(FolderInfo.FOLDER_STYLE_FEATURED)
            updateSelectionUi(FolderInfo.FOLDER_STYLE_FEATURED)
        }
    }

    override fun dismiss() {
        TODO("Not yet implemented")
    }
}
