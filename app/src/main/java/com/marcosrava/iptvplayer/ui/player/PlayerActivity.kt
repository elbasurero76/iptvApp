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
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    private var exoPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null
    private var currentPlayer: Player? = null
    private var castContext: CastContext? = null
    private lateinit var playerView: PlayerView

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

        val channelId = intent.getLongExtra(EXTRA_CHANNEL_ID, -1)
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
        val channelUrl = intent.getStringExtra(EXTRA_CHANNEL_URL) ?: ""
        val channelLogo = intent.getStringExtra(EXTRA_CHANNEL_LOGO)
        val userAgent = intent.getStringExtra(EXTRA_USER_AGENT)

        initPlayer(channelUrl, channelName, channelLogo, userAgent)

        // Botón Cast
        val castButton = findViewById<androidx.mediarouter.app.MediaRouteButton>(R.id.media_route_button)
        CastButtonFactory.setUpMediaRouteButton(applicationContext, castButton)
    }

    private fun initPlayer(url: String, name: String, logoUrl: String?, userAgent: String?) {
        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            playerView.player = player

            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(name)
                        .setArtworkUri(logoUrl?.let { android.net.Uri.parse(it) })
                        .build()
                )
                .apply {
                    // Detectar tipo MIME
                    if (url.contains(".m3u8") || url.contains("hls")) {
                        setMimeType(MimeTypes.APPLICATION_M3U8)
                    }
                }
                .build()

            player.setMediaItem(mediaItem)
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

            // Verificar si ya hay una sesión Cast activa
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
        castPlayer?.let {
            setCurrentPlayer(it)
            viewModel.setCasting(true)
        }
    }

    private fun switchToLocalPlayer() {
        exoPlayer?.let {
            setCurrentPlayer(it)
            viewModel.setCasting(false)
        }
    }

    private fun setCurrentPlayer(player: Player) {
        if (currentPlayer == player) return

        // Guardar posición
        val playbackPosition = currentPlayer?.currentPosition ?: 0L
        val playWhenReady = currentPlayer?.playWhenReady ?: true

        currentPlayer?.stop()
        currentPlayer = player

        if (player == exoPlayer) {
            playerView.player = player
        }

        player.playWhenReady = playWhenReady
        player.seekTo(playbackPosition)
        player.play()
    }

    override fun onResume() {
        super.onResume()
        castContext?.addCastStateListener(castStateListener)
        exoPlayer?.play()
    }

    override fun onPause() {
        super.onPause()
        castContext?.removeCastStateListener(castStateListener)
        if (!isInPictureInPictureMode) {
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        exoPlayer?.release()
        exoPlayer = null
        castPlayer = null
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Entrar automáticamente en PiP al salir de la app
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
