package com.laptop.gallery

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** UI-visible sharing status. */
enum class ServerStatus { STOPPED, WAITING, CONNECTED }

/**
 * Owns the [GalleryServer] lifecycle and the idle timer. Compose observes the
 * public state fields. No persistent background service — this is created and
 * destroyed with the Activity.
 */
class ServerController(private val context: Context) {

    companion object {
        val PORT = BuildConfig.GALLERY_SERVER_PORT
        private const val IDLE_MS = 5 * 60 * 1000L         // stop 5 min after last request
        private const val CLIENT_IDLE_MS = 30 * 1000L      // "connected" -> "waiting" after 30s quiet
    }

    private val repo = GalleryRepo(context)
    private val pairs = PairStore(context)
    private val fcmTokens = FcmTokenStore(context)
    private val main = Handler(Looper.getMainLooper())

    private var server: GalleryServer? = null
    private var idleStop: Runnable? = null
    private var clientIdle: Runnable? = null

    var status by mutableStateOf(ServerStatus.STOPPED)
        private set
    var otp by mutableStateOf("")
        private set
    var ip by mutableStateOf<String?>(null)
        private set
    var photoCount by mutableStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val port = PORT

    fun refreshCount() { photoCount = repo.count() }

    fun start() {
        GalleryForegroundService.start(context)
    }

    fun startServing() {
        if (server != null) return
        error = null
        otp = pairs.getOrCreateOtp()
        ip = NetUtil.localIp(context)
        try {
            val s = GalleryServer(
                port = PORT,
                repo = repo,
                pairs = pairs,
                fcmTokens = fcmTokens,
                onActivity = { main.post { onActivity() } },
                onClientState = { connected -> main.post { if (connected) markConnected() } },
                onClientSleep = {
                    main.post {
                        NotificationUtil.showNotification(context, "Laptop Gallery", "laptop gallery disconnected")
                    }
                    main.postDelayed({ GalleryForegroundService.stop(context) }, 150)
                },
                onClientUnpair = { token ->
                    main.post {
                        pairs.unpairToken(token)
                        NotificationUtil.showNotification(context, "Laptop Gallery", "laptop gallery unpaired")
                    }
                    main.postDelayed({ GalleryForegroundService.stop(context) }, 150)
                }
            )
            s.start(NanoTimeout, false)
            server = s
            status = ServerStatus.WAITING
            scheduleIdleStop()
        } catch (e: Exception) {
            // Bind failure (port busy) or similar — surface instead of crashing.
            server = null
            otp = ""
            status = ServerStatus.STOPPED
            error = "Couldn't start server: ${e.message ?: "unknown error"}"
        }
    }

    fun stop() {
        GalleryForegroundService.stop(context)
    }

    fun sleepServer() {
        NotificationUtil.showNotification(context, "Laptop Gallery", "laptop gallery disconnected")
        stop()
    }

    fun unpairAll() {
        pairs.clearAll()
        NotificationUtil.showNotification(context, "Laptop Gallery", "laptop gallery unpaired")
        stop()
    }

    fun hasPairedDevices(): Boolean = pairs.hasPairedTokens()

    fun stopServing() {
        cancel(idleStop); cancel(clientIdle)
        server?.stop()
        server = null
        status = ServerStatus.STOPPED
        otp = ""
    }

    fun toggle() { if (server == null) start() else stop() }

    /** Any authorized request keeps the server alive. */
    private fun onActivity() {
        if (server == null) return
        scheduleIdleStop()
    }

    private fun markConnected() {
        if (server == null) return
        status = ServerStatus.CONNECTED
        cancel(clientIdle)
        clientIdle = Runnable {
            if (server != null) status = ServerStatus.WAITING
        }.also { main.postDelayed(it, CLIENT_IDLE_MS) }
    }

    private fun scheduleIdleStop() {
        cancel(idleStop)
        idleStop = Runnable { GalleryForegroundService.stop(context) }.also { main.postDelayed(it, IDLE_MS) }
    }

    private fun cancel(r: Runnable?) { r?.let { main.removeCallbacks(it) } }
}

/** NanoHTTPD socket read timeout (ms). */
private const val NanoTimeout = 10_000
