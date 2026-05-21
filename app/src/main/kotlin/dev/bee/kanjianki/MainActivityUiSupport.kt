package dev.bee.kanjianki

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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

    fun text(value: String?, sp: Int, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = value ?: ""
            textSize = sp.toFloat()
            setTextColor(color)
            includeFontPadding = true
            setLineSpacing(0f, 1.05f)
            typeface = Typeface.DEFAULT
            setTypeface(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    fun panel(fill: Int, stroke: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            setStroke(dp(1), stroke)
            cornerRadius = radius.toFloat()
        }
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
        @JvmField val BG: Int = Color.rgb(255, 247, 251)
        @JvmField val INK: Int = Color.rgb(45, 22, 53)
        @JvmField val MUTED: Int = Color.rgb(108, 86, 116)
        @JvmField val CORAL: Int = Color.rgb(255, 76, 118)
        @JvmField val TEAL: Int = Color.rgb(0, 174, 181)
        @JvmField val GOLD: Int = Color.rgb(255, 214, 64)
        @JvmField val BLUE: Int = Color.rgb(110, 92, 230)
        @JvmField val BLUSH: Int = Color.rgb(255, 239, 246)
        @JvmField val PINK_STROKE: Int = Color.rgb(255, 174, 204)
        @JvmField val LILAC: Int = Color.rgb(118, 72, 255)
        @JvmField val STUDY_BG: Int = Color.rgb(255, 245, 250)
        @JvmField val STUDY_CARD: Int = Color.rgb(255, 255, 255)
        @JvmField val STUDY_PANEL: Int = Color.rgb(255, 236, 245)
        @JvmField val STUDY_PLUM: Int = Color.rgb(75, 37, 82)
        @JvmField val STUDY_MUTED: Int = Color.rgb(130, 96, 132)
        @JvmField val STUDY_PINK_DARK: Int = Color.rgb(218, 58, 122)
        @JvmField val STUDY_BORDER: Int = Color.rgb(255, 199, 222)
        @JvmField val STUDY_PILL: Int = Color.rgb(255, 239, 247)
        @JvmField val STUDY_BG_SOFT: Int = Color.rgb(255, 246, 251)
        @JvmField val STUDY_HERO_PANEL: Int = Color.rgb(253, 241, 247)
        @JvmField val STUDY_HERO_PILL: Int = Color.rgb(253, 239, 246)
        @JvmField val STUDY_HERO_PINK: Int = Color.rgb(248, 45, 114)
        @JvmField val STUDY_HERO_PLUM: Int = Color.rgb(33, 7, 44)
        @JvmField val STUDY_HERO_MUTED: Int = Color.rgb(102, 82, 110)
    }
}
