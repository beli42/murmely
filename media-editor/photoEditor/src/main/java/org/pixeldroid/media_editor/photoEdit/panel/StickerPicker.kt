package org.pixeldroid.media_editor.photoEdit.panel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pixeldroid.media_editor.photoEdit.R
import org.pixeldroid.media_editor.photoEdit.overlay.StickerCatalog

/** Label of each sticker category */
@get:StringRes
val StickerCatalog.Category.label: Int
    get() = when (this) {
        StickerCatalog.Category.FACES -> R.string.sticker_category_faces
        StickerCatalog.Category.HEARTS -> R.string.sticker_category_hearts
        StickerCatalog.Category.ANIMALS -> R.string.sticker_category_animals
        StickerCatalog.Category.FOOD -> R.string.sticker_category_food
        StickerCatalog.Category.PARTY -> R.string.sticker_category_party
        StickerCatalog.Category.NATURE -> R.string.sticker_category_nature
    }

/**
 * The built-in stickers by category, and a tile for an own image from the gallery. Tapping a
 * sticker adds it in the middle of the photo ([onAdd] gets its uri).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickerPicker(onAdd: (String) -> Unit, onFromGallery: () -> Unit, modifier: Modifier = Modifier) {
    var category by rememberSaveable { mutableStateOf(StickerCatalog.Category.FACES) }
    val context = LocalContext.current
    val thumbnails by produceState<Map<String, ImageBitmap>>(emptyMap(), category) {
        value = withContext(Dispatchers.IO) {
            category.codes.mapNotNull { code -> loadThumbnail(context, code)?.let { code to it.asImageBitmap() } }.toMap()
        }
    }

    Column(modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            StickerCatalog.Category.entries.forEach { c ->
                FilterChip(
                    selected = c == category,
                    onClick = { category = c },
                    label = { Text(stringResource(c.label)) },
                    modifier = Modifier.testTag(PanelTags.stickerCategory(c)),
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            val fromGallery = stringResource(R.string.sticker_from_gallery)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onFromGallery)
                    .semantics { contentDescription = fromGallery }
                    .testTag(PanelTags.STICKER_GALLERY),
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, contentDescription = null) }
            }
            category.codes.forEach { code ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onAdd(StickerCatalog.uri(code)) }
                        .testTag(PanelTags.sticker(code)),
                ) {
                    thumbnails[code]?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
                }
            }
        }
    }
}

/** A built-in sticker scaled down for the list (128 px instead of 512) */
private fun loadThumbnail(context: Context, code: String): Bitmap? = runCatching {
    context.assets.open(StickerCatalog.assetPath(code)).use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 4 })
    }
}.getOrNull()
