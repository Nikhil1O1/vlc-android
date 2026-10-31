package org.videolan.vlc.remoteaccessserver.routing

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.medialibrary.interfaces.media.Playlist
import org.videolan.resources.util.getFromMl
import org.videolan.tools.REMOTE_ACCESS_PLAYBACK_CONTROL
import org.videolan.vlc.gui.dialogs.getPlaylistByName
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.remoteaccessserver.RemoteAccessServer
import org.videolan.vlc.remoteaccessserver.utils.serveAudios
import org.videolan.vlc.remoteaccessserver.utils.servePlaylists
import org.videolan.vlc.remoteaccessserver.utils.serveSearch

private const val MAX_PAGE = 500

/**
 * JSON API for non-browser clients (the MCP bridge). Mounted under /api/v1, authenticated with a bearer token
 * (see [org.videolan.vlc.remoteaccessserver.RemoteAccessApiTokens]) and served on the HTTPS port only.
 * Requests and responses are JSON. Errors are {"error": "..."} with a matching status code.
 */
fun Route.apiRouting(appContext: Context, scope: CoroutineScope, settings: SharedPreferences) {
    route("/api/v1") {
        // ---------- player ----------
        get("/status") {
            val service = RemoteAccessServer.getInstance(appContext).service
            val json = withContext(Dispatchers.Main) {
                val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                JSONObject()
                    .put("serviceRunning", service != null)
                    .put("playing", service?.isPlaying == true)
                    .put("positionMs", service?.getTime() ?: 0L)
                    .put("durationMs", service?.length ?: 0L)
                    .put("volumePercent", audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max)
                    .put("speed", service?.rate ?: 1f)
                    .put("shuffle", service?.isShuffling == true)
                    .put("repeat", service?.repeatType ?: 0)
                    .put("queueSize", service?.media?.size ?: 0)
                    .put("queueIndex", service?.currentMediaPosition ?: -1)
                    .put("nowPlaying", service?.currentMediaWrapper?.toApiJson())
            }
            call.respondApi(json)
        }
        post("/player") {
            if (!call.requirePlaybackControl(settings)) return@post
            val body = call.body() ?: return@post
            val action = body.optString("action")
            val service = RemoteAccessServer.getInstance(appContext).service
            if (service == null) {
                call.respondError(HttpStatusCode.ServiceUnavailable, "Playback service is not running. Start something in the app first.")
                return@post
            }
            val ok = withContext(Dispatchers.Main) {
                when (action) {
                    "play" -> { service.play(); true }
                    "pause" -> { service.pause(); true }
                    "toggle" -> { if (service.isPlaying) service.pause() else service.play(); true }
                    "next" -> { service.next(); true }
                    "previous" -> { service.previous(false); true }
                    "stop" -> { service.stop(); true }
                    "seek" -> { service.setTime(body.optLong("value")); true }
                    "shuffle" -> { service.shuffle(); true }
                    "repeat" -> {
                        service.repeatType = when (body.optInt("value")) {
                            1 -> PlaybackStateCompat.REPEAT_MODE_ONE
                            2 -> PlaybackStateCompat.REPEAT_MODE_ALL
                            else -> PlaybackStateCompat.REPEAT_MODE_NONE
                        }
                        true
                    }
                    "volume" -> {
                        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (body.optInt("value").coerceIn(0, 100) * max / 100), 0)
                        true
                    }
                    "speed" -> { service.setRate(body.optDouble("value", 1.0).toFloat().coerceIn(0.25f, 4f), true); true }
                    else -> false
                }
            }
            if (ok) call.respondApi(JSONObject().put("ok", true))
            else call.respondError(HttpStatusCode.BadRequest, "Unknown action '$action'. Use play, pause, toggle, next, previous, stop, seek, shuffle, repeat, volume, speed.")
        }

        // ---------- queue ----------
        get("/queue") {
            val service = RemoteAccessServer.getInstance(appContext).service
            val (items, index) = withContext(Dispatchers.Main) { (service?.media ?: emptyList()) to (service?.currentMediaPosition ?: -1) }
            val array = JSONArray()
            items.forEachIndexed { i, m -> array.put(m.toApiJson().put("queueIndex", i)) }
            call.respondApi(JSONObject().put("currentIndex", index).put("items", array))
        }
        post("/queue/play") {
            if (!call.requirePlaybackControl(settings)) return@post
            val body = call.body() ?: return@post
            val service = RemoteAccessServer.getInstance(appContext).service
            val medias = appContext.resolveMedia(body)
            if (medias.isEmpty()) {
                call.respondError(HttpStatusCode.NotFound, "No tracks found. Give mediaIds, albumId, artistId, genreId or playlistId.")
                return@post
            }
            val list = if (body.optBoolean("shuffle")) medias.shuffled() else medias
            when (body.optString("mode", "replace")) {
                "next" -> withContext(Dispatchers.Main) { MediaUtils.insertNext(appContext, list.toTypedArray()) }
                "append" -> withContext(Dispatchers.Main) { MediaUtils.appendMedia(appContext, list) }
                else -> withContext(Dispatchers.Main) { MediaUtils.openList(appContext, list, 0) }
            }
            call.respondApi(JSONObject().put("ok", true).put("queued", list.size))
        }
        delete("/queue/{index}") {
            if (!call.requirePlaybackControl(settings)) return@delete
            val index = call.parameters["index"]?.toIntOrNull()
            val service = RemoteAccessServer.getInstance(appContext).service
            if (index == null || service == null) {
                call.respondError(HttpStatusCode.BadRequest, "Bad index or no playback service")
                return@delete
            }
            withContext(Dispatchers.Main) { service.remove(index) }
            call.respondApi(JSONObject().put("ok", true))
        }
        post("/queue/move") {
            if (!call.requirePlaybackControl(settings)) return@post
            val body = call.body() ?: return@post
            val service = RemoteAccessServer.getInstance(appContext).service
            if (service == null || !body.has("from") || !body.has("to")) {
                call.respondError(HttpStatusCode.BadRequest, "Needs from, to and a running playback service")
                return@post
            }
            withContext(Dispatchers.Main) { service.moveItem(body.getInt("from"), body.getInt("to")) }
            call.respondApi(JSONObject().put("ok", true))
        }

        // ---------- library ----------
        get("/search") {
            if (!call.requireContent(settings.serveSearch(appContext))) return@get
            val q = call.request.queryParameters["q"].orEmpty()
            val limit = call.limit(25)
            val result = appContext.getFromMl { search(q, false, false) }
            val json = JSONObject()
            json.put("tracks", JSONArray().also { a -> result?.tracks?.take(limit)?.forEach { a.put(it.toApiJson()) } })
            json.put("albums", JSONArray().also { a -> result?.albums?.take(limit)?.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.albumArtist ?: "").put("trackCount", it.tracksCount)) } })
            json.put("artists", JSONArray().also { a -> result?.artists?.take(limit)?.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("albumCount", it.albumsCount)) } })
            json.put("genres", JSONArray().also { a -> result?.genres?.take(limit)?.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("trackCount", it.tracksCount)) } })
            json.put("playlists", JSONArray().also { a -> result?.playlists?.take(limit)?.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("trackCount", it.tracksCount)) } })
            call.respondApi(json)
        }
        get("/tracks") {
            if (!call.requireContent(settings.serveAudios(appContext))) return@get
            val p = call.request.queryParameters
            val all = appContext.getFromMl { getAudio(Medialibrary.SORT_DEFAULT, false, false, false) }?.toList() ?: emptyList()
            fun contains(value: String?, needle: String?) = needle.isNullOrBlank() || value?.contains(needle, ignoreCase = true) == true
            var items = all.filter {
                contains(it.genre, p["genre"]) && contains(it.artistName, p["artist"]) && contains(it.albumName, p["album"]) &&
                        (p["q"].isNullOrBlank() || contains(it.title, p["q"]) || contains(it.artistName, p["q"]) || contains(it.albumName, p["q"])) &&
                        (p["yearFrom"]?.toIntOrNull()?.let { y -> yearOf(it.releaseYear) >= y } ?: true) &&
                        (p["yearTo"]?.toIntOrNull()?.let { y -> yearOf(it.releaseYear) in 1..y } ?: true) &&
                        (p["favorite"] != "true" || it.isFavorite)
            }
            items = when (p["sort"]) {
                "title" -> items.sortedBy { it.title?.lowercase() }
                "artist" -> items.sortedBy { it.artistName?.lowercase() }
                "year" -> items.sortedByDescending { yearOf(it.releaseYear) }
                "playcount" -> items.sortedByDescending { it.playCount }
                else -> items
            }
            val offset = (p["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
            val page = items.drop(offset).take(call.limit(50))
            call.respondApi(JSONObject().put("total", items.size).put("offset", offset).put("items", JSONArray().also { a -> page.forEach { a.put(it.toApiJson()) } }))
        }
        get("/albums") {
            if (!call.requireContent(settings.serveAudios(appContext))) return@get
            val limit = call.limit(50); val offset = call.offset()
            val total = appContext.getFromMl { albumsCount }
            val items = appContext.getFromMl { getPagedAlbums(Medialibrary.SORT_DEFAULT, false, false, false, limit, offset) }
            call.respondApi(JSONObject().put("total", total).put("items", JSONArray().also { a -> items?.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("artist", it.albumArtist ?: "").put("year", yearOf(it.releaseYear)).put("trackCount", it.tracksCount)) } }))
        }
        get("/artists") {
            if (!call.requireContent(settings.serveAudios(appContext))) return@get
            val limit = call.limit(50); val offset = call.offset()
            val items = appContext.getFromMl { getPagedArtists(true, Medialibrary.SORT_DEFAULT, false, false, false, limit, offset) }
            call.respondApi(JSONObject().put("items", JSONArray().also { a -> items?.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("albumCount", it.albumsCount)) } }))
        }
        get("/genres") {
            if (!call.requireContent(settings.serveAudios(appContext))) return@get
            val limit = call.limit(100); val offset = call.offset()
            val items = appContext.getFromMl { getPagedGenres(Medialibrary.SORT_DEFAULT, false, false, false, limit, offset) }
            call.respondApi(JSONObject().put("items", JSONArray().also { a -> items?.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("trackCount", it.tracksCount)) } }))
        }
        post("/favorite") {
            val body = call.body() ?: return@post
            val id = body.optLong("mediaId")
            val value = body.optBoolean("value", true)
            val found = appContext.getFromMl { getMedia(id)?.also { it.isFavorite = value } != null }
            if (found) call.respondApi(JSONObject().put("ok", true)) else call.respondError(HttpStatusCode.NotFound, "No media with id $id")
        }

        // ---------- playlists ----------
        get("/playlists") {
            if (!call.requireContent(settings.servePlaylists(appContext))) return@get
            val items = appContext.getFromMl { getPlaylists(Playlist.Type.All, false) }
            call.respondApi(JSONObject().put("items", JSONArray().also { a -> items?.forEach { a.put(JSONObject().put("id", it.id).put("title", it.title).put("trackCount", it.tracksCount)) } }))
        }
        get("/playlists/{id}") {
            if (!call.requireContent(settings.servePlaylists(appContext))) return@get
            val id = call.parameters["id"]?.toLongOrNull() ?: return@get call.respondError(HttpStatusCode.BadRequest, "Bad id")
            val result = appContext.getFromMl { getPlaylist(id, true, false)?.let { it to it.tracks } }
            if (result == null) return@get call.respondError(HttpStatusCode.NotFound, "No playlist $id")
            val json = JSONObject().put("id", id).put("title", result.first.title)
            json.put("tracks", JSONArray().also { a -> result.second?.forEachIndexed { i, t -> a.put(t.toApiJson().put("position", i)) } })
            call.respondApi(json)
        }
        post("/playlists") {
            if (!call.requireContent(settings.servePlaylists(appContext))) return@post
            val body = call.body() ?: return@post
            val name = body.optString("name").trim()
            if (name.isEmpty()) return@post call.respondError(HttpStatusCode.BadRequest, "name is required")
            val ids = body.optLongArray("mediaIds")
            val created = appContext.getFromMl {
                if (getPlaylistByName(name) != null) null
                else createPlaylist(name, true, false)?.also { if (ids.isNotEmpty()) it.append(ids) }
            }
            if (created == null) call.respondError(HttpStatusCode.Conflict, "A playlist named '$name' already exists")
            else call.respondApi(JSONObject().put("id", created.id).put("title", name).put("added", ids.size), HttpStatusCode.Created)
        }
        patch("/playlists/{id}") {
            if (!call.requireContent(settings.servePlaylists(appContext))) return@patch
            val id = call.parameters["id"]?.toLongOrNull() ?: return@patch call.respondError(HttpStatusCode.BadRequest, "Bad id")
            val name = (call.body() ?: return@patch).optString("name").trim()
            if (name.isEmpty()) return@patch call.respondError(HttpStatusCode.BadRequest, "name is required")
            val ok = appContext.getFromMl { getPlaylist(id, true, false)?.also { it.setName(name) } != null }
            if (ok) call.respondApi(JSONObject().put("ok", true)) else call.respondError(HttpStatusCode.NotFound, "No playlist $id")
        }
        delete("/playlists/{id}") {
            if (!call.requireContent(settings.servePlaylists(appContext))) return@delete
            val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respondError(HttpStatusCode.BadRequest, "Bad id")
            val ok = appContext.getFromMl { getPlaylist(id, true, false)?.also { it.delete() } != null }
            if (ok) call.respondApi(JSONObject().put("ok", true)) else call.respondError(HttpStatusCode.NotFound, "No playlist $id")
        }
        post("/playlists/{id}/tracks") {
            if (!call.requireContent(settings.servePlaylists(appContext))) return@post
            val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respondError(HttpStatusCode.BadRequest, "Bad id")
            val body = call.body() ?: return@post
            val medias = appContext.resolveMedia(body)
            if (medias.isEmpty()) return@post call.respondError(HttpStatusCode.NotFound, "No tracks found. Give mediaIds, albumId, artistId or genreId.")
            val position = if (body.has("position")) body.getInt("position") else -1
            val ok = appContext.getFromMl {
                getPlaylist(id, true, false)?.also { playlist ->
                    if (position < 0) playlist.append(medias.map { it.id }.toLongArray())
                    else medias.forEachIndexed { i, m -> playlist.add(m.id, position + i) }
                } != null
            }
            if (ok) call.respondApi(JSONObject().put("ok", true).put("added", medias.size)) else call.respondError(HttpStatusCode.NotFound, "No playlist $id")
        }
        delete("/playlists/{id}/tracks") {
            if (!call.requireContent(settings.servePlaylists(appContext))) return@delete
            val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.respondError(HttpStatusCode.BadRequest, "Bad id")
            val positions = (call.body() ?: return@delete).optLongArray("positions").map { it.toInt() }.distinct().sortedDescending()
            val ok = appContext.getFromMl { getPlaylist(id, true, false)?.also { p -> positions.forEach { p.remove(it) } } != null }
            if (ok) call.respondApi(JSONObject().put("ok", true).put("removed", positions.size)) else call.respondError(HttpStatusCode.NotFound, "No playlist $id")
        }
        post("/playlists/{id}/move") {
            if (!call.requireContent(settings.servePlaylists(appContext))) return@post
            val id = call.parameters["id"]?.toLongOrNull() ?: return@post call.respondError(HttpStatusCode.BadRequest, "Bad id")
            val body = call.body() ?: return@post
            if (!body.has("from") || !body.has("to")) return@post call.respondError(HttpStatusCode.BadRequest, "Needs from and to")
            val ok = appContext.getFromMl { getPlaylist(id, true, false)?.also { it.move(body.getInt("from"), body.getInt("to")) } != null }
            if (ok) call.respondApi(JSONObject().put("ok", true)) else call.respondError(HttpStatusCode.NotFound, "No playlist $id")
        }
    }
}

// ---------- helpers ----------

/** The medialibrary stores the release date as a unix timestamp (seconds) for tracks and albums, or sometimes as a plain year. */
private fun yearOf(releaseDate: Int): Int = when {
    releaseDate <= 0 -> 0
    releaseDate < 3000 -> releaseDate
    else -> java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = releaseDate * 1000L }.get(java.util.Calendar.YEAR)
}

private fun MediaWrapper.toApiJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title ?: "")
    .put("artist", artistName ?: "")
    .put("album", albumName ?: "")
    .put("genre", genre ?: "")
    .put("year", yearOf(releaseYear))
    .put("durationMs", length)
    .put("playCount", playCount)
    .put("favorite", isFavorite)
    .put("trackNumber", trackNumber)

private suspend fun ApplicationCall.respondApi(json: JSONObject, status: HttpStatusCode = HttpStatusCode.OK) =
    respondJson(json.toString(), status)

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String) =
    respondJson(JSONObject().put("error", message).toString(), status)

/** Parses the JSON request body, or answers 400 and returns null. */
private suspend fun ApplicationCall.body(): JSONObject? = try {
    val text = receiveText()
    if (text.isBlank()) JSONObject() else JSONObject(text)
} catch (e: Exception) {
    respondError(HttpStatusCode.BadRequest, "Body must be valid JSON")
    null
}

private suspend fun ApplicationCall.requirePlaybackControl(settings: SharedPreferences): Boolean =
    if (settings.getBoolean(REMOTE_ACCESS_PLAYBACK_CONTROL, true)) true
    else { respondError(HttpStatusCode.Forbidden, "Playback control is disabled in the app's Remote Access settings"); false }

private suspend fun ApplicationCall.requireContent(allowed: Boolean): Boolean =
    if (allowed) true
    else { respondError(HttpStatusCode.Forbidden, "This content type is disabled in the app's Remote Access settings"); false }

private fun ApplicationCall.limit(default: Int) = (request.queryParameters["limit"]?.toIntOrNull() ?: default).coerceIn(1, MAX_PAGE)
private fun ApplicationCall.offset() = (request.queryParameters["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)

private fun JSONObject.optLongArray(key: String): LongArray {
    val array = optJSONArray(key) ?: return LongArray(0)
    return LongArray(array.length()) { array.optLong(it) }
}

/** Resolves mediaIds / albumId / artistId / genreId / playlistId in [body] to library tracks (unknown ids are skipped). */
private suspend fun Context.resolveMedia(body: JSONObject): List<MediaWrapper> = getFromMl {
    val result = ArrayList<MediaWrapper>()
    body.optLongArray("mediaIds").forEach { id -> getMedia(id)?.let { if (it.id != 0L) result.add(it) } }
    if (body.has("albumId")) getAlbum(body.getLong("albumId"))?.tracks?.let { result.addAll(it) }
    if (body.has("artistId")) getArtist(body.getLong("artistId"))?.tracks?.let { result.addAll(it) }
    if (body.has("genreId")) getGenre(body.getLong("genreId"))?.tracks?.let { result.addAll(it) }
    if (body.has("playlistId")) getPlaylist(body.getLong("playlistId"), true, false)?.tracks?.let { result.addAll(it) }
    result as List<MediaWrapper>
} ?: emptyList()
