package org.pixeldroid.media_editor.photoEdit.frame

import org.pixeldroid.media_editor.photoEdit.overlay.ImageFrame
import org.pixeldroid.media_editor.photoEdit.overlay.hslColor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The look of a frame */
enum class FrameStyle {
    /** An even area */
    SOLID,
    /** An area with two fine lines */
    DOUBLE_LINE,
    /** Much wider at the bottom, like an instant photo */
    POLAROID,
    /** A wide mat with a fine line around the photo */
    PASSEPARTOUT,
    /** The photo's corners rounded off */
    ROUNDED,
    /** Film strip: perforation at the top and bottom */
    FILM,
}

/** Where the frame goes */
enum class FramePlacement {
    /** Over the edge of the photo: the size stays, the edge is covered */
    INSIDE,
    /** Around the photo: the image grows, nothing of the photo is covered */
    OUTSIDE,
}

/**
 * A frame. [size] is the frame width relative to the shorter side of the photo; the colour is
 * [hue] (degrees) and [lightness] (0 black .. 1 white) at full saturation, like the text colour.
 */
data class FrameSettings(
    val style: FrameStyle = FrameStyle.SOLID,
    val placement: FramePlacement = FramePlacement.OUTSIDE,
    val size: Float = DEFAULT_SIZE,
    val hue: Float = 0f,
    val lightness: Float = 1f,
) {
    /** ARGB colour, opaque */
    val color: Int get() = hslColor(hue, 1f, lightness)

    companion object {
        const val DEFAULT_SIZE = 0.05f
        val SIZE_RANGE = 0.01f..0.25f

        /** A new frame of [style]; a film strip is black, everything else white */
        fun of(style: FrameStyle, placement: FramePlacement = FramePlacement.OUTSIDE, size: Float = DEFAULT_SIZE) =
            FrameSettings(style, placement, size, lightness = if (style == FrameStyle.FILM) 0f else 1f)
    }
}

/** Widths of a frame on each side, in pixels */
data class FrameMargins(val left: Int, val top: Int, val right: Int, val bottom: Int)

/** A rectangle in pixels */
data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int)

/**
 * Where the framed result ([output]) and the photo in it ([photo]) are on screen, and how many
 * screen pixels one image pixel is ([scale])
 */
data class FrameLayout(val output: PixelRect, val photo: PixelRect, val scale: Float)

/** Sizes of frames. Pure, no Android. */
object FrameGeometry {

    /** Basic frame width in pixels for a photo of [width] x [height] */
    fun base(settings: FrameSettings, width: Int, height: Int): Float =
        settings.size.coerceIn(FrameSettings.SIZE_RANGE) * min(width, height)

    /** Frame widths on each side for a photo of [width] x [height] (at least 1 pixel) */
    fun margins(settings: FrameSettings, width: Int, height: Int): FrameMargins {
        val base = base(settings, width, height)
        fun px(factor: Float) = max(1, (base * factor).roundToInt())
        return when (settings.style) {
            FrameStyle.POLAROID -> FrameMargins(px(1f), px(1f), px(1f), px(3.5f))
            FrameStyle.FILM -> FrameMargins(px(0.35f), px(1.4f), px(0.35f), px(1.4f))
            else -> px(1f).let { FrameMargins(it, it, it, it) }
        }
    }

    /** Size of the result: the photo's size inside, larger by the frame outside */
    fun outputSize(settings: FrameSettings?, width: Int, height: Int): Pair<Int, Int> {
        if (settings == null || settings.placement == FramePlacement.INSIDE) return width to height
        val m = margins(settings, width, height)
        return (width + m.left + m.right) to (height + m.top + m.bottom)
    }

    /**
     * Where the photo can be seen through the frame, in pixels of the result: with the frame
     * outside it is the whole photo, inside it is the photo less the frame
     */
    fun window(settings: FrameSettings, width: Int, height: Int): PixelRect {
        val m = margins(settings, width, height)
        val (outWidth, outHeight) = outputSize(settings, width, height)
        return PixelRect(m.left, m.top, outWidth - m.left - m.right, outHeight - m.top - m.bottom)
    }

    /**
     * The framed photo of [width] x [height] scaled to fit a view of [viewWidth] x [viewHeight]:
     * where the result and the photo in it are. Without a frame or with one inside, both are
     * the same rectangle.
     */
    fun layoutOnScreen(settings: FrameSettings?, width: Int, height: Int, viewWidth: Int, viewHeight: Int): FrameLayout {
        val (outWidth, outHeight) = outputSize(settings, width, height)
        val fit = ImageFrame(outWidth, outHeight, viewWidth, viewHeight)
        val scale = fit.shownWidth.toFloat() / outWidth
        val output = PixelRect(fit.sideBar, fit.topBar, fit.shownWidth, fit.shownHeight)
        if (settings == null || settings.placement == FramePlacement.INSIDE) return FrameLayout(output, output, scale)
        val m = margins(settings, width, height)
        val photo = PixelRect(
            output.left + (m.left * scale).roundToInt(),
            output.top + (m.top * scale).roundToInt(),
            (width * scale).roundToInt(),
            (height * scale).roundToInt(),
        )
        return FrameLayout(output, photo, scale)
    }
}
