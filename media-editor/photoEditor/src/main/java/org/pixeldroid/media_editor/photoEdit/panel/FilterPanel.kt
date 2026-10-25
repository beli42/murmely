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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
    ART(R.string.filter_category_art),
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
@OptIn(ExperimentalLayoutApi::class)
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
        // The groups, and apply at the end of the same line: a round green check mark, small
        // enough to fit also with large system text (the group names get smaller if needed)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            ) {
                FilterCategory.entries.forEach { group ->
                    CategoryChip(group, selected = group == category) { category = group }
                }
            }
            val applyLabel = stringResource(R.string.apply_filter)
            FilledIconButton(
                onClick = onApply,
                enabled = selected != null,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = APPLY_GREEN, contentColor = Color.White),
                modifier = Modifier.size(44.dp).semantics { contentDescription = applyLabel }.testTag(FilterTags.APPLY),
            ) {
                Icon(painterResource(R.drawable.ic_check_24dp), contentDescription = null)
            }
        }
        // The list first, then the settings below it: showing them doesn't move the thumbnails
        // As high as the tiles (which grow with the free height): the strength follows right below
        Box(Modifier.weight(1f, fill = false)) {
            FilterList(
                groups[category].orEmpty(), thumbnailOf, selected, vertical, onSelect, onLongPress,
                onCreate = onCreate.takeIf { category == FilterCategory.CUSTOM },
            )
        }
        // Always the same room for the strength, used or not: the thumbnails above keep their
        // size whichever filter is chosen. Only filters with a strength show it; centre and
        // mirror line are set on the photo (and reset with the undo controls' reset button)
        // The strength over the whole width; the room is kept also without one
        Box(Modifier.fillMaxWidth().heightIn(min = STRENGTH_ROW_HEIGHT).padding(end = 8.dp)) {
            if (selected != null && selected.strengthMode != StrengthMode.NONE) FilterSettingsRow(selected, onSettingsChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChip(category: FilterCategory, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        // Smaller with large system text (down to 12 sp), only then cut short
        label = { ShrinkingText(stringResource(category.label)) },
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
            vertical = vertical,
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
            if (onCreate != null) item { CreateItem(onCreate, vertical = true) }
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
    vertical: Boolean = false,
) {
    FilterTile(
        label = name,
        selected = selected,
        vertical = vertical,
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (thumbnail != null) {
            Image(thumbnail.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * A tile with [label] on top and a square below, as large as there is room: the full height of
 * a row (or width of a grid cell), between [TILE_MIN] and [TILE_MAX]. The name is never cut off
 * by too little height; with large text the square gets smaller instead.
 */
@Composable
internal fun FilterTile(
    label: String,
    selected: Boolean,
    vertical: Boolean,
    modifier: Modifier = Modifier,
    labelLines: Int = 1,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val labelStyle = MaterialTheme.typography.labelMedium
    val labelHeight = with(LocalDensity.current) { labelStyle.lineHeight.toDp() } * labelLines + 4.dp
    BoxWithConstraints(modifier) {
        val room = if (vertical) maxWidth - 8.dp else maxHeight - labelHeight - 8.dp
        val side = if (room.value.isFinite()) room.coerceIn(TILE_MIN, TILE_MAX) else TILE_DEFAULT
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(side + 8.dp)
                .clip(shape)
                .padding(4.dp),
        ) {
            Text(
                label,
                style = labelStyle,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = labelLines,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Surface(
                shape = shape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(side)
                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier),
            ) {
                Box(contentAlignment = Alignment.Center, content = content)
            }
        }
    }
}

/** Sizes of the square in a tile */
private val TILE_MIN = 40.dp
private val TILE_MAX = 160.dp
private val TILE_DEFAULT = 64.dp

/** Button to create a custom filter (shader code) */
@Composable
private fun CreateItem(onCreate: () -> Unit, vertical: Boolean = false) =
    CreateItem(stringResource(R.string.create_custom_filter), FilterTags.CREATE, vertical, onCreate)

/** A "+" tile with [label] above it */
@Composable
internal fun CreateItem(label: String, tag: String, vertical: Boolean = false, onCreate: () -> Unit) {
    FilterTile(
        label = label,
        selected = false,
        vertical = vertical,
        modifier = Modifier.clickable(onClick = onCreate).testTag(tag),
    ) {
        Icon(painterResource(R.drawable.add), contentDescription = null)
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
        ShrinkingText(
            label,
            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            minSize = 9.sp,
            modifier = Modifier
                .widthIn(max = 96.dp)
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
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .widthIn(min = 52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { onChange(settings.copy(strength = 1f)) }
                .semantics { contentDescription = reset }
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .testTag(FilterTags.STRENGTH_VALUE),
        )
    }
}

/** Least height of the strength row, kept also when a filter has no strength */

/** Apply: green, clearly not one of the groups */
private val APPLY_GREEN = Color(0xFF2E7D32)
private val STRENGTH_ROW_HEIGHT = 48.dp


/**
 * One line of text that gets smaller when it doesn't fit (down to [minSize]) and only then is cut
 * short with "…". In the style and colour around it unless [style] is given.
 */
@Composable
internal fun ShrinkingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current.copy(color = LocalContentColor.current),
    minSize: TextUnit = 12.sp,
) {
    val size = style.fontSize.takeIf { it.isSpecified } ?: 14.sp
    BasicText(
        text,
        style = style,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = smaller(minSize, size), maxFontSize = size, stepSize = 0.5.sp),
        modifier = modifier,
    )
}

private fun smaller(a: TextUnit, b: TextUnit): TextUnit = if (a.value <= b.value) a else b
