package com.droid.hdhrmv.player

import android.content.Context
import android.os.Build
import android.util.Log
import com.droid.hdhrmv.model.Channel
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

object AudioTranscodeManager {
    private const val TAG = "AudioTranscodeManager"
    private const val CUSTOM_BINARY_NAME = "custom_ffmpeg.so"
    private val activeServers = ConcurrentHashMap<Int, TranscodeSession>()

    private class TranscodeSession(
        val serverSocket: ServerSocket,
        val localPort: Int,
        @Volatile var activeProcess: Process? = null,
        @Volatile var clientSocket: Socket? = null,
        @Volatile var isRunning: Boolean = true
    )

    fun isTranscodeRequired(channel: Channel): Boolean {
        val videoCodec = channel.videoCodec?.uppercase() ?: ""
        if (videoCodec.contains("HEVC") || videoCodec.contains("H.265")) return true
        val audioCodec = channel.audioCodec?.uppercase() ?: ""
        if (audioCodec.isNotEmpty() && !audioCodec.contains("AC3") && !audioCodec.contains("A52") && !audioCodec.contains("MP2") && !audioCodec.contains("AAC")) {
            return true
        }
        return false
    }

    fun isCustomBinaryActive(context: Context): Boolean {
        val customBin = File(context.filesDir, CUSTOM_BINARY_NAME)
        return customBin.exists() && customBin.canExecute() && customBin.length() > 50_000L
    }

    fun getActiveBinaryDescription(context: Context): String {
        val customBin = File(context.filesDir, CUSTOM_BINARY_NAME)
        return if (customBin.exists() && customBin.canExecute() && customBin.length() > 50_000L) {
            val sizeMb = String.format("%.1f", customBin.length().toDouble() / (1024 * 1024))
            "Custom Engine ($sizeMb MB)"
        } else {
            "Default System Engine"
        }
    }

    fun installCustomBinary(context: Context, inputStream: InputStream): Boolean {
        return try {
            val destFile = File(context.filesDir, CUSTOM_BINARY_NAME)
            destFile.outputStream().use { out ->
                inputStream.copyTo(out)
            }
            destFile.setReadable(true, false)
            destFile.setExecutable(true, false)
            Log.i(TAG, "Custom engine installed successfully: ${destFile.absolutePath} (${destFile.length()} bytes)")
            stopAll()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed installing custom engine: ${e.message}", e)
            false
        }
    }

    fun resetToDefaultBinary(context: Context): Boolean {
        return try {
            val customBin = File(context.filesDir, CUSTOM_BINARY_NAME)
            if (customBin.exists()) {
                customBin.delete()
            }
            stopAll()
            Log.i(TAG, "Reset to default bundled engine")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed resetting engine: ${e.message}", e)
            false
        }
    }

    private fun getFfmpegBinary(context: Context): File {
        // 1. Check for user-supplied custom binary in filesDir
        val customBin = File(context.filesDir, CUSTOM_BINARY_NAME)
        if (customBin.exists() && customBin.canExecute() && customBin.length() > 50_000L) {
            return customBin
        }

        // 2. Try nativeLibraryDir (standard unpacked location from jniLibs)
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val nativeLib = File(nativeDir, "libffmpeg.so")
        if (nativeLib.exists() && nativeLib.canExecute()) {
            return nativeLib
        }

        // 3. Fallback extraction from assets
        val filesLib = File(context.filesDir, "libffmpeg.so")
        if (filesLib.exists() && filesLib.canExecute() && filesLib.length() > 100_000L) {
            return filesLib
        }

        try {
            val abi = when {
                Build.SUPPORTED_ABIS.any { it.contains("arm64") } -> "arm64-v8a"
                Build.SUPPORTED_ABIS.any { it.contains("x86_64") } -> "x86_64"
                Build.SUPPORTED_ABIS.any { it.contains("armeabi-v7a") || it.contains("armv7") } -> "armeabi-v7a"
                else -> Build.SUPPORTED_ABIS.firstOrNull() ?: "armeabi-v7a"
            }
            val assetPath = "bin/$abi/libffmpeg.so"
            context.assets.open(assetPath).use { input ->
                filesLib.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            filesLib.setExecutable(true, false)
            Log.i(TAG, "Extracted ffmpeg binary from assets: ${filesLib.absolutePath} (${filesLib.length()} bytes)")
            return filesLib
        } catch (e: Exception) {
            Log.w(TAG, "Failed extracting fallback ffmpeg from assets: ${e.message}")
        }

        return nativeLib
    }

    @Synchronized
    fun getStreamUrlForSlot(context: Context, slotIndex: Int, channel: Channel): String {
        if (!isCustomBinaryActive(context) || !isTranscodeRequired(channel)) {
            // Direct playback without transcoding
            stopTranscode(slotIndex)
            return channel.streamUrl
        }

        stopTranscode(slotIndex)

        val ffmpegBin = getFfmpegBinary(context)
        val serverSocket = ServerSocket(0, 5, java.net.InetAddress.getByName("127.0.0.1"))
        val port = serverSocket.localPort

        val session = TranscodeSession(serverSocket, port)
        activeServers[slotIndex] = session

        // Launch background server thread
        Thread({
            runTranscodeServer(session, ffmpegBin, channel.streamUrl, slotIndex)
        }, "StreamTranscode-Slot$slotIndex").apply { isDaemon = true }.start()

        val localUrl = "http://127.0.0.1:$port/live.ts"
        Log.i(TAG, "Slot $slotIndex transcode proxy initialized at $localUrl for ${channel.guideNumber} ${channel.guideName}")
        return localUrl
    }

    private fun runTranscodeServer(session: TranscodeSession, ffmpegBin: File, sourceUrl: String, slotIndex: Int) {
        try {
            while (session.isRunning && !session.serverSocket.isClosed) {
                val client = session.serverSocket.accept() ?: break
                session.clientSocket = client

                Thread({
                    handleClientConnection(session, client, ffmpegBin, sourceUrl, slotIndex)
                }, "StreamClient-Slot$slotIndex").start()
            }
        } catch (e: Exception) {
            if (session.isRunning) {
                Log.d(TAG, "Server socket closed for slot $slotIndex: ${e.message}")
            }
        } finally {
            cleanupSession(session)
        }
    }

    private fun handleClientConnection(
        session: TranscodeSession,
        client: Socket,
        ffmpegBin: File,
        sourceUrl: String,
        slotIndex: Int
    ) {
        var process: Process? = null
        try {
            // Read minimal HTTP request line
            val input = client.getInputStream()
            val buffer = ByteArray(1024)
            val read = input.read(buffer)
            if (read <= 0) return

            val output = client.getOutputStream()
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: video/mp2t\r\n" +
                    "Connection: close\r\n" +
                    "Cache-Control: no-cache\r\n\r\n"
            output.write(header.toByteArray(Charsets.US_ASCII))
            output.flush()

            // Run FFmpeg to transcode audio while copying video stream verbatim
            val cmd = listOf(
                ffmpegBin.absolutePath,
                "-nostdin",
                "-reconnect", "1",
                "-reconnect_streamed", "1",
                "-reconnect_delay_max", "2",
                "-i", sourceUrl,
                "-c:v", "copy",
                "-c:a", "ac3",
                "-b:a", "384k",
                "-f", "mpegts",
                "pipe:1"
            )

            Log.i(TAG, "Starting transcode process for slot $slotIndex using ${ffmpegBin.name}: ${cmd.joinToString(" ")}")
            process = ProcessBuilder(cmd)
                .redirectErrorStream(false)
                .start()
            session.activeProcess = process

            // Consume stderr in background to prevent process pipe buffer stalling
            Thread({
                try {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.contains("Error", ignoreCase = true)) {
                                Log.w(TAG, "[Transcode-Slot$slotIndex] $line")
                            }
                        }
                    }
                } catch (_: Exception) {}
            }, "Transcode-Stderr-Slot$slotIndex").start()

            // Stream stdout directly to player HTTP socket
            val streamBuffer = ByteArray(64 * 1024)
            val procIn = process.inputStream
            var bytesRead = 0
            while (session.isRunning && procIn.read(streamBuffer).also { bytesRead = it } != -1) {
                output.write(streamBuffer, 0, bytesRead)
                output.flush()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Client streaming ended for slot $slotIndex: ${e.message}")
        } finally {
            try {
                process?.destroyForcibly()
            } catch (_: Exception) {}
            try {
                client.close()
            } catch (_: Exception) {}
        }
    }

    fun stopTranscode(slotIndex: Int) {
        val session = activeServers.remove(slotIndex) ?: return
        Log.i(TAG, "Stopping transcode proxy for slot $slotIndex")
        cleanupSession(session)
    }

    private fun cleanupSession(session: TranscodeSession) {
        session.isRunning = false
        try {
            session.activeProcess?.destroyForcibly()
        } catch (_: Exception) {}
        try {
            session.clientSocket?.close()
        } catch (_: Exception) {}
        try {
            session.serverSocket.close()
        } catch (_: Exception) {}
    }

    fun stopAll() {
        activeServers.keys.forEach { stopTranscode(it) }
    }
}
