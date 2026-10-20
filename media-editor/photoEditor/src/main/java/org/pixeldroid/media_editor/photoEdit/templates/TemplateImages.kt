package org.pixeldroid.media_editor.photoEdit.templates

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * Sticker images of templates, kept in the app's own storage: a template must still work when
 * the image is moved or deleted in the gallery, or the app's access to it ends.
 */
object TemplateImages {

    private fun folder(context: Context) = File(context.filesDir, "template-images")

    /**
     * [uri] as an image the template can rely on: built-in stickers (app assets) and images
     * already kept stay as they are, others are copied; if copying fails, [uri] itself
     */
    fun keep(context: Context, uri: String): String {
        if (uri.startsWith("file:///android_asset/")) return uri
        val dir = folder(context)
        if (uri.startsWith(Uri.fromFile(dir).toString())) return uri
        return runCatching {
            dir.mkdirs()
            val target = File(dir, "${UUID.randomUUID()}.image")
            val copied = context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            if (copied == null) uri else Uri.fromFile(target).toString()
        }.getOrDefault(uri)
    }

    /** Deletes kept images of [removed] that no template in [remaining] uses any more */
    fun forget(context: Context, removed: Template, remaining: List<Template>) {
        val dir = Uri.fromFile(folder(context)).toString()
        val stillUsed = remaining.flatMap { t -> t.stickers.map { it.uri } }.toSet()
        removed.stickers.map { it.uri }
            .filter { it.startsWith(dir) && it !in stillUsed }
            .forEach { uri -> Uri.parse(uri).path?.let { File(it).delete() } }
    }
}
