package com.laptop.gallery

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * LAN HTTP server exposing the gallery. Endpoints:
 *   GET /pair?otp=NNNNNN      -> {"token":"...","fcmToken":"..."} (200) or 403
 *   GET /gallery?page=N&t=TOK -> {"total":N,"items":[...]}  (needs token)
 *   GET /thumb/{id}?t=TOK     -> image/jpeg                 (needs token)
 *   GET /image/{id}?t=TOK     -> image or video file        (needs token)
 *
 *   GET /sleep?t=TOK         -> stop service after response    (needs token)
 *   GET /unpair?t=TOK        -> unpair client                  (needs token)
 *
 * Every authorized request resets a 5-minute idle timer via [onActivity].
 */
class GalleryServer(
    port: Int,
    private val repo: GalleryRepo,
    private val pairs: PairStore,
    private val fcmTokens: FcmTokenStore,
    private val onActivity: () -> Unit,
    private val onClientState: (connected: Boolean) -> Unit,
    private val onClientSleep: () -> Unit,
    private val onClientUnpair: (token: String?) -> Unit
) : NanoHTTPD("0.0.0.0", port) {

    companion object {
        const val PAGE_SIZE = 300
        private const val JSON = "application/json"
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val q = session.parameters // Map<String, List<String>>

        // Preflight / permissive CORS so the browser client can fetch freely on the LAN.
        if (session.method == Method.OPTIONS) return cors(newFixedLengthResponse(""))

        // --- Pairing: the only endpoint that doesn't need a token. ---
        if (uri == "/pair") {
            val otp = q["otp"]?.firstOrNull()
            val token = pairs.redeem(otp)
            return if (token != null) {
                onActivity()
                cors(json("""{"token":"$token","fcmToken":${jsonString(fcmTokens.get())}}"""))
            } else {
                cors(newFixedLengthResponse(Response.Status.FORBIDDEN, JSON, """{"error":"bad otp"}"""))
            }
        }

        // --- Everything else requires an approved token. ---
        val token = q["t"]?.firstOrNull()
        if (!pairs.isApproved(token)) {
            return cors(newFixedLengthResponse(Response.Status.FORBIDDEN, JSON, """{"error":"unpaired"}"""))
        }
        onActivity()
        onClientState(true)

        return when {
            uri == "/gallery" -> {
                val page = q["page"]?.firstOrNull()?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                cors(json(galleryJson(page)))
            }
            uri == "/sleep" -> {
                onClientSleep()
                cors(json("""{"ok":true}"""))
            }
            uri == "/unpair" -> {
                onClientUnpair(token)
                cors(json("""{"ok":true}"""))
            }
            uri.startsWith("/thumb/") -> {
                val parts = uri.removePrefix("/thumb/").split("?")
                val (id, isVideo) = parseIdIsVideo(parts[0])
                val bytes = id?.let { repo.thumbnail(it, isVideo ?: false) }
                if (bytes != null) cors(image(bytes)) else notFound()
            }
            uri.startsWith("/image/") -> {
                val parts = uri.removePrefix("/image/").split("?")
                val (id, isVideo) = parseIdIsVideo(parts[0])
                val media = id?.let { repo.openOriginal(it, isVideo ?: false) }
                if (media != null) {
                    val r = if (media.size >= 0) {
                        newFixedLengthResponse(Response.Status.OK, media.mimeType, media.stream, media.size)
                    } else {
                        newChunkedResponse(Response.Status.OK, media.mimeType, media.stream)
                    }
                    r.addHeader("Cache-Control", "max-age=86400")
                    cors(r)
                } else {
                    notFound()
                }
            }
            else -> notFound()
        }
    }

    /** Build the paginated gallery JSON payload. */
    private fun galleryJson(page: Int): String {
        val total = repo.count()
        val items = repo.page(page * PAGE_SIZE, PAGE_SIZE)
        val arr = StringBuilder(items.size * 60)
        arr.append('[')
        items.forEachIndexed { i, m ->
            if (i > 0) arr.append(',')
            arr.append("""{"id":${m.id},"date":${m.date},"w":${m.w},"h":${m.h},"v":${m.isVideo}}""")
        }
        arr.append(']')
        return """{"total":$total,"items":$arr,"fcmToken":${jsonString(fcmTokens.get())}}"""
    }

    private fun parseIdIsVideo(s: String): Pair<Long?, Boolean?> {
        // Format: "123" or "123v" (v suffix means video).
        val id = s.removeSuffix("v").toLongOrNull() ?: return Pair(null, null)
        val isVideo = s.endsWith("v")
        return Pair(id, isVideo)
    }

    private fun json(body: String) = newFixedLengthResponse(Response.Status.OK, JSON, body)

    private fun jsonString(value: String?): String =
        if (value == null) "null" else "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"") + "\""

    private fun image(bytes: ByteArray): Response {
        val r = newFixedLengthResponse(
            Response.Status.OK, "image/jpeg", ByteArrayInputStream(bytes), bytes.size.toLong()
        )
        r.addHeader("Cache-Control", "max-age=86400")
        return r
    }

    private fun notFound() =
        cors(newFixedLengthResponse(Response.Status.NOT_FOUND, JSON, """{"error":"not found"}"""))

    private fun cors(r: Response): Response {
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Headers", "*")
        r.addHeader("Access-Control-Allow-Methods", "GET, OPTIONS")
        // Chrome Private Network Access: a page on localhost/public origin fetching
        // a private LAN IP sends a preflight requiring this header, else "Failed to fetch".
        r.addHeader("Access-Control-Allow-Private-Network", "true")
        r.addHeader("Access-Control-Max-Age", "86400")
        return r
    }
}
