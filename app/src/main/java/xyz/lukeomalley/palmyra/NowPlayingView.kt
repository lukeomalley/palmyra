package xyz.lukeomalley.palmyra

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView

/** A quiet, static card. Only metadata, playback and focus changes redraw it. */
@SuppressLint("ViewConstructor", "RtlHardcoded")
@Suppress("MagicNumber")
class NowPlayingView(
    context: Context,
) : FrameLayout(context) {
    private val regular = resources.getFont(R.font.inter_regular)
    private val medium = resources.getFont(R.font.inter_medium)
    private val artworkView =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = surface(0xFF33443E.toInt(), 0, 5)
            clipToOutline = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    private val songTitle = label(16f, medium)
    private val artistName = label(11f, regular).apply { alpha = .78f }
    private val openSpotify = FrameLayout(context)
    private val playPause = button()
    private val nextTrack =
        button().apply {
            setImageResource(R.drawable.ic_media_next)
            contentDescription = context.getString(R.string.next_track)
        }
    private val buttons =
        mapOf(
            LauncherState.Effect.OPEN_SPOTIFY to openSpotify,
            LauncherState.Effect.TOGGLE_PLAYBACK to playPause,
            LauncherState.Effect.NEXT_TRACK to nextTrack,
        )
    private var currentTrack: NowPlaying? = null
    private var availableActions = emptyList<LauncherState.Effect>()
    var onAction: ((Int) -> Unit)? = null

    init {
        visibility = GONE
        addView(artworkView, box(44, 44, 12, 12))
        addView(songTitle, box(154, 22, 68, 16))
        addView(artistName, box(154, 17, 68, 38))
        addView(openSpotify, box(230, 68, 0, 0))
        addView(playPause, box(32, 32, 240, 18))
        addView(nextTrack, box(32, 32, 278, 18))
        openSpotify.setOnClickListener { act(LauncherState.Effect.OPEN_SPOTIFY) }
        playPause.setOnClickListener { act(LauncherState.Effect.TOGGLE_PLAYBACK) }
        nextTrack.setOnClickListener { act(LauncherState.Effect.NEXT_TRACK) }
    }

    fun show(value: NowPlaying?) {
        if (currentTrack == value) return
        val previousArt = currentTrack?.artwork
        currentTrack = value
        visibility = if (value == null) GONE else VISIBLE
        availableActions = value?.actions.orEmpty()
        if (value == null) {
            artworkView.setImageDrawable(null)
            songTitle.text = ""
            artistName.text = ""
            return
        }
        val artwork = value.artwork
        if (artwork == null) {
            artworkView.setImageResource(R.drawable.ic_spotify)
            artworkView.setPadding(dp(9), dp(9), dp(9), dp(9))
        } else if (previousArt !== artwork) {
            artworkView.setPadding(0, 0, 0, 0)
            val size = minOf(artwork.width, artwork.height)
            val square =
                Bitmap.createBitmap(
                    artwork,
                    (artwork.width - size) / 2,
                    (artwork.height - size) / 2,
                    size,
                    size,
                )
            artworkView.setImageBitmap(Bitmap.createScaledBitmap(square, dp(44), dp(44), true))
        }
        songTitle.text = value.title
        artistName.text = value.artist
        openSpotify.contentDescription = "${value.title}, ${value.artist}. ${context.getString(R.string.open_spotify)}"
        playPause.setImageResource(if (value.playing) R.drawable.ic_media_pause else R.drawable.ic_media_play)
        playPause.contentDescription = context.getString(if (value.playing) R.string.pause else R.string.play)
        playPause.isEnabled = value.canToggle
        playPause.alpha = if (value.canToggle) 1f else .3f
        nextTrack.isEnabled = value.canNext
        nextTrack.alpha = if (value.canNext) 1f else .3f
    }

    fun render(selected: LauncherState.Effect?) {
        buttons.forEach { (action, button) ->
            val focused = selected == action
            if (button.isSelected != focused) {
                button.isSelected = focused
                button.background = if (focused) surface(0x20F3EFE9, 0xFFF3EFE9.toInt(), 16) else null
            }
        }
    }

    fun describe(action: LauncherState.Effect): CharSequence =
        buttons[action]?.contentDescription ?: openSpotify.contentDescription

    private fun act(effect: LauncherState.Effect) {
        val index = availableActions.indexOf(effect)
        if (index >= 0) onAction?.invoke(index)
    }

    private fun button() =
        ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(8), dp(8), dp(8), dp(8))
            isFocusable = false
        }

    private fun label(
        size: Float,
        font: Typeface,
    ) = TextView(context).apply {
        textSize = size
        typeface = font
        includeFontPadding = false
        setTextColor(0xFFF3EFE9.toInt())
        gravity = Gravity.CENTER_VERTICAL
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun surface(
        color: Int,
        stroke: Int,
        radius: Int,
    ) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun box(
        width: Int,
        height: Int,
        left: Int,
        top: Int,
    ) = LayoutParams(dp(width), dp(height)).apply {
        leftMargin = dp(left)
        topMargin = dp(top)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
