package org.pixeldroid.media_editor.photoEdit.imagine.layers

import org.pixeldroid.media_editor.photoEdit.Adjustment
import org.pixeldroid.media_editor.photoEdit.Sliders
import org.pixeldroid.media_editor.photoEdit.imagine.core.objects.ImagineShader
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

/**
 * All slider adjustments in one pass, reading the original image directly. Before, every slider
 * was a pass of its own, rounded to 8 bit in between.
 *
 * Exposure, white balance (warmth, tint) and brightness are computed in linear light, like light
 * behaves: exposure is a change of f-stops, brightness lifts the midtones and keeps black and
 * white. Tone and colour (contrast, highlights, shadows, saturation, vibrance) work on the
 * perceptual (sRGB) values. Every adjustment at 0 leaves the image unchanged.
 *
 * The sliders are passed as values from -1 to 1 (grain 0 to 1), in the order of [Adjustment].
 */
class AdjustmentsLayer : ImagineLayer(initialIntensity = 1f) {

    override val name: Int? = null

    /** The current slider values */
    var sliders: Sliders = Sliders()

    override fun bind(program: ImagineShader.Program) {
        setUniform(program, "uAdjust", *FloatArray(Adjustment.entries.size) { normalized(Adjustment.entries[it]) })
    }

    /** -1..1 for the slider's range (0..1 if it has no negative side) */
    private fun normalized(adjustment: Adjustment): Float {
        val value = sliders[adjustment]
        return if (value >= 0f) value / adjustment.max else value / -adjustment.min
    }

    override val source: String = """
        // Slider values -1..1, index = position in the Adjustment enum
        uniform highp float uAdjust[13];
        const int EXPOSURE = 0;
        const int BRIGHTNESS = 1;
        const int CONTRAST = 2;
        const int HIGHLIGHTS = 3;
        const int SHADOWS = 4;
        const int CLARITY = 5;
        const int SATURATION = 6;
        const int VIBRANCE = 7;
        const int WARMTH = 8;
        const int TINT = 9;
        const int SHARPNESS = 10;
        const int VIGNETTE = 11;
        const int GRAIN = 12;

        highp vec3 toLinear(highp vec3 c) {
            c = clamp(c, 0.0, 1.0);
            return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
        }

        highp vec3 toSrgb(highp vec3 l) {
            l = clamp(l, 0.0, 1.0);
            return mix(l * 12.92, 1.055 * pow(l, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), l));
        }

        highp float luminance(highp vec3 c) {
            return dot(c, vec3(0.2125, 0.7154, 0.0721));
        }

        highp float grainHash(highp vec2 p) {
            p = fract(p * vec2(443.897, 441.423));
            p += dot(p, p.yx + 19.19);
            return fract((p.x + p.y) * p.x);
        }

        // Parameters renamed: everything here reads the original image at the highp vTexCoords
        vec4 process(vec4 colorMediump, sampler2D uImage, vec2 texCoordsMediump) {
            highp vec2 size = vec2(textureSize(uImage, 0));
            highp vec4 source = texture(uImage, vTexCoords);
            highp vec3 c = source.rgb;

            // --- Detail, from the original's neighbourhood (sizes relative to the image)
            if (uAdjust[SHARPNESS] != 0.0) {
                // Unsharp mask, about 1/1200 of the shorter side
                highp vec2 d = max(1.0, min(size.x, size.y) / 1200.0) / size;
                highp vec3 blur = (
                    texture(uImage, vTexCoords + vec2(-d.x, 0.0)).rgb +
                    texture(uImage, vTexCoords + vec2( d.x, 0.0)).rgb +
                    texture(uImage, vTexCoords + vec2(0.0, -d.y)).rgb +
                    texture(uImage, vTexCoords + vec2(0.0,  d.y)).rgb
                ) * 0.25;
                c += (source.rgb - blur) * 2.0 * uAdjust[SHARPNESS];
            }
            if (uAdjust[CLARITY] != 0.0) {
                // Local contrast in the midtones, about 1/80 of the shorter side
                highp vec2 r = vec2(min(size.x, size.y) / 80.0) / size;
                highp vec3 blur = vec3(0.0);
                for (int i = 0; i < 8; i++) {
                    highp float a = float(i) * 0.785398;
                    highp vec2 dir = vec2(cos(a), sin(a));
                    blur += texture(uImage, vTexCoords + dir * r).rgb;
                    blur += texture(uImage, vTexCoords + dir * r * 0.5).rgb;
                }
                blur /= 16.0;
                highp float midtones = 1.0 - abs(luminance(source.rgb) - 0.5) * 2.0;
                c += (source.rgb - blur) * 1.5 * midtones * uAdjust[CLARITY];
            }
            c = clamp(c, 0.0, 1.0);

            // --- Light: exposure, white balance and brightness in linear light
            if (uAdjust[EXPOSURE] != 0.0 || uAdjust[WARMTH] != 0.0 || uAdjust[TINT] != 0.0 || uAdjust[BRIGHTNESS] != 0.0) {
                highp vec3 light = toLinear(c);
                // Exposure: up to 2 f-stops
                light *= exp2(2.0 * uAdjust[EXPOSURE]);
                // White balance as channel gains, without changing the brightness.
                // Warmth: red up, blue down. Tint: green down (magenta) or up (green).
                highp float w = uAdjust[WARMTH];
                highp float t = uAdjust[TINT];
                highp vec3 gains = vec3(exp2(0.35 * w + 0.15 * t), exp2(-0.3 * t), exp2(-0.35 * w + 0.15 * t));
                light *= gains / luminance(gains);
                // Brightness: lifts or lowers the midtones, black and white stay
                light = pow(clamp(light, 0.0, 1.0), vec3(exp2(-uAdjust[BRIGHTNESS])));
                c = toSrgb(light);
            }

            // --- Tone: contrast, highlights, shadows
            c = 0.5 + (c - 0.5) * (1.0 + 0.9 * uAdjust[CONTRAST]);
            highp float l = luminance(c);
            c += 0.4 * smoothstep(0.45, 1.0, l) * uAdjust[HIGHLIGHTS];
            c += 0.4 * (1.0 - smoothstep(0.0, 0.55, l)) * uAdjust[SHADOWS];

            // --- Colour: saturation, vibrance (more for less saturated colours)
            l = luminance(c);
            c = l + (c - l) * (1.0 + uAdjust[SATURATION]);
            highp float chroma = max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b);
            c += (c - l) * 1.5 * (1.0 - smoothstep(0.0, 0.6, chroma)) * uAdjust[VIBRANCE];

            // --- Vignette: darker (or lighter) towards the edges
            highp float falloff = smoothstep(0.25, 0.75, distance(vTexCoords, vec2(0.5)));
            c *= 1.0 - falloff * uAdjust[VIGNETTE];

            // --- Grain: fine noise, the same pattern at any image size
            if (uAdjust[GRAIN] != 0.0) {
                highp vec2 cells = size / min(size.x, size.y) * 700.0;
                highp float noise = grainHash(floor(vTexCoords * cells)) - 0.5;
                c += noise * 0.25 * (1.0 - abs(luminance(c) - 0.5)) * uAdjust[GRAIN];
            }

            return vec4(clamp(c, 0.0, 1.0), source.a);
        }
    """.trimIndent()
}
