package com.example.movie

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MovieControllerTest {
    private class FakeGateway : MovieGateway {
        var lookups = 0
        var frames = 0
        var answers = 0
        var delayAnswer = false
        var fail = false
        var confidence = .98
        var prompt = ""
        var context = ""
        override suspend fun identify(frames: List<String>): List<MovieCandidate> {
            lookups++; this.frames = frames.size
            if (fail) throw java.io.IOException("offline")
            return listOf(MovieCandidate(1, "Movie", "2001", confidence))
        }
        override suspend fun details(movie: MovieCandidate) = "SECRET FUTURE PLOT"
        override suspend fun generate(frames: List<String>, text: String, system: String, json: Boolean): String {
            answers++; prompt = system; context = text
            if (delayAnswer) delay(60000)
            return "Visible scene only"
        }
    }
    private fun controller(scope: CoroutineScope, api: FakeGateway, frame: suspend () -> String? = { "frame" }): MovieController {
        val context = ApplicationProvider.getApplicationContext<Context>()
        MovieSession(context).reset()
        return MovieController(context, scope, {}, {}, api, frame)
    }
    @Test fun stopBeforeDelayPreventsAnyUpload() = runTest {
        val api = FakeGateway(); val c = controller(this, api)
        c.start(); runCurrent(); c.stop(); advanceUntilIdle()
        assertEquals(0, api.lookups); assertFalse(c.active); assertFalse(c.busy)
    }
    @Test fun multiFrameRecognitionAndSpoilerContext() = runTest {
        val api = FakeGateway(); val c = controller(this, api)
        c.start(); advanceUntilIdle()
        assertTrue(c.ready); assertEquals(3, api.frames)
        c.ask("Why?"); advanceUntilIdle()
        assertEquals(2, c.session.turns.size)
        assertFalse(api.context.contains("SECRET FUTURE PLOT"))
        assertTrue(api.prompt.contains("SPOILER-FREE"))
    }
    @Test fun stoppedRequestCannotAppendLateAnswer() = runTest {
        val api = FakeGateway(); val c = controller(this, api)
        c.start(); advanceUntilIdle()
        api.delayAnswer = true
        c.ask("Who?"); runCurrent(); c.stop(); advanceUntilIdle()
        assertEquals(1, c.session.turns.size)
        assertEquals("user", c.session.turns.single().role)
        assertFalse(c.ready)
    }
    @Test fun uncertainAndBlankFramesDoNotConfirmFilm() = runTest {
        val api = FakeGateway(); api.confidence = .5
        val c = controller(this, api); c.start(); advanceUntilIdle()
        assertFalse(c.ready); assertEquals(1, c.candidates.size)
        c.ask("Who?"); advanceUntilIdle(); assertEquals(0, api.answers)
        val darkApi = FakeGateway(); val dark = controller(this, darkApi) { null }
        dark.start(); advanceUntilIdle(); assertEquals(0, darkApi.lookups)
        assertFalse(dark.ready)
    }
    @Test fun errorReleasesBusyStateAndAllowsManualRetry() = runTest {
        val api = FakeGateway(); api.fail = true
        val c = controller(this, api); c.start(); advanceUntilIdle()
        assertFalse(c.busy); assertFalse(c.ready)
        api.fail = false; c.recognize(); advanceUntilIdle()
        assertTrue(c.ready); assertEquals(2, api.lookups)
    }
}
