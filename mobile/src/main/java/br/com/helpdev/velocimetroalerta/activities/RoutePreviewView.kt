package br.com.helpdev.velocimetroalerta.activities

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import br.com.helpdev.velocimetroalerta.R
import kotlin.math.max
import kotlin.math.min

data class PreviewCoordinate(val x: Float, val y: Float)

class RoutePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var points: List<ActivityPreviewPoint> = emptyList()
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.colorPrimary)
        style = Paint.Style.STROKE
        strokeWidth = 5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE5F1E2.toInt() }

    fun submit(points: List<ActivityPreviewPoint>) {
        this.points = points.toList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        if (points.size < 2 || width == 0 || height == 0) return
        val path = Path()
        val coordinates = normalize(points, width.toFloat(), height.toFloat(), 14f * resources.displayMetrics.density)
        coordinates.forEachIndexed { index, point ->
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        canvas.drawPath(path, routePaint)
    }

    companion object {
        fun normalize(
            points: List<ActivityPreviewPoint>,
            width: Float,
            height: Float,
            padding: Float = 12f
        ): List<PreviewCoordinate> {
            if (points.isEmpty()) return emptyList()
            val minLatitude = points.minOf { it.latitude }
            val maxLatitude = points.maxOf { it.latitude }
            val minLongitude = points.minOf { it.longitude }
            val maxLongitude = points.maxOf { it.longitude }
            val usableWidth = max(0f, width - 2 * padding)
            val usableHeight = max(0f, height - 2 * padding)
            val latitudeSpan = maxLatitude - minLatitude
            val longitudeSpan = maxLongitude - minLongitude
            return points.map { point ->
                val x = if (longitudeSpan == 0.0) width / 2f
                else padding + ((point.longitude - minLongitude) / longitudeSpan).toFloat() * usableWidth
                val y = if (latitudeSpan == 0.0) height / 2f
                else padding + ((maxLatitude - point.latitude) / latitudeSpan).toFloat() * usableHeight
                PreviewCoordinate(x.coerceIn(min(padding, width / 2f), max(width - padding, width / 2f)),
                    y.coerceIn(min(padding, height / 2f), max(height - padding, height / 2f)))
            }
        }
    }
}
