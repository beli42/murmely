package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import androidx.core.widget.NestedScrollView

/**
 * A [NestedScrollView] that never scrolls by itself while the user is using touch.
 *
 * A normal NestedScrollView scrolls automatically to fully reveal a child that gets focus,
 * changes size or asks to be shown. With a list of sliders this makes a partially visible
 * slider jump away from under the finger while it is being dragged. All of these automatic
 * scrolls go through [computeScrollDeltaToGetChildRectOnScreen], so it is disabled in touch
 * mode. In non-touch mode (keyboard / D-pad) the default behaviour is kept, so focused
 * sliders are still scrolled into view during keyboard navigation.
 */
class StableNestedScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : NestedScrollView(context, attrs, defStyleAttr) {

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect): Int =
        if (isInTouchMode) 0 else super.computeScrollDeltaToGetChildRectOnScreen(rect)
}
