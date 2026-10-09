package com.droid.hdhrmv.player

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.TimestampAdjuster
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import org.videolan.libvlc.util.VLCVideoLayout

interface MultiViewPlayerController {
    fun play(slotIndex: Int, streamUrl: String)
    fun playChannel(slotIndex: Int, channel: com.droid.hdhrmv.model.Channel) {
        play(slotIndex, channel.streamUrl)
    }
    fun stop(slotIndex: Int)
    fun pause(slotIndex: Int) {}
    fun resume(slotIndex: Int) {}
    fun togglePause(slotIndex: Int) {}
    fun setMuted(slotIndex: Int, isMuted: Boolean)
    fun attachVideoLayout(slotIndex: Int, layout: VLCVideoLayout) {}
    fun detachVideoLayout(slotIndex: Int) {}
    fun detachVideoLayout(slotIndex: Int, layout: VLCVideoLayout?) { detachVideoLayout(slotIndex) }
    @Composable
    fun VideoView(slotIndex: Int, modifier: Modifier)
    fun release()
    fun getPlayer(slotIndex: Int): ExoPlayer? = null
    fun getDecoderBadge(slotIndex: Int): String = "HW • MediaCodec"
    fun getAudioTracks(slotIndex: Int): List<Pair<Int, String>> = emptyList()
    fun getSelectedAudioTrack(slotIndex: Int): Int = -1
    fun selectAudioTrack(slotIndex: Int, trackId: Int) {}
    fun cycleAudioTrack(slotIndex: Int): String? = null
    fun getDecoderAllocationMode(): DecoderAllocationMode = DecoderAllocationMode.HYBRID_2_HW
    fun setDecoderAllocationMode(mode: DecoderAllocationMode) {}
}

enum class DecoderAllocationMode(val title: String, val description: String) {
    HYBRID_2_HW("Hybrid (2 HW + 2 SW)", "Slots 1-2 Hardware, Slots 3-4 Software (Recommended for 4 Streams)"),
    SINGLE_HW("Single HW (1 HW + 3 SW)", "Slot 1 Hardware, Slots 2-4 Software (Lowest VPU usage)"),
    ALL_HW("All Hardware (4 HW)", "All slots attempt MediaCodec hardware acceleration"),
    ALL_SW("All Software (4 SW)", "All slots use multi-threaded CPU software decoding")
}

@OptIn(UnstableApi::class)
class ExoMultiViewPlayerController(
    private val context: Context,
    private val onPlaybackStateChanged: (slotIndex: Int, isPlaying: Boolean, isBuffering: Boolean, error: String?) -> Unit = { _, _, _, _ -> }
) : MultiViewPlayerController {

    companion object {
        private const val TAG = "HDHR_Player"
    }

    private val players = arrayOfNulls<ExoPlayer>(4)

    // Low latency load control tuned for live broadcast streams
    private val loadControl by lazy {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 1500,
                /* maxBufferMs = */ 3000,
                /* bufferForPlaybackMs = */ 500,
                /* bufferForPlaybackAfterRebufferMs = */ 1000
            )
            .build()
    }

    // Custom MediaCodecSelector providing explicit fallback for MPEG-2
    private val customMediaCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        var decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        if (decoders.isEmpty() && (mimeType == MimeTypes.VIDEO_MPEG2 || mimeType == "video/mp2v")) {
            Log.i(TAG, "Standard decoder lookup empty for $mimeType, registering c2.android.mpeg2.decoder fallback")
            decoders = listOfNotNull(
                MediaCodecInfo.newInstance(
                    /* name = */ "c2.android.mpeg2.decoder",
                    /* mimeType = */ MimeTypes.VIDEO_MPEG2,
                    /* containerMimeType = */ MimeTypes.VIDEO_MPEG2,
                    /* capabilities = */ null,
                    /* hardwareAccelerated = */ false,
                    /* softwareOnly = */ true,
                    /* vendor = */ false,
                    /* forceDisableAdaptivePlayback = */ false,
                    /* forceSecure = */ false
                )
            )
        }
        decoders
    }

    // Direct ExtractorsFactory placing TsExtractor FIRST to bypass HTTP Content-Type: video/mpeg PsExtractor trap
    private val customExtractorsFactory = ExtractorsFactory {
        arrayOf(
            TsExtractor(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
                TimestampAdjuster(0),
                DefaultTsPayloadReaderFactory()
            ),
            FragmentedMp4Extractor(),
            Mp4Extractor()
        )
    }

    override fun getPlayer(slotIndex: Int): ExoPlayer? {
        if (slotIndex !in 0..3) return null
        if (players[slotIndex] == null) {
            players[slotIndex] = createPlayerForSlot(slotIndex)
        }
        return players[slotIndex]
    }

    private fun createPlayerForSlot(slotIndex: Int): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(customMediaCodecSelector)

        val mediaSourceFactory = DefaultMediaSourceFactory(context, customExtractorsFactory)

        // Don't fail the entire stream if phone hardware lacks AC-3 audio decoder
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                .setExceedRendererCapabilitiesIfNecessary(true)
                .build()
        }

        val player = ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()

        player.addAnalyticsListener(EventLogger("HDHR_Slot_$slotIndex"))

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = (playbackState == Player.STATE_BUFFERING)
                val isPlaying = (playbackState == Player.STATE_READY && player.playWhenReady)
                Log.d(TAG, "Slot $slotIndex state changed: $playbackState (playing=$isPlaying, buffering=$isBuffering)")
                onPlaybackStateChanged(slotIndex, isPlaying, isBuffering, null)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val isBuffering = (player.playbackState == Player.STATE_BUFFERING)
                Log.d(TAG, "Slot $slotIndex isPlaying: $isPlaying")
                onPlaybackStateChanged(slotIndex, isPlaying, isBuffering, null)
            }

            override fun onTracksChanged(tracks: Tracks) {
                for (group in tracks.groups) {
                    val typeName = when (group.type) {
                        C.TRACK_TYPE_VIDEO -> "VIDEO"
                        C.TRACK_TYPE_AUDIO -> "AUDIO"
                        else -> "OTHER"
                    }
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        Log.i(TAG, "Slot $slotIndex $typeName track: sampleMimeType=${format.sampleMimeType} codecs=${format.codecs} supported=${group.isTrackSupported(i)} selected=${group.isTrackSelected(i)}")
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Slot $slotIndex player error: ${error.errorCodeName} - ${error.message}", error)
                onPlaybackStateChanged(slotIndex, false, false, error.message)
            }
        })

        // Default muted for non-zero slots
        player.volume = if (slotIndex == 0) 1.0f else 0.0f
        return player
    }

    override fun play(slotIndex: Int, streamUrl: String) {
        val player = getPlayer(slotIndex) ?: return
        Log.i(TAG, "Slot $slotIndex playing: $streamUrl")
        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMimeType(MimeTypes.VIDEO_MP2T)
            .build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    override fun stop(slotIndex: Int) {
        Log.i(TAG, "Slot $slotIndex stopped")
        players[slotIndex]?.stop()
        players[slotIndex]?.clearMediaItems()
        onPlaybackStateChanged(slotIndex, false, false, null)
    }

    override fun setMuted(slotIndex: Int, isMuted: Boolean) {
        players[slotIndex]?.volume = if (isMuted) 0.0f else 1.0f
    }

    override fun pause(slotIndex: Int) {
        if (slotIndex in 0..3) {
            players[slotIndex]?.pause()
        }
    }

    @Composable
    override fun VideoView(slotIndex: Int, modifier: Modifier) {
        val player = getPlayer(slotIndex)
        if (player != null) {
            AndroidView(
                modifier = modifier,
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        this.useController = false
                        this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { playerView ->
                    playerView.player = player
                }
            )
        }
    }

    override fun release() {
        for (i in 0..3) {
            players[i]?.release()
            players[i] = null
        }
    }
}
