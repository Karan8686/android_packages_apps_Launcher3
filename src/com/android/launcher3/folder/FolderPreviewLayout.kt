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

package com.android.launcher3.folder

import android.graphics.RectF
import com.android.launcher3.model.data.ItemInfo
import kotlin.math.roundToInt

object FolderPreviewLayout {

    data class ContentSelection(val directItems: List<ItemInfo>, val overviewItems: List<ItemInfo>)

    enum class ItemRole {
        DIRECT,
        OVERVIEW,
    }

    data class ItemPlacement(val item: ItemInfo, val role: ItemRole, val bounds: RectF)

    data class Grid @JvmOverloads constructor(
        val columns: Int,
        val rows: Int,
        val startX: Float,
        val startY: Float,
        val itemSize: Float,
        val columnGap: Float,
        val rowGap: Float,
        val featuredFirstItem: Boolean = false,
    ) {
        val capacity: Int
            get() = if (featuredFirstItem && columns == 3 && rows == 3) 6 else columns * rows
    }

    data class GridUsage(val hasEmptyColumns: Boolean, val hasEmptyRows: Boolean)

    data class Snapshot(
        val backgroundBounds: RectF,
        val overviewBounds: RectF?,
        val items: List<ItemPlacement>,
        val placeholderBounds: List<RectF> = emptyList(),
    )

    private fun squareBounds(left: Float, top: Float, size: Float) =
        RectF(left, top, left + size, top + size)

    @JvmStatic
    fun selectItems(items: List<ItemInfo>, capacity: Int): ContentSelection {
        require(capacity > 0)

        if (capacity == 1) {
            return ContentSelection(
                directItems = emptyList(),
                overviewItems = items.take(ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW),
            )
        }

        // For multi-span folders (capacity >= 3, e.g. 2x1/1x2 with 3 slots, or 2x2 with 4 slots):
        // There are ALWAYS (capacity - 1) direct big icon slots.
        // The last slot is ALWAYS the overview tile (up to 4 preview icons).
        val directItemCount = capacity - 1

        val direct = items.take(directItemCount)
        val overview =
            items
                .drop(directItemCount)
                .take(ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW)

        return ContentSelection(
            directItems = direct,
            overviewItems = overview,
        )
    }

    @JvmStatic
    fun calculateDirectPlacements(
        items: List<ItemInfo>,
        grid: Grid,
        isRtl: Boolean,
    ): List<ItemPlacement> {
        require(items.size <= grid.capacity)

        val placements =
            items.mapIndexed { index, item ->
                ItemPlacement(
                    item = item,
                    role = ItemRole.DIRECT,
                    bounds = calculateGridItemBounds(index, grid, isRtl),
                )
            }
        return placements
    }

    @JvmStatic
    fun calculateOverviewPlacements(
        items: List<ItemInfo>,
        overviewBounds: RectF,
        tileSize: Float,
        intrinsicIconSize: Float,
        isRtl: Boolean,
        folderColumnCount: Int,
    ): List<ItemPlacement> {
        if (items.isEmpty()) return emptyList()
        require(tileSize > 0f)
        require(intrinsicIconSize > 0f)
        require(folderColumnCount > 0)

        val miniCols = 2
        val miniGap = tileSize * 0.08f
        val miniPadding = tileSize * 0.04f
        val miniItemSize = (tileSize - 2 * miniPadding - miniGap) / 2f

        val placements =
            items.take(ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW).mapIndexed { index, item ->
                val r = index / miniCols
                val logicalC = index % miniCols
                val c = if (isRtl) (miniCols - 1 - logicalC) else logicalC

                val left = overviewBounds.left + miniPadding + c * (miniItemSize + miniGap)
                val top = overviewBounds.top + miniPadding + r * (miniItemSize + miniGap)
                val iconBounds = squareBounds(left, top, miniItemSize)

                ItemPlacement(item = item, role = ItemRole.OVERVIEW, bounds = iconBounds)
            }

        return placements
    }

    @JvmStatic
    fun calculateGrid(availableBounds: RectF, itemSize: Float, minGap: Float): Grid {
        require(itemSize > 0f)
        require(minGap >= 0f)

        val availableWidth = availableBounds.width()
        val availableHeight = availableBounds.height()
        require(availableWidth >= itemSize && availableHeight >= itemSize)

        val columns = ((availableWidth + minGap) / (itemSize + minGap)).toInt()
        val rows = ((availableHeight + minGap) / (itemSize + minGap)).toInt()

        val columnGap =
            if (columns > 1) {
                (availableWidth - columns * itemSize) / (columns - 1)
            } else {
                0f
            }

        val rowGap =
            if (rows > 1) {
                (availableHeight - rows * itemSize) / (rows - 1)
            } else {
                0f
            }

        val startX =
            if (columns > 1) availableBounds.left else availableBounds.centerX() - itemSize / 2f

        val startY =
            if (rows > 1) availableBounds.top else availableBounds.centerY() - itemSize / 2f

        return Grid(
            columns = columns,
            rows = rows,
            startX = startX,
            startY = startY,
            itemSize = itemSize,
            columnGap = columnGap,
            rowGap = rowGap,
        )
    }

    @JvmStatic
    fun calculateGridItemBounds(index: Int, grid: Grid, isRtl: Boolean): RectF {
        require(index in 0 until grid.capacity)

        val stepX = grid.itemSize + grid.columnGap
        val stepY = grid.itemSize + grid.rowGap

        if (grid.featuredFirstItem && grid.columns == 3 && grid.rows == 3) {
            if (index == 0) {
                val spanWidth = 2f * grid.itemSize + grid.columnGap
                val spanHeight = 2f * grid.itemSize + grid.rowGap
                val featuredSize = minOf(spanWidth, spanHeight)
                val blockLeft = if (isRtl) grid.startX + stepX else grid.startX
                val blockTop = grid.startY
                val left = blockLeft + (spanWidth - featuredSize) / 2f
                val top = blockTop + (spanHeight - featuredSize) / 2f
                return squareBounds(left, top, featuredSize)
            }
            val (row, logicalColumn) = when (index) {
                1 -> 0 to 2
                2 -> 1 to 2
                3 -> 2 to 0
                4 -> 2 to 1
                else -> 2 to 2
            }
            val column = if (isRtl) grid.columns - logicalColumn - 1 else logicalColumn
            return squareBounds(
                grid.startX + column * stepX,
                grid.startY + row * stepY,
                grid.itemSize,
            )
        }

        val row = index / grid.columns
        val logicalColumn = index % grid.columns
        val column = if (isRtl) grid.columns - logicalColumn - 1 else logicalColumn

        return squareBounds(grid.startX + column * stepX, grid.startY + row * stepY, grid.itemSize)
    }

    @JvmStatic
    fun isTightlyWrapped(itemCount: Int, grid: Grid): Boolean {
        if ((grid.columns in 2..3 && grid.rows == 1)
            || (grid.columns == 1 && grid.rows in 2..3)
            || (grid.columns == 2 && grid.rows == 2)
            || (grid.columns == 3 && grid.rows == 3)
        ) {
            return itemCount >= 2
        }
        val usage = calculateGridUsage(itemCount, grid)
        return !usage.hasEmptyColumns && !usage.hasEmptyRows
    }

    @JvmStatic
    fun calculateGridUsage(itemCount: Int, grid: Grid): GridUsage {
        if ((grid.columns in 2..3 && grid.rows == 1)
            || (grid.columns == 1 && grid.rows in 2..3)
            || (grid.columns == 2 && grid.rows == 2)
            || (grid.columns == 3 && grid.rows == 3)
        ) {
            if (itemCount >= 2) {
                return GridUsage(
                    hasEmptyColumns = false,
                    hasEmptyRows = false,
                )
            }
        }
        val occupiedSlots = minOf(itemCount, grid.capacity)
        val usedColumns = minOf(occupiedSlots, grid.columns)
        val usedRows = (occupiedSlots + grid.columns - 1) / grid.columns

        return GridUsage(
            hasEmptyColumns = usedColumns < grid.columns,
            hasEmptyRows = usedRows < grid.rows,
        )
    }

    @JvmStatic
    fun calculateSnapshot(
        items: List<ItemInfo>,
        backgroundBounds: RectF,
        grid: Grid,
        intrinsicIconSize: Float,
        isRtl: Boolean,
        folderColumnCount: Int,
    ): Snapshot {
        val snapshotBounds = RectF(backgroundBounds)
        val selection = selectItems(items, grid.capacity)

        val directPlacements = calculateDirectPlacements(selection.directItems, grid, isRtl)

        if (grid.capacity <= 1) {
            val overviewBounds = RectF(
                backgroundBounds.centerX() - grid.itemSize / 2f,
                backgroundBounds.centerY() - grid.itemSize / 2f,
                backgroundBounds.centerX() + grid.itemSize / 2f,
                backgroundBounds.centerY() + grid.itemSize / 2f,
            )
            val overviewPlacements =
                calculateOverviewPlacements(
                    selection.overviewItems,
                    overviewBounds,
                    grid.itemSize,
                    intrinsicIconSize,
                    isRtl,
                    folderColumnCount,
                )
            return Snapshot(snapshotBounds, overviewBounds, overviewPlacements, emptyList())
        }

        val overviewIndex = grid.capacity - 1
        val overviewBounds = calculateGridItemBounds(overviewIndex, grid, isRtl)

        val overviewPlacements =
            calculateOverviewPlacements(
                selection.overviewItems,
                overviewBounds,
                grid.itemSize,
                intrinsicIconSize,
                isRtl,
                folderColumnCount,
            )

        val miniCols = 2
        val miniGap = grid.itemSize * 0.08f
        val miniPadding = grid.itemSize * 0.04f
        val miniItemSize = (grid.itemSize - 2 * miniPadding - miniGap) / 2f

        val placeholderBounds = mutableListOf<RectF>()
        for (dotIndex in selection.overviewItems.size until ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW) {
            val r = dotIndex / miniCols
            val logicalC = dotIndex % miniCols
            val c = if (isRtl) (miniCols - 1 - logicalC) else logicalC

            val left = overviewBounds.left + miniPadding + c * (miniItemSize + miniGap)
            val top = overviewBounds.top + miniPadding + r * (miniItemSize + miniGap)
            placeholderBounds.add(squareBounds(left, top, miniItemSize))
        }

        return Snapshot(
            snapshotBounds,
            overviewBounds,
            directPlacements + overviewPlacements,
            placeholderBounds
        )
    }
}
