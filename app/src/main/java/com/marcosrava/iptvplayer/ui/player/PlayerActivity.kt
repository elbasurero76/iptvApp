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

    // MediaItem guardado a nivel de clase para dárselo al CastPlayer
    private var currentMediaItem: MediaItem? = null

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

        // Botón Cast
        val castButton = findViewById<androidx.mediarouter.app.MediaRouteButton>(R.id.media_route_button)
        CastButtonFactory.setUpMediaRouteButton(applicationContext, castButton)

        // Obtener CastContext (puede fallar en devices sin Play Services)
        try {
            castContext = CastContext.getSharedInstance(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: ""
        val channelUrl  = intent.getStringExtra(EXTRA_CHANNEL_URL)  ?: ""
        val channelLogo = intent.getStringExtra(EXTRA_CHANNEL_LOGO)

        initPlayer(channelUrl, channelName, channelLogo)
    }

    // ─── Player init ──────────────────────────────────────────────────────────

    private fun buildMediaItem(url: String, name: String, logoUrl: String?): MediaItem {
        val mimeType = when {
            url.contains(".m3u8", ignoreCase = true) || url.contains("hls", ignoreCase = true) ->
                MimeTypes.APPLICATION_M3U8
            url.contains(".ts", ignoreCase = true) ->
                MimeTypes.VIDEO_MP2T
            else -> MimeTypes.APPLICATION_M3U8
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
        // 1. Construir y guardar el MediaItem
        currentMediaItem = buildMediaItem(url, name, logoUrl)

        // 2. ExoPlayer local — asignamos la vista SIEMPRE aquí, antes de nada
        exoPlayer = ExoPlayer.Builder(this).build().also { ep ->
            playerView.player = ep       // <-- siempre asignado, pase lo que pase con Cast
            currentPlayer = ep
            ep.setMediaItem(currentMediaItem!!)
            ep.prepare()
            ep.playWhenReady = true
        }

        // 3. CastPlayer — sólo SessionAvailabilityListener para evitar doble disparo
        try {
            castContext?.let { ctx ->
                castPlayer = CastPlayer(ctx).apply {
                    setSessionAvailabilityListener(object : SessionAvailabilityListener {
                        override fun onCastSessionAvailable()   { switchToCastPlayer()  }
                        override fun onCastSessionUnavailable() { switchToLocalPlayer() }
                    })
                }
                // Si ya hay sesión Cast activa al abrir el reproductor, cambiar a Cast
                if (castPlayer?.isCastSessionAvailable == true) {
                    switchToCastPlayer()
                }
            }
        } catch (e: Exception) {
            // Si Cast falla por cualquier motivo, seguimos con ExoPlayer local
            e.printStackTrace()
        }
    }

    // ─── Cambio de reproductor ────────────────────────────────────────────────

    private fun switchToCastPlayer() {
        // Guard: evita re-entrada si ya estamos en Cast
        if (currentPlayer == castPlayer) return
        val cp    = castPlayer       ?: return
        val item  = currentMediaItem ?: return

        val position = exoPlayer?.currentPosition ?: 0L

        // Detener ExoPlayer y desvincularlo de la vista
        exoPlayer?.pause()
        playerView.player = null

        // Cargar el stream en el CastPlayer y reproducir
        cp.setMediaItem(item)
        cp.prepare()
        cp.seekTo(position)
        cp.playWhenReady = true

        currentPlayer = cp
        viewModel.setCasting(true)
    }

    private fun switchToLocalPlayer() {
        // Guard: evita re-entrada si ya estamos en local
        if (currentPlayer == exoPlayer) return
        val ep = exoPlayer ?: return

        val position = castPlayer?.currentPosition ?: 0L
        castPlayer?.stop()

        // Restaurar ExoPlayer en la vista
        playerView.player = ep
        ep.seekTo(position)
        ep.playWhenReady = true

        currentPlayer = ep
        viewModel.setCasting(false)
    }

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        // Reanudar sólo si estamos en local y no en PiP
        if (viewModel.isCasting.value != true) {
            exoPlayer?.play()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isInPictureInPictureMode && viewModel.isCasting.value != true) {
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        exoPlayer?.release()
        exoPlayer  = null
        castPlayer = null
        currentPlayer = null
    }

    // ─── PiP ─────────────────────────────────────────────────────────────────

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
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
