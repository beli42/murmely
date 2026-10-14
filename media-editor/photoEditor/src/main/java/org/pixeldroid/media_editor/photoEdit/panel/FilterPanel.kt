package org.pixeldroid.media_editor.photoEdit.panel

import android.graphics.Bitmap
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

object FilterTags {
    fun item(index: Int) = "filter_$index"
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
 * The filters with a thumbnail each: "none" first, then the built-in and custom filters, and a
 * button to create a custom filter. A row in portrait, two columns in landscape.
 * Long press: show (built-in) or edit (custom) the filter's shader code.
 *
 * Filters can be stacked: "Apply" keeps the filter being tried, then the next one is tried on
 * top of it. The applied filters are listed above.
 *
 * @param thumbnails thumbnail for "none" and every filter in [filters], in that order (may be shorter while loading)
 * @param applied filters applied so far, in order
 */
@Composable
fun FilterPanel(
    filters: List<ImagineLayer>,
    thumbnails: List<Bitmap?>,
    selected: ImagineLayer?,
    applied: List<ImagineLayer>,
    vertical: Boolean,
    onSelect: (ImagineLayer?) -> Unit,
    onApply: () -> Unit,
    onLongPress: (ImagineLayer?) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        AppliedFiltersRow(applied, canApply = selected != null, onApply = onApply)
        FilterList(filters, thumbnails, selected, vertical, onSelect, onLongPress, onCreate)
    }
}

/** "Applied: Elsa, Sepia" and the button to apply the filter being tried */
@Composable
private fun AppliedFiltersRow(applied: List<ImagineLayer>, canApply: Boolean, onApply: () -> Unit) {
    val names = applied.map { filterName(it) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp),
    ) {
        Text(
            if (names.isEmpty()) "" else stringResource(R.string.applied_filters, names.joinToString(", ")),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .testTag(FilterTags.APPLIED),
        )
        FilledTonalButton(onClick = onApply, enabled = canApply, modifier = Modifier.testTag(FilterTags.APPLY)) {
            Text(stringResource(R.string.apply_filter))
        }
    }
}

@Composable
private fun FilterList(
    filters: List<ImagineLayer>,
    thumbnails: List<Bitmap?>,
    selected: ImagineLayer?,
    vertical: Boolean,
    onSelect: (ImagineLayer?) -> Unit,
    onLongPress: (ImagineLayer?) -> Unit,
    onCreate: () -> Unit,
) {
    val entries: List<ImagineLayer?> = listOf(null) + filters
    val item: @Composable (Int, ImagineLayer?) -> Unit = { index, filter ->
        FilterItem(
            name = filterName(filter),
            thumbnail = thumbnails.getOrNull(index),
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
            item { CreateItem(onCreate) }
        }
    } else {
        LazyRow(
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(entries) { index, filter -> item(index, filter) }
            item { CreateItem(onCreate) }
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
