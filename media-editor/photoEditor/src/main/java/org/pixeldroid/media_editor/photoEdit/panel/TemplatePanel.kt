package org.pixeldroid.media_editor.photoEdit.panel

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.pixeldroid.media_editor.photoEdit.R
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable

/** A saved template as shown: its database id, name and preview (null while it is rendered) */
data class TemplateItem(val id: Int, val name: String, val preview: Bitmap?)

object TemplateTags {
    const val SAVE = "template_save"
    fun template(id: Int) = "template_$id"
    const val NAME = "template_name"
    const val OK = "template_ok"
    const val DELETE = "template_delete"
    const val WITH_OVERLAY = "template_with_overlay"
}

/**
 * The saved templates: first "save as template", then one tile per template. Tap applies it,
 * a long press renames or deletes it.
 */
@Composable
fun TemplatePanel(
    templates: List<TemplateItem>,
    vertical: Boolean,
    /** Name, and whether stickers and texts go into the template too (e.g. a watermark) */
    onSave: (String, Boolean) -> Unit,
    onApply: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The dialog shown: a new template, or the template being renamed or deleted
    var naming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TemplateItem?>(null) }
    val defaultName = stringResource(R.string.template_default_name, templates.size + 1)

    val save: @Composable () -> Unit = {
        CreateItem(stringResource(R.string.template_save), TemplateTags.SAVE, vertical) { naming = true }
    }
    val tile: @Composable (TemplateItem) -> Unit = { t ->
        FilterItem(
            name = t.name,
            thumbnail = t.preview,
            selected = false,
            onClick = { onApply(t.id) },
            onLongClick = { editing = t },
            vertical = vertical,
            modifier = Modifier.testTag(TemplateTags.template(t.id)),
        )
    }
    Box(modifier) {
        if (vertical) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { save() }
                items(templates, key = { it.id }) { tile(it) }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { save() }
                items(templates, key = { it.id }) { tile(it) }
            }
        }
    }

    if (naming) {
        var withOverlay by remember { mutableStateOf(false) }
        TemplateNameDialog(
            title = stringResource(R.string.template_save),
            initial = defaultName,
            onDone = { name -> onSave(name, withOverlay); naming = false },
            onDismiss = { naming = false },
            extra = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { withOverlay = !withOverlay }
                        .testTag(TemplateTags.WITH_OVERLAY),
                ) {
                    Checkbox(checked = withOverlay, onCheckedChange = { withOverlay = it })
                    Text(stringResource(R.string.template_with_overlay), style = MaterialTheme.typography.bodyMedium)
                }
            },
        )
    }
    editing?.let { t ->
        TemplateNameDialog(
            title = stringResource(R.string.template_edit),
            initial = t.name,
            onDone = { name -> onRename(t.id, name); editing = null },
            onDismiss = { editing = null },
            onDelete = { onDelete(t.id); editing = null },
        )
    }
}

/** Asks for a template's name; with [onDelete] also offers to delete it */
@Composable
private fun TemplateNameDialog(
    title: String,
    initial: String,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.template_name)) },
                    modifier = Modifier.testTag(TemplateTags.NAME),
                )
                extra?.invoke()
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onDone(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag(TemplateTags.OK),
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete, modifier = Modifier.testTag(TemplateTags.DELETE)) {
                        Text(stringResource(R.string.delete))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    )
}
