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
package com.android.launcher3.settings.compose

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.axion.compose.preferences.PreferenceGroup
import com.android.launcher3.AxWorkspaceDisplayPrefs
import com.android.launcher3.ConstantItem
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherPrefsExt
import com.android.launcher3.R
import com.android.launcher3.qsb.OseWidgetManager

internal object HomeGridSettingsKeys {
    const val OPTIONS = "settings_screen_home_grid_options"
    const val COLUMNS = "pref_workspace_grid_columns"
    const val ROWS = "pref_workspace_grid_rows"
    const val HOTSEAT_ICONS = "pref_workspace_hotseat_icons"
    const val TABLET_PORTRAIT_COLUMNS = "pref_workspace_tablet_portrait_grid_columns"
    const val TABLET_PORTRAIT_ROWS = "pref_workspace_tablet_portrait_grid_rows"
    const val TABLET_PORTRAIT_HOTSEAT_ICONS =
        "pref_workspace_tablet_portrait_hotseat_icons"
    const val TABLET_LANDSCAPE_COLUMNS = "pref_workspace_tablet_landscape_grid_columns"
    const val TABLET_LANDSCAPE_ROWS = "pref_workspace_tablet_landscape_grid_rows"
    const val TABLET_LANDSCAPE_HOTSEAT_ICONS =
        "pref_workspace_tablet_landscape_hotseat_icons"

    fun contains(key: String?): Boolean = when (key) {
        OPTIONS,
        COLUMNS,
        ROWS,
        HOTSEAT_ICONS,
        TABLET_PORTRAIT_COLUMNS,
        TABLET_PORTRAIT_ROWS,
        TABLET_PORTRAIT_HOTSEAT_ICONS,
        TABLET_LANDSCAPE_COLUMNS,
        TABLET_LANDSCAPE_ROWS,
        TABLET_LANDSCAPE_HOTSEAT_ICONS -> true
        else -> false
    }
}

@Composable
internal fun HomeGridScreen() {
    val preview = rememberHomePreviewState()
    HomeSettingsPreview(
        rows = preview.rows,
        columns = preview.columns,
        iconPercent = preview.iconPercent,
        labelPercent = preview.labelPercent,
        showLabels = preview.showLabels,
        hotseatIcons = preview.hotseatIcons,
        showSearchBar = preview.showSearchBar,
    )
    if (preview.isTablet) {
        HomeGridPreferenceGroup(
            title = stringResource(R.string.home_grid_tablet_portrait_category),
            columnsItem = LauncherPrefsExt.WORKSPACE_TABLET_PORTRAIT_GRID_COLUMNS,
            rowsItem = LauncherPrefsExt.WORKSPACE_TABLET_PORTRAIT_GRID_ROWS,
            hotseatIconsItem = LauncherPrefsExt.WORKSPACE_TABLET_PORTRAIT_HOTSEAT_ICONS,
            preview = preview,
            max = AxWorkspaceDisplayPrefs.MAX_TABLET_GRID_SIZE,
        )
        HomeGridPreferenceGroup(
            title = stringResource(R.string.home_grid_tablet_landscape_category),
            columnsItem = LauncherPrefsExt.WORKSPACE_TABLET_LANDSCAPE_GRID_COLUMNS,
            rowsItem = LauncherPrefsExt.WORKSPACE_TABLET_LANDSCAPE_GRID_ROWS,
            hotseatIconsItem = LauncherPrefsExt.WORKSPACE_TABLET_LANDSCAPE_HOTSEAT_ICONS,
            preview = preview,
            max = AxWorkspaceDisplayPrefs.MAX_TABLET_GRID_SIZE,
        )
    } else {
        HomeGridPresetGroup(preview)
    }
}

private data class GridPreset(
    val columns: Int,
    val rows: Int,
    val hotseatIcons: Int,
)

private val PHONE_GRID_PRESETS = listOf(
    GridPreset(columns = 4, rows = 6, hotseatIcons = 4),
    GridPreset(columns = 5, rows = 6, hotseatIcons = 5),
)

@Composable
private fun HomeGridPresetGroup(preview: HomePreviewState) {
    val columnsPreference = rememberLauncherPreference(LauncherPrefsExt.WORKSPACE_GRID_COLUMNS)
    val rowsPreference = rememberLauncherPreference(LauncherPrefsExt.WORKSPACE_GRID_ROWS)
    val hotseatPreference = rememberLauncherPreference(LauncherPrefsExt.WORKSPACE_HOTSEAT_ICONS)

    Text(
        text = stringResource(R.string.home_grid_category),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PHONE_GRID_PRESETS.forEach { preset ->
            GridPresetCard(
                preset = preset,
                selected = preview.columns == preset.columns && preview.rows == preset.rows,
                onClick = {
                    columnsPreference.onChange(preset.columns)
                    rowsPreference.onChange(preset.rows)
                    hotseatPreference.onChange(preset.hotseatIcons)
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GridPresetCard(
    preset: GridPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceBright
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (selected) {
            null
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        modifier = modifier.height(156.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GridPresetPreview(
                columns = preset.columns,
                rows = preset.rows,
                selected = selected,
            )
            Text(
                text = stringResource(R.string.home_grid_preset_label, preset.columns, preset.rows),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = stringResource(
                    R.string.home_grid_preset_summary,
                    preset.columns,
                    preset.rows,
                ),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GridPresetPreview(
    columns: Int,
    rows: Int,
    selected: Boolean,
) {
    val cellColor = MaterialTheme.colorScheme.onSurface.copy(
        alpha = if (selected) 0.85f else 0.5f,
    )
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(columns) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(cellColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeGridPreferenceGroup(
    title: String,
    columnsItem: ConstantItem<Int>,
    rowsItem: ConstantItem<Int>,
    hotseatIconsItem: ConstantItem<Int>,
    preview: HomePreviewState,
    max: Int,
) {
    PreferenceGroup(title = title) {
        item {
            IntSliderPreference(
                item = columnsItem,
                titleRes = R.string.home_grid_columns_title,
                min = AxWorkspaceDisplayPrefs.MIN_GRID_SIZE,
                max = max,
                defaultValue = preview.defaultColumns,
                valueOverride = {
                    preview.gridValue(it, preview.commonColumns, preview.defaultColumns, max)
                },
                resetValue = 0,
                valueLabel = { stringResource(R.string.home_settings_count_value, it) },
            )
        }
        item {
            IntSliderPreference(
                item = rowsItem,
                titleRes = R.string.home_grid_rows_title,
                min = AxWorkspaceDisplayPrefs.MIN_GRID_SIZE,
                max = max,
                defaultValue = preview.defaultRows,
                valueOverride = {
                    preview.gridValue(it, preview.commonRows, preview.defaultRows, max)
                },
                resetValue = 0,
                valueLabel = { stringResource(R.string.home_settings_count_value, it) },
            )
        }
        item {
            IntSliderPreference(
                item = hotseatIconsItem,
                titleRes = R.string.home_grid_hotseat_icons_title,
                min = AxWorkspaceDisplayPrefs.MIN_GRID_SIZE,
                max = max,
                defaultValue = preview.defaultHotseatIcons,
                valueOverride = {
                    preview.gridValue(
                        it,
                        preview.commonHotseatIcons,
                        preview.defaultHotseatIcons,
                        max,
                    )
                },
                resetValue = 0,
                valueLabel = { stringResource(R.string.home_settings_count_value, it) },
            )
        }
    }
}

@Composable
internal fun rememberHomePreviewState(): HomePreviewState {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val idp = remember(context) { LauncherAppState.getIDP(context) }
    val defaultColumns = remember(idp) { idp.numColumns }
    val defaultRows = remember(idp) { idp.numRows }
    val defaultHotseatIcons = remember(idp) { idp.numShownHotseatIcons }
    val isTablet = idp.deviceType == InvariantDeviceProfile.TYPE_TABLET
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val maxGridSize = if (isTablet) {
        AxWorkspaceDisplayPrefs.MAX_TABLET_GRID_SIZE
    } else {
        AxWorkspaceDisplayPrefs.MAX_GRID_SIZE
    }
    val columns = rememberLauncherPreference(LauncherPrefsExt.WORKSPACE_GRID_COLUMNS)
    val rows = rememberLauncherPreference(LauncherPrefsExt.WORKSPACE_GRID_ROWS)
    val hotseatIcons = rememberLauncherPreference(LauncherPrefsExt.WORKSPACE_HOTSEAT_ICONS)
    val tabletPortraitColumns = rememberLauncherPreference(
        LauncherPrefsExt.WORKSPACE_TABLET_PORTRAIT_GRID_COLUMNS,
    )
    val tabletPortraitRows = rememberLauncherPreference(
        LauncherPrefsExt.WORKSPACE_TABLET_PORTRAIT_GRID_ROWS,
    )
    val tabletPortraitHotseatIcons = rememberLauncherPreference(
        LauncherPrefsExt.WORKSPACE_TABLET_PORTRAIT_HOTSEAT_ICONS,
    )
    val tabletLandscapeColumns = rememberLauncherPreference(
        LauncherPrefsExt.WORKSPACE_TABLET_LANDSCAPE_GRID_COLUMNS,
    )
    val tabletLandscapeRows = rememberLauncherPreference(
        LauncherPrefsExt.WORKSPACE_TABLET_LANDSCAPE_GRID_ROWS,
    )
    val tabletLandscapeHotseatIcons = rememberLauncherPreference(
        LauncherPrefsExt.WORKSPACE_TABLET_LANDSCAPE_HOTSEAT_ICONS,
    )
    val iconScale = rememberLauncherPreference(LauncherPrefsExt.WORKSPACE_ICON_SCALE)
    val labelScale = rememberLauncherPreference(LauncherPrefsExt.WORKSPACE_LABEL_SCALE)
    val showLabels = rememberLauncherPreference(LauncherPrefsExt.SHOW_DESKTOP_LABELS)
    val showSearchBar = rememberLauncherPreference(LauncherPrefsExt.HOTSEAT_SEARCH_BAR)
    val searchProvider = rememberLauncherPreference(LauncherPrefsExt.HOTSEAT_SEARCH_PROVIDER)
    val previewColumns = if (!isTablet) {
        columns.value
    } else if (isLandscape) {
        tabletLandscapeColumns.value
    } else {
        tabletPortraitColumns.value
    }
    val previewRows = if (!isTablet) {
        rows.value
    } else if (isLandscape) {
        tabletLandscapeRows.value
    } else {
        tabletPortraitRows.value
    }
    val previewHotseatIcons = if (!isTablet) {
        hotseatIcons.value
    } else if (isLandscape) {
        tabletLandscapeHotseatIcons.value
    } else {
        tabletPortraitHotseatIcons.value
    }
    return HomePreviewState(
        isTablet = isTablet,
        rows = resolveGridValue(previewRows, rows.value, defaultRows, maxGridSize),
        columns = resolveGridValue(previewColumns, columns.value, defaultColumns, maxGridSize),
        defaultRows = defaultRows,
        defaultColumns = defaultColumns,
        commonRows = rows.value,
        commonColumns = columns.value,
        hotseatIcons = resolveGridValue(
            previewHotseatIcons,
            hotseatIcons.value,
            defaultHotseatIcons,
            maxGridSize,
        ),
        defaultHotseatIcons = defaultHotseatIcons,
        commonHotseatIcons = hotseatIcons.value,
        iconPercent = iconScale.value,
        labelPercent = labelScale.value,
        showLabels = showLabels.value,
        showSearchBar = showSearchBar.value &&
            searchProvider.value != OseWidgetManager.SEARCH_PROVIDER_NONE,
    )
}

private fun resolveGridValue(value: Int, fallback: Int, defaultValue: Int, max: Int): Int {
    return (value.takeIf { it > 0 } ?: fallback.takeIf { it > 0 } ?: defaultValue).coerceIn(
        AxWorkspaceDisplayPrefs.MIN_GRID_SIZE,
        max,
    )
}

internal data class HomePreviewState(
    val isTablet: Boolean,
    val rows: Int,
    val columns: Int,
    val defaultRows: Int,
    val defaultColumns: Int,
    val commonRows: Int,
    val commonColumns: Int,
    val hotseatIcons: Int,
    val defaultHotseatIcons: Int,
    val commonHotseatIcons: Int,
    val iconPercent: Int,
    val labelPercent: Int,
    val showLabels: Boolean,
    val showSearchBar: Boolean,
) {
    fun gridValue(value: Int, fallback: Int, defaultValue: Int, max: Int): Int =
        resolveGridValue(value, fallback, defaultValue, max)
}
