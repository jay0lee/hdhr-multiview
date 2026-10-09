package com.droid.hdhrmv.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class VlcMultiViewPlayerController(
    private val context: Context,
    private val onPlaybackStateChanged: (slotIndex: Int, isPlaying: Boolean, isBuffering: Boolean, error: String?) -> Unit = { _, _, _, _ -> }
) : MultiViewPlayerController {

    companion object {
        private const val TAG = "HDHR_VLC_Player"
    }

    private val slotMutedStates = BooleanArray(4) { it != 0 } // Slot 0 starts unmuted, others muted

    private val libVlc: LibVLC by lazy {
        val options = arrayListOf(
            "--aout=android_audiotrack",
            "--no-spdif",
            "--spdif=0",
            "--audio-time-stretch",
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--network-caching=500",
            "--live-caching=500"
        )
        LibVLC(context.applicationContext, options)
    }

    private val mediaPlayers = arrayOfNulls<MediaPlayer>(4)
    private val activeUrls = arrayOfNulls<String>(4)
    private val activeLayouts = arrayOfNulls<VLCVideoLayout>(4)

    private fun ensureAudioTrack(slotIndex: Int, player: MediaPlayer) {
        try {
            val tracks = player.audioTracks
            val currentTrack = player.audioTrack
            val validTracks = tracks?.filter { it.id > 0 } ?: emptyList()
            if (validTracks.isNotEmpty() && (currentTrack <= 0 || validTracks.none { it.id == currentTrack })) {
                val selected = validTracks.first()
                player.audioTrack = selected.id
                Log.i(TAG, "Slot $slotIndex selected valid audio track: ${selected.id} (${selected.name})")
            }
            val isMuted = slotMutedStates[slotIndex]
            player.volume = if (isMuted) 0 else 100
        } catch (e: Exception) {
            Log.w(TAG, "Slot $slotIndex error ensuring audio track: ${e.message}")
        }
    }

    private fun getOrCreatePlayer(slotIndex: Int): MediaPlayer {
        var player = mediaPlayers[slotIndex]
        if (player == null) {
            player = MediaPlayer(libVlc).apply {
                setAudioOutput("android_audiotrack")
                setAudioDigitalOutputEnabled(false)
                setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.Playing -> {
                            val isMuted = slotMutedStates[slotIndex]
                            volume = if (isMuted) 0 else 100
                            ensureAudioTrack(slotIndex, this)
                            Log.i(TAG, "Slot $slotIndex VLC Event: Playing (volume=$volume, muted=$isMuted, track=$audioTrack)")
                            onPlaybackStateChanged(slotIndex, true, false, null)
                        }
                        MediaPlayer.Event.ESAdded -> {
                            ensureAudioTrack(slotIndex, this)
                        }
                        MediaPlayer.Event.Buffering -> {
                            val buffering = event.buffering < 100f
                            Log.d(TAG, "Slot $slotIndex VLC Event: Buffering ${event.buffering}%")
                            onPlaybackStateChanged(slotIndex, false, buffering, null)
                        }
                        MediaPlayer.Event.EncounteredError -> {
                            Log.e(TAG, "Slot $slotIndex VLC Event: EncounteredError")
                            onPlaybackStateChanged(slotIndex, false, false, "Playback error")
                        }
                        MediaPlayer.Event.Stopped -> {
                            Log.d(TAG, "Slot $slotIndex VLC Event: Stopped")
                            onPlaybackStateChanged(slotIndex, false, false, null)
                        }
                    }
                }
            }
            mediaPlayers[slotIndex] = player
        }
        return player
    }

    private fun safelyAttach(slotIndex: Int) {
        val player = mediaPlayers[slotIndex] ?: return
        val layout = activeLayouts[slotIndex] ?: return
        if (!player.vlcVout.areViewsAttached()) {
            try {
                // Use TextureView (4th arg true) so video survives layout transitions, resizing & orientation changes
                player.attachViews(layout, null, false, true)
                Log.d(TAG, "Slot $slotIndex attached views to VLC layout (TextureView)")
            } catch (e: Exception) {
                Log.w(TAG, "Error attaching views for slot $slotIndex: ${e.message}")
            }
        }
    }

    private fun safelyDetach(slotIndex: Int) {
        val player = mediaPlayers[slotIndex] ?: return
        if (player.vlcVout.areViewsAttached()) {
            try {
                player.detachViews()
                Log.d(TAG, "Slot $slotIndex detached views from VLC layout")
            } catch (e: Exception) {
                Log.w(TAG, "Error detaching views for slot $slotIndex: ${e.message}")
            }
        }
    }

    override fun playChannel(slotIndex: Int, channel: com.droid.hdhrmv.model.Channel) {
        if (slotIndex !in 0..3) return
        val targetUrl = AudioTranscodeManager.getStreamUrlForSlot(context, slotIndex, channel)
        play(slotIndex, targetUrl)
    }

    override fun play(slotIndex: Int, streamUrl: String) {
        if (slotIndex !in 0..3) return
        if (activeUrls[slotIndex] == streamUrl && mediaPlayers[slotIndex]?.isPlaying == true) {
            return
        }
        activeUrls[slotIndex] = streamUrl
        val player = getOrCreatePlayer(slotIndex)

        try {
            val media = Media(libVlc, Uri.parse(streamUrl)).apply {
                setHWDecoderEnabled(true, false) // HW accelerated with SW fallback for MPEG2
                addOption(":network-caching=500")
                addOption(":live-caching=500")
                addOption(":clock-jitter=0")
                addOption(":clock-synchro=0")
                addOption(":aout=android_audiotrack")
                addOption(":no-spdif")
                addOption(":spdif=0")
            }
            player.media = media
            media.release()

            safelyAttach(slotIndex)

            val isMuted = slotMutedStates[slotIndex]
            player.volume = if (isMuted) 0 else 100
            player.play()
            Log.i(TAG, "Slot $slotIndex playing: $streamUrl (initial volume=${player.volume}, muted=$isMuted)")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing slot $slotIndex: ${e.message}", e)
            onPlaybackStateChanged(slotIndex, false, false, e.message)
        }
    }

    override fun stop(slotIndex: Int) {
        if (slotIndex !in 0..3) return
        AudioTranscodeManager.stopTranscode(slotIndex)
        activeUrls[slotIndex] = null
        mediaPlayers[slotIndex]?.let { player ->
            if (player.isPlaying) {
                player.stop()
            }
            safelyDetach(slotIndex)
        }
        Log.i(TAG, "Slot $slotIndex stopped")
    }

    override fun pause(slotIndex: Int) {
        if (slotIndex in 0..3) {
            mediaPlayers[slotIndex]?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                }
            }
        }
    }

    override fun resume(slotIndex: Int) {
        if (slotIndex in 0..3) {
            mediaPlayers[slotIndex]?.let { player ->
                if (!player.isPlaying) {
                    player.play()
                }
            }
        }
    }

    override fun togglePause(slotIndex: Int) {
        if (slotIndex in 0..3) {
            mediaPlayers[slotIndex]?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                } else {
                    player.play()
                }
            }
        }
    }

    override fun setMuted(slotIndex: Int, isMuted: Boolean) {
        if (slotIndex !in 0..3) return
        slotMutedStates[slotIndex] = isMuted
        val player = mediaPlayers[slotIndex] ?: return
        player.volume = if (isMuted) 0 else 100
        Log.d(TAG, "Slot $slotIndex setMuted: $isMuted (player.volume=${player.volume})")
    }

    override fun attachVideoLayout(slotIndex: Int, layout: VLCVideoLayout) {
        if (slotIndex !in 0..3) return
        val currentLayout = activeLayouts[slotIndex]
        val player = mediaPlayers[slotIndex]
        if (currentLayout === layout && player?.vlcVout?.areViewsAttached() == true) {
            return
        }
        if (player != null && player.vlcVout.areViewsAttached()) {
            try {
                player.detachViews()
            } catch (e: Exception) {
                Log.w(TAG, "Error detaching previous views for slot $slotIndex: ${e.message}")
            }
        }
        activeLayouts[slotIndex] = layout
        safelyAttach(slotIndex)
    }

    override fun detachVideoLayout(slotIndex: Int) {
        detachVideoLayout(slotIndex, null)
    }

    override fun detachVideoLayout(slotIndex: Int, layout: VLCVideoLayout?) {
        if (slotIndex !in 0..3) return
        if (layout == null || activeLayouts[slotIndex] === layout) {
            safelyDetach(slotIndex)
            activeLayouts[slotIndex] = null
        } else {
            Log.d(TAG, "Slot $slotIndex ignoring detach for obsolete layout")
        }
    }

    @Composable
    override fun VideoView(slotIndex: Int, modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    attachVideoLayout(slotIndex, layout)
                }
            },
            update = { layout ->
                attachVideoLayout(slotIndex, layout)
            },
            onRelease = { layout ->
                detachVideoLayout(slotIndex, layout)
            }
        )
    }

    override fun release() {
        AudioTranscodeManager.stopAll()
        for (i in 0..3) {
            stop(i)
            mediaPlayers[i]?.release()
            mediaPlayers[i] = null
        }
        libVlc.release()
        Log.i(TAG, "All VLC players released")
    }
}
