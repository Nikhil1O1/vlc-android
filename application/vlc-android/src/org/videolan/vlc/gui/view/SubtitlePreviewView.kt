package org.videolan.vlc.gui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import org.videolan.resources.SubtitleStyle
import org.videolan.vlc.R
import org.videolan.vlc.gui.video.SubtitlePositionHelper

/**
 * A 16:9 mock video frame showing a sample subtitle with the given [style] and vertical [offset],
 * so the style can be judged before it is applied. Dragging vertically changes the offset.
 *
 * The text size follows libVLC: the font height is the frame height divided by [SubtitleStyle.size].
 */
class SubtitlePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var style: SubtitleStyle = SubtitleStyle.DEFAULT
        set(value) {
            field = value
            invalidate()
        }

    /** Fraction of the frame height the subtitle is lifted by, see [SubtitlePositionHelper] */
    var offset: Float = 0f
        set(value) {
            field = SubtitlePositionHelper.clamp(value)
            invalidate()
        }

    /** Called with the new offset while the user drags the preview vertically */
    var onOffsetDragged: ((Float) -> Unit)? = null

    private val framePaint = Paint()
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.style = Paint.Style.STROKE }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sampleText = context.getString(R.string.subtitle_style_preview)
    private var lastTouchY = 0f

    /** The strip is capped in height so the panel stays short, but sizes follow the full 16:9 frame it stands for */
    private val maxStripHeight = (MAX_STRIP_HEIGHT_DP * resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, minOf(width * 9 / 16, maxStripHeight))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        // Height of the whole 16:9 video frame this strip is a crop of (its bottom part)
        val frameH = w * 9f / 16f

        // Two bands, dark and bright, to show how readable the style is on both
        framePaint.color = 0xFF2B2B2B.toInt()
        canvas.drawRect(0f, 0f, w, h * 0.5f, framePaint)
        framePaint.color = 0xFFB8B8B8.toInt()
        canvas.drawRect(0f, h * 0.5f, w, h, framePaint)

        val divisor = style.size.toFloatOrNull() ?: 16f
        val textSize = frameH / divisor
        textPaint.textSize = textSize
        textPaint.typeface = if (style.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.color = withOpacity(style.color, style.colorOpacity)
        textPaint.clearShadowLayer()
        if (style.shadowEnabled) {
            textPaint.setShadowLayer(textSize * 0.15f, textSize * 0.06f, textSize * 0.06f, withOpacity(style.shadowColor, style.shadowOpacity))
        }

        val textWidth = textPaint.measureText(sampleText)
        val x = (w - textWidth) / 2f
        val bottom = (h * 0.94f - offset * frameH).coerceAtLeast(textSize * 1.2f)
        val baseline = bottom - textPaint.descent()

        if (style.backgroundEnabled) {
            val pad = textSize * 0.25f
            boxPaint.color = withOpacity(style.backgroundColor, style.backgroundOpacity)
            canvas.drawRect(
                x - pad, baseline + textPaint.ascent() - pad / 2f,
                x + textWidth + pad, baseline + textPaint.descent() + pad / 2f, boxPaint
            )
        }
        if (style.outlineEnabled) {
            strokePaint.textSize = textSize
            strokePaint.typeface = textPaint.typeface
            strokePaint.strokeWidth = textSize * 0.14f
            strokePaint.strokeJoin = Paint.Join.ROUND
            strokePaint.color = withOpacity(style.outlineColor, style.outlineOpacity)
            canvas.drawText(sampleText, x, baseline, strokePaint)
        }
        canvas.drawText(sampleText, x, baseline, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                // Keep the enclosing scroll view from stealing the vertical drag
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (height > 0) {
                    val newOffset = SubtitlePositionHelper.clamp(offset - (event.y - lastTouchY) / height)
                    lastTouchY = event.y
                    if (newOffset != offset) {
                        offset = newOffset
                        onOffsetDragged?.invoke(newOffset)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    private fun withOpacity(color: Int, opacity: Int): Int = (opacity.coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)

    private companion object {
        const val MAX_STRIP_HEIGHT_DP = 110
    }
}
