package com.example.movie

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MovieSessionTest {
    @Test fun textHistorySurvivesRestartAndNewFilmClearsContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val session = MovieSession(context)
        session.reset()
        assertTrue(session.spoilerFree)
        session.movie = MovieCandidate(12, "Example", "2000", .8)
        session.turns += MovieTurn("user", "Who is this?")
        session.save()
        val restored = MovieSession(context)
        assertEquals("Example", restored.movie?.title)
        assertEquals("Who is this?", restored.turns.single().text)
        restored.reset()
        val cleared = MovieSession(context)
        assertNull(cleared.movie)
        assertTrue(cleared.turns.isEmpty())
        assertEquals("", cleared.metadata)
    }
}
