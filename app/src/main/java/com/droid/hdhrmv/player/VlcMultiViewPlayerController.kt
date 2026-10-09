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

    private val hasHardwareMpeg2Decoder: Boolean by lazy {
        try {
            val codecList = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
            codecList.codecInfos.any { info ->
                !info.isEncoder &&
                info.supportedTypes.any { it.equals("video/mpeg2", ignoreCase = true) } &&
                !info.isSoftwareOnly
            }
        } catch (_: Exception) {
            false
        }
    }

    private val libVlc: LibVLC by lazy {
        val options = arrayListOf(
            "--network-caching=1500",
            "--live-caching=1500",
            "--audio-time-stretch",
            "--deinterlace=1",
            "--deinterlace-mode=blend",
            "--aout=android_audiotrack",
            "-v"
        )
        LibVLC(context.applicationContext, options)
    }

    private val mediaPlayers = arrayOfNulls<MediaPlayer>(4)
    private val activeUrls = arrayOfNulls<String>(4)
    private val activeLayouts = arrayOfNulls<VLCVideoLayout>(4)

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
                            ensureAudioTrackSelected(slotIndex, this)
                            Log.i(TAG, "Slot $slotIndex VLC Event: Playing (volume=$volume, muted=$isMuted, audioTrack=$audioTrack, decoder=${getDecoderBadge(slotIndex)})")
                            onPlaybackStateChanged(slotIndex, true, false, null)
                        }
                        MediaPlayer.Event.Buffering -> {
                            val buffering = event.buffering < 100f
                            Log.d(TAG, "Slot $slotIndex VLC Event: Buffering ${event.buffering}%")
                            onPlaybackStateChanged(slotIndex, false, buffering, null)
                        }
                        MediaPlayer.Event.EncounteredError -> {
                            val failedUrl = activeUrls[slotIndex]
                            activeUrls[slotIndex] = null
                            Log.e(TAG, "Slot $slotIndex VLC Event: EncounteredError for url: $failedUrl")
                            onPlaybackStateChanged(slotIndex, false, false, "Playback error")
                        }
                        MediaPlayer.Event.Stopped -> {
                            activeUrls[slotIndex] = null
                            Log.d(TAG, "Slot $slotIndex VLC Event: Stopped")
                            onPlaybackStateChanged(slotIndex, false, false, null)
                        }
                        MediaPlayer.Event.ESAdded -> {
                            if (event.esChangedType == 1) { // 1 == IMedia.Track.Type.Audio
                                Log.i(TAG, "Slot $slotIndex VLC Event: Audio track added id=${event.esChangedID}")
                                ensureAudioTrackSelected(slotIndex, this)
                            }
                        }
                        MediaPlayer.Event.ESSelected -> {
                            if (event.esChangedType == 1) {
                                Log.i(TAG, "Slot $slotIndex VLC Event: Audio track selected id=${event.esChangedID}")
                            }
                        }
                    }
                }
            }
            mediaPlayers[slotIndex] = player
        }
        return player
    }

    private fun ensureAudioTrackSelected(slotIndex: Int, player: MediaPlayer) {
        try {
            val tracks = try { player.audioTracks } catch (_: Exception) { null }
            val currentTrack = try { player.audioTrack } catch (_: Exception) { -1 }
            val validTracks = tracks?.filterNotNull()?.filter { it.id > 0 } ?: emptyList()

            Log.d(TAG, "Slot $slotIndex Audio state: currentTrack=$currentTrack, available=${validTracks.joinToString { "[${it.id}:${it.name ?: "Track"}]" }}")
            if (currentTrack <= 0 && validTracks.isNotEmpty()) {
                val validTrack = validTracks.first()
                player.audioTrack = validTrack.id
                Log.i(TAG, "Slot $slotIndex auto-selected audio track ${validTrack.id}: ${validTrack.name ?: "Track"}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Slot $slotIndex non-fatal audio track selection: ${e.message}")
        }
    }

    @Synchronized
    private fun safelyAttach(slotIndex: Int) {
        val player = mediaPlayers[slotIndex] ?: return
        val layout = activeLayouts[slotIndex] ?: return
        try {
            if (!player.vlcVout.areViewsAttached()) {
                // Use SurfaceView (4th arg false) for zero-copy hardware video overlay plane rendering (VPU to display)
                player.attachViews(layout, null, false, false)
                Log.d(TAG, "Slot $slotIndex attached views to VLC layout (SurfaceView Hardware Overlay)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Slot $slotIndex non-fatal view attach: ${e.message}")
        }
    }

    @Synchronized
    private fun safelyDetach(slotIndex: Int) {
        val player = mediaPlayers[slotIndex] ?: return
        try {
            if (player.vlcVout.areViewsAttached()) {
                player.detachViews()
                Log.d(TAG, "Slot $slotIndex detached views from VLC layout")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Slot $slotIndex non-fatal view detach: ${e.message}")
        }
    }

    override fun playChannel(slotIndex: Int, channel: com.droid.hdhrmv.model.Channel) {
        if (slotIndex !in 0..3) return
        val targetUrl = AudioTranscodeManager.getStreamUrlForSlot(context, slotIndex, channel)
        play(slotIndex, targetUrl)
    }

    override fun play(slotIndex: Int, streamUrl: String) {
        if (slotIndex !in 0..3) return
        if (streamUrl.isBlank()) {
            stop(slotIndex)
            return
        }
        if (activeUrls[slotIndex] == streamUrl && mediaPlayers[slotIndex] != null) {
            return
        }
        activeUrls[slotIndex] = streamUrl
        val player = getOrCreatePlayer(slotIndex)

        try {
            if (player.isPlaying) {
                player.stop()
            }

            val mediaUri = Uri.parse(streamUrl)
            val media = Media(libVlc, mediaUri).apply {
                // MediaCodec hardware acceleration with graceful software fallback (second arg = false allows SW fallback)
                setHWDecoderEnabled(true, false)
                addOption(":network-caching=1500")
                addOption(":live-caching=1500")
                addOption(":clock-jitter=0")
                addOption(":clock-synchro=0")
                addOption(":deinterlace=1")
                addOption(":deinterlace-mode=blend")
            }
            player.media = media
            media.release()

            safelyAttach(slotIndex)

            val isMuted = slotMutedStates[slotIndex]
            player.volume = if (isMuted) 0 else 100
            player.play()
            Log.i(TAG, "Slot $slotIndex started playback for URL: $streamUrl (volume=${player.volume}, muted=$isMuted)")
        } catch (e: Exception) {
            activeUrls[slotIndex] = null
            Log.e(TAG, "Error playing slot $slotIndex: ${e.message}", e)
            onPlaybackStateChanged(slotIndex, false, false, e.message)
        }
    }

    override fun getDecoderBadge(slotIndex: Int): String {
        if (slotIndex !in 0..3) return "Ready"
        val player = mediaPlayers[slotIndex] ?: return "Ready"
        return try {
            val track = player.currentVideoTrack
            if (track != null && track.width > 0) {
                val res = if (track.height >= 1000) "1080i" else if (track.height in 700..750) "720p" else "${track.width}x${track.height}"
                val isMpeg2 = track.codec?.contains("mp2", true) == true
                val isH264 = track.codec?.contains("h264", true) == true
                val isHevc = track.codec?.contains("hevc", true) == true || track.codec?.contains("h265", true) == true
                val codecName = when {
                    isMpeg2 -> "MPEG-2"
                    isH264 -> "H.264"
                    isHevc -> "HEVC"
                    else -> track.codec?.uppercase() ?: "Video"
                }
                val isHw = if (isMpeg2) hasHardwareMpeg2Decoder else true
                val engineType = if (isHw) "HW • MediaCodec" else "SW • LibVLC"
                "$engineType ($codecName $res)"
            } else {
                if (player.isPlaying) "Playing" else "Buffering"
            }
        } catch (e: Exception) {
            "Ready"
        }
    }

    override fun stop(slotIndex: Int) {
        if (slotIndex !in 0..3) return
        AudioTranscodeManager.stopTranscode(slotIndex)
        activeUrls[slotIndex] = null
        try {
            mediaPlayers[slotIndex]?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                safelyDetach(slotIndex)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Slot $slotIndex non-fatal stop error: ${e.message}")
        }
        Log.i(TAG, "Slot $slotIndex stopped")
    }

    override fun pause(slotIndex: Int) {
        if (slotIndex in 0..3) {
            try {
                mediaPlayers[slotIndex]?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Slot $slotIndex non-fatal pause error: ${e.message}")
            }
        }
    }

    override fun resume(slotIndex: Int) {
        if (slotIndex in 0..3) {
            try {
                mediaPlayers[slotIndex]?.let { player ->
                    if (!player.isPlaying) {
                        player.play()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Slot $slotIndex non-fatal resume error: ${e.message}")
            }
        }
    }

    override fun togglePause(slotIndex: Int) {
        if (slotIndex in 0..3) {
            try {
                mediaPlayers[slotIndex]?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Slot $slotIndex non-fatal togglePause error: ${e.message}")
            }
        }
    }

    override fun setMuted(slotIndex: Int, isMuted: Boolean) {
        if (slotIndex !in 0..3) return
        slotMutedStates[slotIndex] = isMuted
        val player = mediaPlayers[slotIndex] ?: return
        try {
            player.volume = if (isMuted) 0 else 100
            if (!isMuted) {
                ensureAudioTrackSelected(slotIndex, player)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Slot $slotIndex non-fatal setMuted error: ${e.message}")
        }
        Log.d(TAG, "Slot $slotIndex setMuted: $isMuted (player.volume=${player.volume})")
    }

    override fun getAudioTracks(slotIndex: Int): List<Pair<Int, String>> {
        if (slotIndex !in 0..3) return emptyList()
        val player = mediaPlayers[slotIndex] ?: return emptyList()
        return try {
            player.audioTracks?.filterNotNull()?.filter { it.id > 0 }?.map { it.id to (it.name ?: "Track ${it.id}") } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun getSelectedAudioTrack(slotIndex: Int): Int {
        if (slotIndex !in 0..3) return -1
        return try {
            mediaPlayers[slotIndex]?.audioTrack ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    override fun selectAudioTrack(slotIndex: Int, trackId: Int) {
        if (slotIndex !in 0..3) return
        val player = mediaPlayers[slotIndex] ?: return
        try {
            player.audioTrack = trackId
            Log.i(TAG, "Slot $slotIndex manually selected audio track $trackId")
        } catch (e: Exception) {
            Log.w(TAG, "Slot $slotIndex error setting audio track $trackId: ${e.message}")
        }
    }

    override fun cycleAudioTrack(slotIndex: Int): String? {
        if (slotIndex !in 0..3) return null
        val player = mediaPlayers[slotIndex] ?: return null
        return try {
            val tracks = player.audioTracks?.filterNotNull()?.filter { it.id > 0 } ?: return null
            if (tracks.isEmpty()) return null
            val current = player.audioTrack
            val currentIndex = tracks.indexOfFirst { it.id == current }
            val nextIndex = if (currentIndex < 0 || currentIndex >= tracks.size - 1) 0 else currentIndex + 1
            val nextTrack = tracks[nextIndex]
            player.audioTrack = nextTrack.id
            Log.i(TAG, "Slot $slotIndex cycled audio track to ${nextTrack.id} (${nextTrack.name})")
            nextTrack.name ?: "Track ${nextTrack.id}"
        } catch (e: Exception) {
            Log.w(TAG, "Slot $slotIndex error cycling audio track: ${e.message}")
            null
        }
    }

    @Synchronized
    override fun attachVideoLayout(slotIndex: Int, layout: VLCVideoLayout) {
        if (slotIndex !in 0..3) return
        val currentLayout = activeLayouts[slotIndex]
        val player = mediaPlayers[slotIndex]
        if (currentLayout === layout && player?.vlcVout?.areViewsAttached() == true) {
            return
        }
        if (player != null && player.vlcVout.areViewsAttached()) {
            safelyDetach(slotIndex)
        }
        activeLayouts[slotIndex] = layout
        safelyAttach(slotIndex)
    }

    override fun detachVideoLayout(slotIndex: Int) {
        detachVideoLayout(slotIndex, null)
    }

    @Synchronized
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
