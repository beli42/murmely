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
        return Template(name, sliders, filters, frame)
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
