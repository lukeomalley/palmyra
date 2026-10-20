package xyz.lukeomalley.palmyra

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Constructed in code. Physical right alignment follows the TV reference. */
@SuppressLint("ViewConstructor", "RtlHardcoded")
@Suppress("MagicNumber", "TooManyFunctions")
class HomeView(
    context: Context,
    val apps: List<AppEntry>,
    val allApps: Boolean = false,
) : FrameLayout(context) {
    val backdrop = BackdropView(context)
    private val ui = FrameLayout(context)
    private val rows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val scroll =
        ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(60))
            clipToPadding = false
            isFocusable = false
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
            addView(rows, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
    private val labels = mutableListOf<TextView>()
    private val menu = mutableListOf<TextView>()
    private val medium = resources.getFont(R.font.inter_medium)
    private val regular = resources.getFont(R.font.inter_regular)
    val clock = label("", 24f, regular).apply { textScaleX = 1.06f }
    val nowPlaying = NowPlayingView(context)
    var onSelect: (() -> Unit)? = null
    var onTouchRow: ((Int, Boolean) -> Unit)? = null

    init {
        isFocusableInTouchMode = true
        addView(backdrop, LayoutParams(-1, -1))
        addView(ui, LayoutParams(-1, -1))
        buildRows()
        ui.addView(
            scroll,
            LayoutParams(dp(if (allApps) 700 else 600), dp(389)).apply {
                leftMargin = dp(56)
                topMargin = dp(111)
            },
        )
        ui.addView(
            clock,
            LayoutParams(dp(180), dp(40), Gravity.TOP or Gravity.RIGHT).apply {
                rightMargin = dp(70)
                topMargin = dp(52)
            },
        )
        clock.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
        if (!allApps) {
            ui.addView(
                nowPlaying,
                LayoutParams(
                    dp(Geometry.PLAYER_WIDTH),
                    dp(Geometry.PLAYER_HEIGHT),
                    Gravity.TOP or Gravity.RIGHT,
                ).apply {
                    rightMargin = dp(Geometry.PLAYER_RIGHT)
                    topMargin = dp(Geometry.PLAYER_TOP)
                },
            )
        }
        if (!allApps) buildMenu()
        requestFocus()
    }

    private fun buildRows() {
        apps.forEachIndexed { index, app ->
            val row = FrameLayout(context)
            val text =
                label(app.label, if (allApps) 28f else 44f, medium).apply {
                    textScaleX = if (allApps) 1f else 1.06f
                    translationY = -resources.displayMetrics.density * .5f
                }
            val start = if (allApps) 14 else 64
            row.addView(text, LayoutParams(-2, -1).apply { leftMargin = dp(start) })
            if (app.glyph != 0 && !allApps) {
                row.addView(
                    ImageView(context).apply {
                        setImageResource(app.glyph)
                        val inset =
                            if (app.glyph == R.drawable.ic_youtube ||
                                app.glyph == R.drawable.ic_stremio
                            ) {
                                dp(6)
                            } else {
                                0
                            }
                        setPadding(inset, 0, inset, 0)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                    },
                    LayoutParams(dp(40), dp(30), Gravity.CENTER_VERTICAL).apply { leftMargin = dp(14) },
                )
            }
            row.setOnClickListener {
                onTouchRow?.invoke(index, false)
                onSelect?.invoke()
            }
            rows.addView(row, LinearLayout.LayoutParams(-1, dp(64)))
            labels += text
        }
        rows.setPadding(0, 0, 0, dp(60))
        if (apps.isEmpty()) {
            ui.addView(
                label(context.getString(R.string.no_favorites), 18f, regular),
                LayoutParams(dp(500), dp(64)).apply {
                    leftMargin =
                        dp(70)
                    ; topMargin = dp(111)
                },
            )
        }
    }

    private fun buildMenu() {
        listOf(R.string.art_mode, R.string.all_apps, R.string.settings).forEachIndexed { index, id ->
            val text =
                label(context.getString(id), 14.5f, regular).apply {
                    gravity =
                        Gravity.RIGHT or Gravity.CENTER_VERTICAL
                }
            ui.addView(
                text,
                LayoutParams(dp(150), dp(32), Gravity.TOP or Gravity.RIGHT).apply {
                    rightMargin = dp(70)
                    topMargin =
                        dp(388 + index * 32)
                },
            )
            text.setOnClickListener {
                onTouchRow?.invoke(index, true)
                onSelect?.invoke()
            }
            menu += text
        }
    }

    private fun label(
        value: String,
        size: Float,
        font: Typeface,
    ) = TextView(context).apply {
        text = value
        textSize = size
        typeface = font
        setTextColor(0xFFF3EFE9.toInt())
        includeFontPadding = false
        gravity = Gravity.CENTER_VERTICAL
        isSingleLine = true
    }

    fun render(
        state: LauncherState,
        animate: Boolean = true,
    ) {
        ui.animate().cancel()
        val targetAlpha = if (state.art) 0f else 1f
        if (animate && ui.alpha != targetAlpha) {
            ui
                .animate()
                .alpha(targetAlpha)
                .setDuration(180)
                .setUpdateListener {
                    backdrop.uiAlpha = ui.alpha
                }.start()
        } else {
            ui.alpha = targetAlpha
            backdrop.uiAlpha = targetAlpha
        }
        ui.importantForAccessibility =
            if (state.art) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO
        labels.forEachIndexed { index, label ->
            label.alpha = if (state.player < 0 && state.menu < 0 && state.row == index) 1f else .88f
        }
        nowPlaying.render(state.playbackActions.getOrNull(state.player))
        backdrop.showFocus = state.player < 0
        backdrop.showPlayer = state.hasPlayback
        menu.forEachIndexed { index, label ->
            label.alpha = if (state.menu == index) 1f else .85f
            label.translationY = dp(Geometry.menuTop(state.hasPlayback) - 388).toFloat()
        }
        val targetScroll =
            dp(Geometry.scrollFor(state.row, scroll.scrollY / resources.displayMetrics.density, apps.size).toInt())
        scroll.scrollTo(0, targetScroll)
        labels.forEachIndexed { index, _ ->
            val center = index * 64f + 32f - targetScroll / resources.displayMetrics.density
            val edge = ((Geometry.VIEWPORT - center) / Geometry.FADE).coerceIn(0f, 1f)
            rows.getChildAt(index).alpha = if (state.menu < 0 && state.row == index) 1f else edge
        }
        contentDescription = describe(state)
        if (!state.art && state.player < 0) updatePill(state, targetScroll, animate)
    }

    private fun describe(state: LauncherState): String =
        when {
            state.art -> "Art Mode"
            state.player >= 0 -> "Spotify: ${nowPlaying.describe(state.playbackActions[state.player])}"
            state.menu >= 0 -> "Menu: ${menu[state.menu].text}"
            else -> "App: ${apps.getOrNull(state.row)?.label.orEmpty()}"
        }

    private fun updatePill(
        state: LauncherState,
        targetScroll: Int,
        animate: Boolean,
    ) {
        val density = resources.displayMetrics.density
        val box =
            if (state.menu >= 0) {
                val label = menu[state.menu]
                val width = label.paint.measureText(label.text.toString())
                RectF(
                    widthPixels() - dp(70) - width - dp(14),
                    dp(Geometry.menuTop(state.hasPlayback) + state.menu * 32).toFloat(),
                    (widthPixels() - dp(56)).toFloat(),
                    dp(Geometry.menuTop(state.hasPlayback) + 32 + state.menu * 32).toFloat(),
                )
            } else {
                val label = labels.getOrNull(state.row) ?: return
                val textWidth = label.paint.measureText(label.text.toString()) / density
                val width = if (allApps) 14 + textWidth + 28 else Geometry.pillWidth(textWidth)
                RectF(
                    dp(56).toFloat(),
                    dp(111 + state.row * 64).toFloat() - targetScroll,
                    dp(56) + width * density,
                    dp(176 + state.row * 64).toFloat() - targetScroll,
                )
            }
        backdrop.focus(box, animate)
    }

    private fun widthPixels() = if (width > 0) width else resources.displayMetrics.widthPixels

    fun visibleRowCenter(state: LauncherState) =
        143f + state.row * 64f - scroll.scrollY / resources.displayMetrics.density

    fun pauseAnimations() {
        ui.animate().cancel()
        backdrop.pauseAnimations()
    }

    fun clear() {
        pauseAnimations()
        nowPlaying.show(null)
        backdrop.clear()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
