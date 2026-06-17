package com.marcosrava.iptvplayer.ui.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener
import com.marcosrava.iptvplayer.R
import com.marcosrava.iptvplayer.data.model.Channel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private var exoPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null
    private var currentPlayer: Player? = null
    private var castContext: CastContext? = null
    private lateinit var playerView: PlayerView

    // Guardamos el MediaItem para poder dárselo al CastPlayer al conectar
    private var currentMediaItem: MediaItem? = null

    private val castStateListener = CastStateListener { state ->
        when (state) {
            CastState.CONNECTED -> switchToCastPlayer()
            CastState.NOT_CONNECTED -> switchToLocalPlayer()
        }
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_CHANNEL_NAME = "channel_name"
        const val EXTRA_CHANNEL_URL = "channel_url"
        const val EXTRA_CHANNEL_LOGO = "channel_logo"
        const val EXTRA_USER_AGENT = "user_agent"

        fun createIntent(context: Context, channel: Channel): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_CHANNEL_ID, channel.id)
                putExtra(EXTRA_CHANNEL_NAME, channel.name)
                putExtra(EXTRA_CHANNEL_URL, channel.url)
                putExtra(EXTRA_CHANNEL_LOGO, channel.logoUrl)
                putExtra(EXTRA_USER_AGENT, channel.userAgent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)

        // Pantalla completa
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, playerView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Cast
        try {
            castContext = CastContext.getSharedInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
        val channelUrl = intent.getStringExtra(EXTRA_CHANNEL_URL) ?: ""
        val channelLogo = intent.getStringExtra(EXTRA_CHANNEL_LOGO)

        initPlayer(channelUrl, channelName, channelLogo)

        // Botón Cast
        val castButton = findViewById<androidx.mediarouter.app.MediaRouteButton>(R.id.media_route_button)
        CastButtonFactory.setUpMediaRouteButton(applicationContext, castButton)
    }

    private fun buildMediaItem(url: String, name: String, logoUrl: String?): MediaItem {
        val mimeType = when {
            url.contains(".m3u8", ignoreCase = true) || url.contains("hls", ignoreCase = true) ->
                MimeTypes.APPLICATION_M3U8
            url.contains(".ts", ignoreCase = true) ->
                MimeTypes.VIDEO_MP2T
            else -> MimeTypes.APPLICATION_M3U8  // Por defecto HLS para IPTV
        }
        return MediaItem.Builder()
            .setUri(url)
            .setMimeType(mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtworkUri(logoUrl?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()
    }

    private fun initPlayer(url: String, name: String, logoUrl: String?) {
        currentMediaItem = buildMediaItem(url, name, logoUrl)

        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            playerView.player = player
            player.setMediaItem(currentMediaItem!!)
            player.prepare()
            player.playWhenReady = true
        }

        // Cast Player
        castContext?.let { ctx ->
            castPlayer = CastPlayer(ctx).apply {
                setSessionAvailabilityListener(object : SessionAvailabilityListener {
                    override fun onCastSessionAvailable() {
                        switchToCastPlayer()
                    }

                    override fun onCastSessionUnavailable() {
                        switchToLocalPlayer()
                    }
                })
            }

            if (castPlayer?.isCastSessionAvailable == true) {
                switchToCastPlayer()
            } else {
                setCurrentPlayer(exoPlayer!!)
            }
        } ?: run {
            setCurrentPlayer(exoPlayer!!)
        }

        castContext?.addCastStateListener(castStateListener)
    }

    private fun switchToCastPlayer() {
        val cp = castPlayer ?: return
        val mediaItem = currentMediaItem ?: return

        // Guardar posición del reproductor local
        val position = exoPlayer?.currentPosition ?: 0L

        // Dar el MediaItem al CastPlayer ANTES de reproducir
        cp.setMediaItem(mediaItem, position)
        cp.prepare()
        cp.playWhenReady = true

        playerView.player = null  // Liberar la vista del ExoPlayer
        exoPlayer?.pause()

        currentPlayer = cp
        viewModel.setCasting(true)
    }

    private fun switchToLocalPlayer() {
        val ep = exoPlayer ?: return

        // Recuperar posición del CastPlayer si es posible
        val position = castPlayer?.currentPosition ?: 0L
        castPlayer?.stop()

        playerView.player = ep
        ep.seekTo(position)
        ep.playWhenReady = true

        currentPlayer = ep
        viewModel.setCasting(false)
    }

    private fun setCurrentPlayer(player: Player) {
        currentPlayer = player
        if (player == exoPlayer) {
            playerView.player = player
        }
    }

    override fun onResume() {
        super.onResume()
        castContext?.addCastStateListener(castStateListener)
        if (viewModel.isCasting.value != true) {
            exoPlayer?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        castContext?.removeCastStateListener(castStateListener)
        if (!isInPictureInPictureMode && viewModel.isCasting.value != true) {
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        castContext?.removeCastStateListener(castStateListener)
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        exoPlayer?.release()
        exoPlayer = null
        castPlayer = null
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipMode()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && exoPlayer?.isPlaying == true) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView.useController = !isInPictureInPictureMode
    }
}
