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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.remember
import org.pixeldroid.media_editor.photoEdit.customFilters.CustomLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.base
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.FilterSettings
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.StrengthMode
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.settings
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt

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
    const val STRENGTH = "filter_strength"
    const val STRENGTH_NAME = "filter_strength_name"
    const val STRENGTH_VALUE = "filter_strength_value"
}

/**
 * True if [a] and [b] are the same filter, whatever their settings. Built-in filters are compared
 * by type (a new instance after rotating the screen is still the same filter), custom filters by
 * their content.
 */
fun sameFilter(a: ImagineLayer?, b: ImagineLayer?): Boolean {
    val first = a?.base
    val second = b?.base
    return when {
        first == null || second == null -> first == null && second == null
        first is CustomLayer || second is CustomLayer -> first == second
        else -> first::class == second::class
    }
}

/**
 * The filters in three groups (colour, geometry, custom): buttons to switch the group, and the
 * filters of the group with a thumbnail each, "none" first. A row in portrait, two columns in
 * landscape. The custom group ends with a button to create a custom filter.
 * Long press: show (built-in) or edit (custom) the filter's shader code.
 *
 * Filters can be stacked: "Apply" keeps the filter being tried, then the next one is tried on
 * top of it.
 *
 * The filter being tried can be adjusted: a strength slider, and a hint to tap the photo for
 * its centre (both only if the filter has them).
 *
 * @param thumbnailOf thumbnail of a filter (null: the unfiltered image), null while loading
 * @param selected the filter being tried, with its settings
 */
@Composable
fun FilterPanel(
    groups: Map<FilterCategory, List<ImagineLayer>>,
    thumbnailOf: (ImagineLayer?) -> Bitmap?,
    selected: ImagineLayer?,
    vertical: Boolean,
    onSelect: (ImagineLayer?) -> Unit,
    onApply: () -> Unit,
    onLongPress: (ImagineLayer?) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
    onSettingsChange: (FilterSettings) -> Unit = {},
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
            // The groups have the whole width (apply is below the thumbnails)
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
        }
        // The list first, then the settings below it: showing them doesn't move the thumbnails
        Box(Modifier.weight(1f, fill = false)) {
            FilterList(
                groups[category].orEmpty(), thumbnailOf, selected, vertical, onSelect, onLongPress,
                onCreate = onCreate.takeIf { category == FilterCategory.CUSTOM },
            )
        }
        // Always the same room for the strength, used or not: the thumbnails above keep their
        // size whichever filter is chosen. Only filters with a strength show it; centre and
        // mirror line are set on the photo (and reset with the undo controls' reset button)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = STRENGTH_ROW_HEIGHT).padding(end = 8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (selected != null && selected.strengthMode != StrengthMode.NONE) FilterSettingsRow(selected, onSettingsChange)
            }
            // Apply: where one looks after setting the strength; compact
            FilledTonalButton(
                onClick = onApply,
                enabled = selected != null,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 36.dp).testTag(FilterTags.APPLY),
            ) {
                Text(stringResource(R.string.apply_filter), maxLines = 1)
            }
        }
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
internal fun FilterItem(
    name: String,
    thumbnail: Bitmap?,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    // Name on top, always shown: when there is little height, the thumbnail gets smaller
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(76.dp)
            .clip(shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .weight(1f, fill = false)
                .sizeIn(maxWidth = 64.dp, maxHeight = 64.dp)
                .aspectRatio(1f)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
        ) {
            if (thumbnail != null) {
                Image(thumbnail.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop)
            }
        }
    }
}

/** Button to create a custom filter (shader code) */
@Composable
private fun CreateItem(onCreate: () -> Unit) =
    CreateItem(stringResource(R.string.create_custom_filter), FilterTags.CREATE, onCreate)

/** A "+" tile with [label] above it */
@Composable
internal fun CreateItem(label: String, tag: String, onCreate: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(shape)
            .clickable(onClick = onCreate)
            .padding(4.dp)
            .testTag(tag),
    ) {
        // Like the filters: name on top, the button shrinks when there is little height
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .weight(1f, fill = false)
                .sizeIn(maxWidth = 64.dp, maxHeight = 64.dp)
                .aspectRatio(1f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.add), contentDescription = null)
            }
        }
    }
}

/** Strength slider (tap the value: back to 100 %) and the hint to tap the photo for the centre */
@Composable
private fun FilterSettingsRow(filter: ImagineLayer, onChange: (FilterSettings) -> Unit) {
    val settings = filter.settings
    val label = stringResource(R.string.filter_strength)
    val reset = stringResource(R.string.reset_slider, label)
    // Quiet: small muted text, a muted slider (with the large handle of all resettable sliders)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
    ) {
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClickLabel = reset) { onChange(settings.copy(strength = 1f)) }
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .testTag(FilterTags.STRENGTH_NAME),
        )
        ResettableSlider(
            value = settings.strength,
            onChange = { onChange(settings.copy(strength = it)) },
            range = 0f..filter.strengthMode.max,
            resetValue = 1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.weight(1f).testTag(FilterTags.STRENGTH),
        )
        Text(
            "${(settings.strength * 100).roundToInt()} %",
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { onChange(settings.copy(strength = 1f)) }
                .semantics { contentDescription = reset }
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .testTag(FilterTags.STRENGTH_VALUE),
        )
    }
}

/** Least height of the row with the strength and apply, kept also when a filter has no strength */
private val STRENGTH_ROW_HEIGHT = 48.dp

