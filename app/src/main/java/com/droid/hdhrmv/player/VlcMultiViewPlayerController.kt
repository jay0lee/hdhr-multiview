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
            "--network-caching=1000",
            "--live-caching=1000",
            "--audio-time-stretch",
            "--audio-resampler=soxr",
            "--deinterlace=1",
            "--deinterlace-mode=blend",
            "--aout=android_audiotrack",
            "--no-audio-passthrough",
            "--stereo-mode=1",
            "--no-mediacodec-audio",
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
                            Log.e(TAG, "Slot $slotIndex VLC Event: EncounteredError")
                            onPlaybackStateChanged(slotIndex, false, false, "Playback error")
                        }
                        MediaPlayer.Event.Stopped -> {
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
        val currentTrack = player.audioTrack
        val tracks = player.audioTracks
        Log.i(TAG, "Slot $slotIndex Audio state: currentTrack=$currentTrack, available=${tracks?.joinToString { "[${it.id}:${it.name}]" }}")
        if (currentTrack == -1 && !tracks.isNullOrEmpty()) {
            val validTrack = tracks.firstOrNull { it.id > 0 } ?: tracks.firstOrNull { it.id != -1 }
            if (validTrack != null) {
                player.audioTrack = validTrack.id
                Log.i(TAG, "Slot $slotIndex auto-selected audio track ${validTrack.id}: ${validTrack.name}")
            }
        }
    }

    private fun safelyAttach(slotIndex: Int) {
        val player = mediaPlayers[slotIndex] ?: return
        val layout = activeLayouts[slotIndex] ?: return
        if (!player.vlcVout.areViewsAttached()) {
            try {
                // Use SurfaceView (4th arg false) for zero-copy hardware video overlay plane rendering (VPU to display)
                player.attachViews(layout, null, false, false)
                Log.d(TAG, "Slot $slotIndex attached views to VLC layout (SurfaceView Hardware Overlay)")
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
        if (activeUrls[slotIndex] == streamUrl && mediaPlayers[slotIndex] != null) {
            return
        }
        activeUrls[slotIndex] = streamUrl
        val player = getOrCreatePlayer(slotIndex)

        try {
            val media = Media(libVlc, Uri.parse(streamUrl)).apply {
                // Force MediaCodec hardware acceleration for video (second arg = true forces HW decoding for MPEG-2)
                setHWDecoderEnabled(true, true)
                // Disable MediaCodec for audio so LibVLC's software decoder downmixes 5.1 surround sound to stereo PCM
                addOption(":no-mediacodec-audio")
                // Disable raw digital audio passthrough to prevent HDMI handshake silence on TVs
                addOption(":no-audio-passthrough")
                // Force stereo downmix
                addOption(":stereo-mode=1")
                addOption(":network-caching=1000")
                addOption(":live-caching=1000")
                addOption(":deinterlace=1")
                addOption(":deinterlace-mode=blend")
            }
            player.media = media
            media.release()

            safelyAttach(slotIndex)

            val isMuted = slotMutedStates[slotIndex]
            player.volume = if (isMuted) 0 else 100
            player.play()
            Log.i(TAG, "Slot $slotIndex playing: $streamUrl (initial volume=${player.volume}, muted=$isMuted, HW=forced)")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing slot $slotIndex: ${e.message}", e)
            onPlaybackStateChanged(slotIndex, false, false, e.message)
        }
    }

    override fun getDecoderBadge(slotIndex: Int): String {
        if (slotIndex !in 0..3) return "MediaCodec HW"
        val player = mediaPlayers[slotIndex] ?: return "MediaCodec HW"
        val track = player.currentVideoTrack
        if (track != null && track.width > 0) {
            val res = if (track.height >= 1000) "1080i" else if (track.height in 700..750) "720p" else "${track.width}x${track.height}"
            val codecName = when {
                track.codec?.contains("mp2", true) == true -> "MPEG-2"
                track.codec?.contains("h264", true) == true -> "H.264"
                track.codec?.contains("hevc", true) == true || track.codec?.contains("h265", true) == true -> "HEVC"
                else -> track.codec?.uppercase() ?: "HW"
            }
            return "HW • MediaCodec ($codecName $res)"
        }
        return "HW • MediaCodec"
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
        if (!isMuted) {
            ensureAudioTrackSelected(slotIndex, player)
        }
        Log.d(TAG, "Slot $slotIndex setMuted: $isMuted (player.volume=${player.volume})")
    }

    override fun getAudioTracks(slotIndex: Int): List<Pair<Int, String>> {
        if (slotIndex !in 0..3) return emptyList()
        val player = mediaPlayers[slotIndex] ?: return emptyList()
        return player.audioTracks?.filter { it.id > 0 }?.map { it.id to it.name } ?: emptyList()
    }

    override fun getSelectedAudioTrack(slotIndex: Int): Int {
        if (slotIndex !in 0..3) return -1
        return mediaPlayers[slotIndex]?.audioTrack ?: -1
    }

    override fun selectAudioTrack(slotIndex: Int, trackId: Int) {
        if (slotIndex !in 0..3) return
        val player = mediaPlayers[slotIndex] ?: return
        player.audioTrack = trackId
        Log.i(TAG, "Slot $slotIndex manually selected audio track $trackId")
    }

    override fun cycleAudioTrack(slotIndex: Int): String? {
        if (slotIndex !in 0..3) return null
        val player = mediaPlayers[slotIndex] ?: return null
        val tracks = player.audioTracks?.filter { it.id > 0 } ?: return null
        if (tracks.isEmpty()) return null
        val current = player.audioTrack
        val currentIndex = tracks.indexOfFirst { it.id == current }
        val nextIndex = if (currentIndex < 0 || currentIndex >= tracks.size - 1) 0 else currentIndex + 1
        val nextTrack = tracks[nextIndex]
        player.audioTrack = nextTrack.id
        Log.i(TAG, "Slot $slotIndex cycled audio track to ${nextTrack.id} (${nextTrack.name})")
        return nextTrack.name
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
