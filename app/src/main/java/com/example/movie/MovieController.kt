package com.example.movie

import android.app.KeyguardManager
import android.content.Context
import android.graphics.RectF
import androidx.compose.runtime.*
import com.example.R
import com.example.service.MediaProjectionHolder
import com.example.service.ScreenCaptureHelper
import com.example.util.LocaleHelper
import kotlinx.coroutines.*

/** One serialized capture/request job, bounded recognition, no automatic restart after process death. */
class MovieController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val captureVisibility: (Boolean) -> Unit,
    private val notify: (String) -> Unit,
    private val api: MovieGateway = MovieApi(),
    private val captureOverride: (suspend () -> String?)? = null
) {
    val session = MovieSession(context)
    var active by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var status by mutableStateOf(context.getString(R.string.movie_stopped)); private set
    var candidates by mutableStateOf<List<MovieCandidate>>(emptyList()); private set
    var revision by mutableIntStateOf(0); private set
    val ready: Boolean get() = active && confirmedThisRun && session.movie != null
    private var job: Job? = null
    private var scene: String? = null
    private var confirmedThisRun by mutableStateOf(false)

    private fun status(id: Int) { status = context.getString(id); notify(status) }
    private fun persist() { session.save(); revision++ }

    fun start() {
        if (active) return
        active = true
        recognize(reset = false)
    }
    fun stop() {
        active = false
        job?.cancel(); job = null
        busy = false; scene = null; confirmedThisRun = false; candidates = emptyList()
        captureVisibility(true)
        status(R.string.movie_stopped)
    }
    fun projectionStopped() { if (active) stop() }

    private suspend fun frame(): String? {
        captureOverride?.let { return it() }
        if (!active || !MediaProjectionHolder.isProjectionActive.value) return null
        if ((context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) return null
        captureVisibility(false)
        return try {
            delay(220)
            val projection = MediaProjectionHolder.mediaProjection ?: return null
            val (w, h, _) = ScreenCaptureHelper.getRealScreenMetrics(context)
            val bitmap = ScreenCaptureHelper.captureArea(context, projection, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), System.nanoTime()) ?: return null
            try { MovieApi.encode(bitmap) } finally { bitmap.recycle() }
        } finally { captureVisibility(true) }
    }

    private fun launch(block: suspend () -> Unit) {
        if (!active || busy) return
        busy = true
        job = scope.launch {
            try { block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status(R.string.movie_error) }
            finally { scene = null; busy = false }
        }
    }

    fun recognize(reset: Boolean = false) {
        launch {
            if (reset) { session.reset(); revision++; scene = null }
            candidates = emptyList()
            confirmedThisRun = false
            status(R.string.movie_preparing)
            delay(10000) // Return to the movie before any pixels are sent.
            status(R.string.movie_identifying)
            val frames = mutableListOf<String>()
            try {
                repeat(3) {
                    frame()?.let { frames += it }
                    if (it < 2) delay(2500)
                }
                if (frames.isEmpty()) { status(R.string.movie_black); return@launch }
                scene = frames.last()
                candidates = api.identify(frames)
                val first = candidates.firstOrNull()
                if (first != null && MoviePolicy.canAutoSelect(first.confidence, candidates.size)) selectInternal(first)
                else status(if (candidates.isEmpty()) R.string.movie_unknown else R.string.movie_choose)
            } finally { frames.clear() }
        }
    }
    fun choose(candidate: MovieCandidate) { launch { selectInternal(candidate) } }
    private suspend fun selectInternal(candidate: MovieCandidate) {
        val metadata = api.details(candidate)
        ensureActiveContext()
        if (session.movie?.id != candidate.id) session.turns.clear()
        session.movie = candidate; session.metadata = metadata
        confirmedThisRun = true
        candidates = emptyList(); persist()
        status = "${candidate.title} (${candidate.year}) · ${context.getString(R.string.movie_estimate)} ${(candidate.confidence * 100).toInt()}%"
        notify(status)
    }
    private suspend fun ensureActiveContext() { currentCoroutineContext().ensureActive(); check(active) }
    fun spoiler(value: Boolean) {
        // Previously revealed spoilers must not become alleged watched context on re-enabling protection.
        if (value && !session.spoilerFree) session.turns.clear()
        session.spoilerFree = value; persist()
    }
    fun ask(question: String) {
        if (question.isBlank() || session.movie == null || !confirmedThisRun) return
        launch {
            status(R.string.movie_answering)
            // Refresh before opening the keyboard; overlay is hidden during capture.
            scene = frame()
            val movie = session.movie ?: return@launch
            val language = when (LocaleHelper.currentLanguage.value) { "ru" -> "Russian"; "en" -> "English"; else -> "Uzbek" }
            val history = session.turns.takeLast(12).map { org.json.JSONObject().put("role", it.role).put("text", it.text.take(3000)) }
            val contextData = org.json.JSONObject().put("movie", movie.title).put("year", movie.year)
                .put("history", org.json.JSONArray(history)).put("question", question.take(4000))
                .put("sceneAvailable", scene != null)
            if (!session.spoilerFree) contextData.put("metadata", session.metadata)
            session.turns += MovieTurn("user", question.take(4000)); persist()
            val answer = api.generate(listOfNotNull(scene), contextData.toString(), MoviePolicy.systemPrompt(session.spoilerFree, language))
            ensureActiveContext()
            session.turns += MovieTurn("assistant", if (scene == null) context.getString(R.string.movie_no_scene) + "\n\n" + answer else answer); persist()
            scene = null // Do not retain sensitive pixels beyond the request.
            status(R.string.movie_ready)
        }
    }
}
