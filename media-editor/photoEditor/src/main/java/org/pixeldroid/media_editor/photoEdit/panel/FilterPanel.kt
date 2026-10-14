package org.pixeldroid.media_editor.photoEdit.panel

import android.graphics.Bitmap
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.annotation.StringRes
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.customFilters.CustomLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/** The groups of filters, each shown as one row */
enum class FilterCategory(@StringRes val label: Int) {
    COLOR(R.string.filter_category_color),
    GEOMETRY(R.string.filter_category_geometry),
    CUSTOM(R.string.filter_category_custom),
}

object FilterTags {
    /** [index] within the shown group; 0 is "none" */
    fun item(index: Int) = "filter_$index"
    fun category(category: FilterCategory) = "filter_category_${category.name}"
    const val CREATE = "filter_create"
    const val APPLY = "filter_apply"
    const val APPLIED = "filter_applied"
}

/**
 * True if [a] and [b] are the same filter. Built-in filters are compared by type (a new instance
 * after rotating the screen is still the same filter), custom filters by their content.
 */
fun sameFilter(a: ImagineLayer?, b: ImagineLayer?): Boolean = when {
    a == null || b == null -> a == null && b == null
    a is CustomLayer || b is CustomLayer -> a == b
    else -> a::class == b::class
}

/**
 * The filters in three groups (colour, geometry, custom): buttons to switch the group, and the
 * filters of the group with a thumbnail each, "none" first. A row in portrait, two columns in
 * landscape. The custom group ends with a button to create a custom filter.
 * Long press: show (built-in) or edit (custom) the filter's shader code.
 *
 * Filters can be stacked: "Apply" keeps the filter being tried, then the next one is tried on
 * top of it. The applied filters are listed above the filters.
 *
 * @param thumbnailOf thumbnail of a filter (null: the unfiltered image), null while loading
 * @param applied filters applied so far, in order
 */
@Composable
fun FilterPanel(
    groups: Map<FilterCategory, List<ImagineLayer>>,
    thumbnailOf: (ImagineLayer?) -> Bitmap?,
    selected: ImagineLayer?,
    applied: List<ImagineLayer>,
    vertical: Boolean,
    onSelect: (ImagineLayer?) -> Unit,
    onApply: () -> Unit,
    onLongPress: (ImagineLayer?) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Opens the group of the filter being tried
    var category by rememberSaveable {
        mutableStateOf(groups.entries.firstOrNull { (_, filters) -> filters.any { sameFilter(it, selected) } }?.key ?: FilterCategory.COLOR)
    }
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
            ) {
                FilterCategory.entries.forEach { group ->
                    CategoryChip(group, selected = group == category) { category = group }
                }
            }
            FilledTonalButton(onClick = onApply, enabled = selected != null, modifier = Modifier.testTag(FilterTags.APPLY)) {
                Text(stringResource(R.string.apply_filter))
            }
        }
        if (applied.isNotEmpty()) AppliedFilters(applied)
        FilterList(
            groups[category].orEmpty(), thumbnailOf, selected, vertical, onSelect, onLongPress,
            onCreate = onCreate.takeIf { category == FilterCategory.CUSTOM },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChip(category: FilterCategory, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(category.label)) },
        modifier = Modifier.testTag(FilterTags.category(category)),
    )
}

/** "Applied: Elsa, Sepia" */
@Composable
private fun AppliedFilters(applied: List<ImagineLayer>) {
    val names = applied.map { filterName(it) }
    Text(
        stringResource(R.string.applied_filters, names.joinToString(", ")),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .testTag(FilterTags.APPLIED),
    )
}

@Composable
private fun FilterList(
    filters: List<ImagineLayer>,
    thumbnailOf: (ImagineLayer?) -> Bitmap?,
    selected: ImagineLayer?,
    vertical: Boolean,
    onSelect: (ImagineLayer?) -> Unit,
    onLongPress: (ImagineLayer?) -> Unit,
    /** Shows the create button at the end, if not null */
    onCreate: (() -> Unit)?,
) {
    val entries: List<ImagineLayer?> = listOf(null) + filters
    val item: @Composable (Int, ImagineLayer?) -> Unit = { index, filter ->
        FilterItem(
            name = filterName(filter),
            thumbnail = thumbnailOf(filter),
            selected = sameFilter(filter, selected),
            onClick = { onSelect(filter) },
            onLongClick = { onLongPress(filter) },
            modifier = Modifier.testTag(FilterTags.item(index)),
        )
    }
    if (vertical) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(entries) { index, filter -> item(index, filter) }
            if (onCreate != null) item { CreateItem(onCreate) }
        }
    } else {
        LazyRow(
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(entries) { index, filter -> item(index, filter) }
            if (onCreate != null) item { CreateItem(onCreate) }
        }
    }
}

@Composable
private fun filterName(filter: ImagineLayer?): String = when {
    filter == null -> stringResource(R.string.filterNone)
    filter.name != null -> stringResource(filter.name!!)
    else -> filter.customName.orEmpty()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterItem(
    name: String,
    thumbnail: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(76.dp)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
    ) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .size(64.dp)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
        ) {
            if (thumbnail != null) {
                Image(thumbnail.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop)
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Button to create a custom filter (shader code) */
@Composable
private fun CreateItem(onCreate: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(shape)
            .clickable(onClick = onCreate)
            .padding(4.dp)
            .testTag(FilterTags.CREATE),
    ) {
        Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.add), contentDescription = null)
            }
        }
        Text(
            stringResource(R.string.create_custom_filter),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
