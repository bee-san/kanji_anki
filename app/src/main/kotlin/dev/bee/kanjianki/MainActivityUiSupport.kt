package dev.bee.kanjianki

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isEmpty
import androidx.core.view.isGone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal abstract class MainActivityUiSupport : ComponentActivity() {
    fun styleSystemBars() {
        window.decorView.setBackgroundColor(BG)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }

    fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    class SpaceView(context: Context) : View(context)

    class SquarePadFrame : ViewGroup {
        private val maxSizePx: Int

        constructor(context: Context) : this(context, null, 0, 0)

        constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0, 0)

        constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(
            context,
            attrs,
            defStyleAttr,
            0
        )

        constructor(context: Context, maxSizePx: Int) : this(context, null, 0, maxSizePx)

        private constructor(
            context: Context,
            attrs: AttributeSet?,
            defStyleAttr: Int,
            maxSizePx: Int,
        ) : super(context, attrs, defStyleAttr) {
            this.maxSizePx = maxSizePx
            clipToPadding = false
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val widthMode = MeasureSpec.getMode(widthMeasureSpec)
            val widthSize = MeasureSpec.getSize(widthMeasureSpec)
            val heightMode = MeasureSpec.getMode(heightMeasureSpec)
            val heightSize = MeasureSpec.getSize(heightMeasureSpec)
            val horizontalPadding = paddingLeft + paddingRight
            val verticalPadding = paddingTop + paddingBottom
            val effectiveMaxSize = if (maxSizePx <= 0) Int.MAX_VALUE else maxSizePx
            val availableWidth = if (widthMode == MeasureSpec.UNSPECIFIED) {
                effectiveMaxSize
            } else {
                max(0, widthSize - horizontalPadding)
            }
            val availableHeight = if (heightMode == MeasureSpec.UNSPECIFIED) {
                effectiveMaxSize
            } else {
                max(0, heightSize - verticalPadding)
            }
            val size = max(0, min(min(availableWidth, availableHeight), effectiveMaxSize))
            val childSpec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                if (child.visibility != GONE) {
                    child.measure(childSpec, childSpec)
                }
            }
            val measuredWidth = if (widthMode == MeasureSpec.EXACTLY) {
                widthSize
            } else {
                size + horizontalPadding
            }
            var measuredHeight = size + verticalPadding
            measuredHeight = when (heightMode) {
                MeasureSpec.EXACTLY -> heightSize
                MeasureSpec.AT_MOST -> min(measuredHeight, heightSize)
                else -> measuredHeight
            }
            setMeasuredDimension(measuredWidth, measuredHeight)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            if (isEmpty()) {
                return
            }
            val contentLeft = paddingLeft
            val contentTop = paddingTop
            val contentWidth = max(0, right - left - paddingLeft - paddingRight)
            val contentHeight = max(0, bottom - top - paddingTop - paddingBottom)
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                if (child.isGone) {
                    continue
                }
                val size = min(child.measuredWidth, child.measuredHeight)
                val childLeft = contentLeft + max(0, (contentWidth - size) / 2)
                val childTop = contentTop + max(0, (contentHeight - size) / 2)
                child.layout(childLeft, childTop, childLeft + size, childTop + size)
            }
        }
    }

    companion object {
        @JvmField val BG: Int = 0xFFFFF7FB.toInt()
        @JvmField val INK: Int = 0xFF2D1635.toInt()
        @JvmField val MUTED: Int = 0xFF6C5674.toInt()
        @JvmField val CORAL: Int = 0xFFFF4C76.toInt()
        @JvmField val TEAL: Int = 0xFF00AEB5.toInt()
        @JvmField val GOLD: Int = 0xFFFFD640.toInt()
        @JvmField val BLUE: Int = 0xFF6E5CE6.toInt()
        @JvmField val BLUSH: Int = 0xFFFFEFF6.toInt()
        @JvmField val PINK_STROKE: Int = 0xFFFFAECC.toInt()
        @JvmField val LILAC: Int = 0xFF7648FF.toInt()
        @JvmField val STUDY_BG: Int = 0xFFFFF5FA.toInt()
        @JvmField val STUDY_CARD: Int = 0xFFFFFFFF.toInt()
        @JvmField val STUDY_PANEL: Int = 0xFFFFECF5.toInt()
        @JvmField val STUDY_PLUM: Int = 0xFF4B2552.toInt()
        @JvmField val STUDY_MUTED: Int = 0xFF826084.toInt()
        @JvmField val STUDY_PINK_DARK: Int = 0xFFDA3A7A.toInt()
        @JvmField val STUDY_BORDER: Int = 0xFFFFC7DE.toInt()
        @JvmField val STUDY_PILL: Int = 0xFFFFEFF7.toInt()
        @JvmField val STUDY_BG_SOFT: Int = 0xFFFFF6FB.toInt()
        @JvmField val STUDY_HERO_PANEL: Int = 0xFFFDF1F7.toInt()
        @JvmField val STUDY_HERO_PILL: Int = 0xFFFDEFF6.toInt()
        @JvmField val STUDY_HERO_PINK: Int = 0xFFF82D72.toInt()
        @JvmField val STUDY_HERO_PLUM: Int = 0xFF21072C.toInt()
        @JvmField val STUDY_HERO_MUTED: Int = 0xFF66526E.toInt()
    }
}
