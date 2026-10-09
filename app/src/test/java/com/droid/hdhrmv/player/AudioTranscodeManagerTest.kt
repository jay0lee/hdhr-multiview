package com.droid.hdhrmv.player

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.droid.hdhrmv.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AudioTranscodeManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun standardAtsc1Channel_doesNotRequireTranscode() {
        val channel = Channel(
            guideNumber = "3.1",
            guideName = "KYW-TV",
            videoCodec = "MPEG2",
            audioCodec = "AC3",
            streamUrl = "http://10.1.0.4:5004/auto/v3.1"
        )
        assertFalse(AudioTranscodeManager.isTranscodeRequired(channel))
    }

    @Test
    fun aacOrMp2Channel_doesNotRequireTranscode() {
        val channelAac = Channel(
            guideNumber = "10.1",
            guideName = "NBC",
            videoCodec = "MPEG2",
            audioCodec = "AAC",
            streamUrl = "http://10.1.0.4:5004/auto/v10.1"
        )
        val channelMp2 = Channel(
            guideNumber = "12.1",
            guideName = "PBS",
            videoCodec = "MPEG2",
            audioCodec = "MP2",
            streamUrl = "http://10.1.0.4:5004/auto/v12.1"
        )
        assertFalse(AudioTranscodeManager.isTranscodeRequired(channelAac))
        assertFalse(AudioTranscodeManager.isTranscodeRequired(channelMp2))
    }

    @Test
    fun hevcChannel_requiresTranscode() {
        val hevcChannel = Channel(
            guideNumber = "103.1",
            guideName = "NEXTGEN TV",
            videoCodec = "HEVC",
            audioCodec = "AC3",
            streamUrl = "http://10.1.0.4:5004/auto/v103.1"
        )
        assertTrue(AudioTranscodeManager.isTranscodeRequired(hevcChannel))
    }

    @Test
    fun getStreamUrlForSlot_whenNoCustomBinary_returnsDirectStreamUrl() {
        val channel = Channel(
            guideNumber = "3.1",
            guideName = "KYW-TV",
            videoCodec = "MPEG2",
            audioCodec = "AC3",
            streamUrl = "http://10.1.0.4:5004/auto/v3.1"
        )
        val url = AudioTranscodeManager.getStreamUrlForSlot(context, 0, channel)
        assertEquals("http://10.1.0.4:5004/auto/v3.1", url)
    }

    @Test
    fun getActiveBinaryDescription_defaultReturnsSystemEngine() {
        val desc = AudioTranscodeManager.getActiveBinaryDescription(context)
        assertEquals("Default System Engine", desc)
        assertFalse(AudioTranscodeManager.isCustomBinaryActive(context))
    }
}
