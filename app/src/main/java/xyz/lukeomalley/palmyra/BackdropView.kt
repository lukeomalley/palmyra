package xyz.lukeomalley.palmyra

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.DecelerateInterpolator

@Suppress("MagicNumber")
class BackdropView(
    context: Context,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bounds = RectF()
    private val pill = RectF()
    private val player = RectF()
    private val clip = Path()
    private var current: WallpaperFrame? = null
    private var previous: WallpaperFrame? = null
    private var fade = 1f
    private var transition: ValueAnimator? = null
    private var motion: ValueAnimator? = null
    private var left: Shader? = null
    private var bottom: Shader? = null
    val hasPhoto: Boolean get() = current != null
    var onPhotoDrawn: (() -> Unit)? = null
    var showPlayer = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var showFocus = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var uiAlpha = 1f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    fun show(
        frame: WallpaperFrame,
        animate: Boolean = true,
    ) {
        transition?.cancel()
        previous?.release()
        previous = current
        current = frame
        if (!animate || previous == null) {
            finishTransition()
            invalidate()
            return
        }
        fade = 0f
        transition =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1000
                addUpdateListener {
                    fade = it.animatedValue as Float
                    invalidate()
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            finishTransition()
                        }
                    },
                )
                start()
            }
    }

    private fun finishTransition() {
        previous?.release()
        previous = null
        fade = 1f
    }

    fun pauseAnimations() {
        transition?.cancel()
        motion?.cancel()
    }

    fun clear() {
        pauseAnimations()
        previous?.release()
        previous = null
        current?.release()
        current = null
        invalidate()
    }

    fun focus(
        target: RectF,
        animate: Boolean,
    ) {
        if (motion?.isRunning != true && pill == target) return
        motion?.cancel()
        if (!animate || pill.isEmpty) {
            pill.set(target)
            invalidate()
            return
        }
        val start = RectF(pill)
        motion =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 180
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val t = it.animatedValue as Float
                    pill.set(
                        start.left + (target.left - start.left) * t,
                        start.top + (target.top - start.top) * t,
                        start.right + (target.right - start.right) * t,
                        start.bottom + (target.bottom - start.bottom) * t,
                    )
                    invalidate()
                }
                start()
            }
    }

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        val density = resources.displayMetrics.density
        player.set(
            w - (Geometry.PLAYER_RIGHT + Geometry.PLAYER_WIDTH) * density,
            Geometry.PLAYER_TOP * density,
            w - Geometry.PLAYER_RIGHT * density,
            (Geometry.PLAYER_TOP + Geometry.PLAYER_HEIGHT) * density,
        )
        left = LinearGradient(0f, 0f, w * .6f, 0f, 0x8C000000.toInt(), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        bottom = LinearGradient(0f, h * .6f, 0f, h.toFloat(), Color.TRANSPARENT, 0x59000000, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (current == null || fade < 1f) canvas.drawColor(0xFF111417.toInt())
        previous?.let { drawPhoto(canvas, it, 1f) }
        current?.let { drawPhoto(canvas, it, fade) }
        if (uiAlpha > 0f) {
            scrims(canvas)
            if (showFocus && !pill.isEmpty) drawGlass(canvas, pill)
            if (showPlayer) drawGlass(canvas, player, true)
        }
        if (current != null && fade == 1f) {
            val callback = onPhotoDrawn
            onPhotoDrawn = null
            if (callback != null) post { callback() }
        }
    }

    private fun drawPhoto(
        canvas: Canvas,
        frame: WallpaperFrame,
        alpha: Float,
    ) {
        paint.shader = null
        paint.alpha = (alpha * 255).toInt()
        canvas.drawBitmap(frame.photo, null, bounds, paint)
        paint.alpha = 255
    }

    private fun scrims(canvas: Canvas) {
        paint.shader = null
        paint.color = Color.BLACK
        paint.alpha = (uiAlpha * 33).toInt()
        canvas.drawRect(bounds, paint)
        paint.alpha = (uiAlpha * 255).toInt()
        paint.shader = left
        canvas.drawRect(bounds, paint)
        paint.shader = bottom
        canvas.drawRect(bounds, paint)
        paint.shader = null
        paint.alpha = 255
    }

    private fun drawGlass(
        canvas: Canvas,
        area: RectF,
        isPlayer: Boolean = false,
    ) {
        val save = canvas.save()
        clip.reset()
        clip.addRoundRect(
            area,
            14 * resources.displayMetrics.density,
            14 * resources.displayMetrics.density,
            Path.Direction.CW,
        )
        canvas.clipPath(clip)
        paint.color = Color.WHITE
        paint.alpha = (uiAlpha * 255).toInt()
        val destination = if (isPlayer) area else bounds
        previous?.let { canvas.drawBitmap(if (isPlayer) it.playerGlass else it.blur, null, destination, paint) }
        paint.alpha = (uiAlpha * fade * 255).toInt()
        current?.let { canvas.drawBitmap(if (isPlayer) it.playerGlass else it.blur, null, destination, paint) }
        scrims(canvas)
        paint.color = 0xFFF3EFE9.toInt()
        paint.alpha = (uiAlpha * if (isPlayer) 41 else 30).toInt()
        canvas.drawRect(area, paint)
        canvas.restoreToCount(save)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = Color.WHITE
        paint.alpha = (uiAlpha * if (isPlayer) 36 else 20).toInt()
        canvas.drawPath(clip, paint)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }
}
