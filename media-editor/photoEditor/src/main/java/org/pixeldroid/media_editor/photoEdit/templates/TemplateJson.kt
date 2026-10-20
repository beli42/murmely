package org.pixeldroid.media_editor.photoEdit.templates

import org.json.JSONArray
import org.json.JSONObject
import org.pixeldroid.media_editor.photoEdit.Adjustment
import org.pixeldroid.media_editor.photoEdit.Sliders
import org.pixeldroid.media_editor.photoEdit.frame.FramePlacement
import org.pixeldroid.media_editor.photoEdit.frame.FrameSettings
import org.pixeldroid.media_editor.photoEdit.frame.FrameStyle
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.FilterSettings
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.MirrorPart
import org.pixeldroid.media_editor.photoEdit.overlay.OverlayDefaults
import org.pixeldroid.media_editor.photoEdit.overlay.StickerItem
import org.pixeldroid.media_editor.photoEdit.overlay.TextEffects
import org.pixeldroid.media_editor.photoEdit.overlay.TextFont
import org.pixeldroid.media_editor.photoEdit.overlay.TextItem

/**
 * Templates as JSON, for the database (the name is kept in its own column). Unknown values,
 * e.g. from a newer version, fall back to defaults instead of failing.
 */
object TemplateJson {

    fun write(template: Template): String = JSONObject().apply {
        put("version", 1)
        put("sliders", JSONObject().apply {
            Adjustment.entries.forEach { a -> template.sliders[a].takeIf { it != 0f }?.let { put(a.name, it.toDouble()) } }
        })
        put("filters", JSONArray().apply {
            template.filters.forEach { f ->
                put(JSONObject().apply {
                    f.key?.let { put("key", it) }
                    f.customName?.let { put("customName", it) }
                    f.source?.let { put("source", it) }
                    put("strength", f.settings.strength.toDouble())
                    put("centerX", f.settings.centerX.toDouble())
                    put("centerY", f.settings.centerY.toDouble())
                    put("mirrorPart", f.settings.mirrorPart.name)
                })
            }
        })
        if (template.stickers.isNotEmpty()) put("stickers", JSONArray().apply {
            template.stickers.forEach { st ->
                put(JSONObject().apply {
                    put("uri", st.uri)
                    put("x", st.x.toDouble()); put("y", st.y.toDouble())
                    put("width", st.width.toDouble()); put("aspect", st.aspect.toDouble())
                    put("rotation", st.rotation.toDouble()); put("layer", st.layer)
                    put("opacity", st.opacity.toDouble())
                })
            }
        })
        if (template.texts.isNotEmpty()) put("texts", JSONArray().apply {
            template.texts.forEach { t ->
                put(JSONObject().apply {
                    put("text", t.text)
                    put("x", t.x.toDouble()); put("y", t.y.toDouble())
                    put("size", t.size.toDouble()); put("color", t.color)
                    put("rotation", t.rotation.toDouble()); put("font", t.font.name)
                    put("layer", t.layer); put("opacity", t.opacity.toDouble())
                    val e = t.effects
                    put("effects", JSONObject().apply {
                        put("outline", e.outline.toDouble()); put("outlineColor", e.outlineColor)
                        put("letterSpacing", e.letterSpacing.toDouble()); put("shadow", e.shadow.toDouble())
                        put("dropShadow", e.dropShadow.toDouble()); put("dropShadowColor", e.dropShadowColor)
                        put("dropShadowAngle", e.dropShadowAngle.toDouble())
                    })
                })
            }
        })
        template.frame?.let { frame ->
            put("frame", JSONObject().apply {
                put("style", frame.style.name)
                put("placement", frame.placement.name)
                put("size", frame.size.toDouble())
                put("hue", frame.hue.toDouble())
                put("lightness", frame.lightness.toDouble())
            })
        }
    }.toString()

    fun read(name: String, json: String): Template {
        val o = JSONObject(json)
        var sliders = Sliders()
        o.optJSONObject("sliders")?.let { s ->
            Adjustment.entries.forEach { a -> if (s.has(a.name)) sliders = sliders.with(a, s.getDouble(a.name).toFloat()) }
        }
        val filters = o.optJSONArray("filters")?.let { array ->
            (0 until array.length()).map { i ->
                val f = array.getJSONObject(i)
                TemplateFilter(
                    key = f.optString("key").ifEmpty { null },
                    customName = f.optString("customName").ifEmpty { null },
                    source = f.optString("source").ifEmpty { null },
                    settings = FilterSettings(
                        strength = f.optDouble("strength", 1.0).toFloat(),
                        centerX = f.optDouble("centerX", 0.5).toFloat(),
                        centerY = f.optDouble("centerY", 0.5).toFloat(),
                        mirrorPart = enumOr(f.optString("mirrorPart"), MirrorPart.UNDECIDED),
                    ),
                )
            }
        }.orEmpty()
        val frame = o.optJSONObject("frame")?.let { f ->
            FrameSettings(
                style = enumOr(f.optString("style"), FrameStyle.SOLID),
                placement = enumOr(f.optString("placement"), FramePlacement.OUTSIDE),
                size = f.optDouble("size", FrameSettings.DEFAULT_SIZE.toDouble()).toFloat(),
                hue = f.optDouble("hue", 0.0).toFloat(),
                lightness = f.optDouble("lightness", 1.0).toFloat(),
            )
        }
        val stickers = o.optJSONArray("stickers")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val st = array.getJSONObject(i)
                val uri = st.optString("uri").ifEmpty { null } ?: return@mapNotNull null
                StickerItem(
                    uri = uri,
                    x = st.optDouble("x", 0.5).toFloat(), y = st.optDouble("y", 0.5).toFloat(),
                    width = st.optDouble("width", OverlayDefaults.STICKER_WIDTH.toDouble()).toFloat(),
                    aspect = st.optDouble("aspect", 1.0).toFloat(),
                    rotation = st.optDouble("rotation", 0.0).toFloat(),
                    layer = st.optLong("layer", 0),
                    opacity = st.optDouble("opacity", 1.0).toFloat(),
                )
            }
        }.orEmpty()
        val texts = o.optJSONArray("texts")?.let { array ->
            (0 until array.length()).mapNotNull { i ->
                val t = array.getJSONObject(i)
                val words = t.optString("text").ifEmpty { null } ?: return@mapNotNull null
                val e = t.optJSONObject("effects")
                val defaults = TextEffects()
                TextItem(
                    text = words,
                    x = t.optDouble("x", 0.5).toFloat(), y = t.optDouble("y", 0.5).toFloat(),
                    size = t.optDouble("size", OverlayDefaults.TEXT_SIZE.toDouble()).toFloat(),
                    color = t.optInt("color", OverlayDefaults.COLOR),
                    rotation = t.optDouble("rotation", 0.0).toFloat(),
                    font = enumOr(t.optString("font"), TextFont.DEFAULT),
                    layer = t.optLong("layer", 0),
                    opacity = t.optDouble("opacity", 1.0).toFloat(),
                    effects = if (e == null) defaults else TextEffects(
                        outline = e.optDouble("outline", 0.0).toFloat(),
                        outlineColor = e.optInt("outlineColor", defaults.outlineColor),
                        letterSpacing = e.optDouble("letterSpacing", 0.0).toFloat(),
                        shadow = e.optDouble("shadow", 0.0).toFloat(),
                        dropShadow = e.optDouble("dropShadow", 0.0).toFloat(),
                        dropShadowColor = e.optInt("dropShadowColor", defaults.dropShadowColor),
                        dropShadowAngle = e.optDouble("dropShadowAngle", defaults.dropShadowAngle.toDouble()).toFloat(),
                    ),
                )
            }
        }.orEmpty()
        return Template(name, sliders, filters, frame, stickers, texts)
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
