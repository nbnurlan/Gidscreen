package com.example.movie

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class MovieTurn(val role: String, val text: String)
/** Text-only separate no-backup store. Capture consent and frames are never persisted. */
class MovieSession(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "movie-session.json"))
    var movie: MovieCandidate? = null
    var metadata = ""
    var spoilerFree = true
    val turns = mutableListOf<MovieTurn>()
    init {
        try {
            val root = JSONObject(String(file.readFully(), Charsets.UTF_8))
            movie = root.optJSONObject("movie")?.let(MovieCandidate::from)
            metadata = root.optString("metadata")
            spoilerFree = root.optBoolean("spoilerFree", true)
            val rows = root.optJSONArray("turns") ?: JSONArray()
            for (i in 0 until minOf(rows.length(), 200)) {
                val row = rows.getJSONObject(i)
                turns += MovieTurn(row.getString("role"), row.getString("text"))
            }
        } catch (_: Exception) { movie = null; metadata = ""; turns.clear() }
    }
    fun save() {
        val root = JSONObject().put("metadata", metadata).put("spoilerFree", spoilerFree)
            .put("turns", JSONArray(turns.takeLast(200).map { JSONObject().put("role", it.role).put("text", it.text) }))
        movie?.let { root.put("movie", it.json()) }
        val out = file.startWrite()
        try { out.write(root.toString().toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
    }
    fun reset() { movie = null; metadata = ""; turns.clear(); save() }
}
