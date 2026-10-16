package com.example.movie

import org.junit.Assert.*
import org.junit.Test

class MoviePolicyTest {
    @Test fun uncertainOrAmbiguousMatchesAreNeverAutoSelected() {
        assertFalse(MoviePolicy.canAutoSelect(.91, 1))
        assertFalse(MoviePolicy.canAutoSelect(.99, 2))
        assertFalse(MoviePolicy.canAutoSelect(.99, 0))
        assertFalse(MoviePolicy.canAutoSelect(Double.NaN, 1))
        assertFalse(MoviePolicy.canAutoSelect(2.0, 1))
        assertTrue(MoviePolicy.canAutoSelect(.92, 1))
    }
    @Test fun spoilerPolicyDoesNotInferViewingProgress() {
        val prompt = MoviePolicy.systemPrompt(true, "Uzbek")
        assertTrue(prompt.contains("Never infer viewing progress"))
        assertTrue(prompt.contains("even if requested"))
        assertTrue(prompt.contains("No audio is supplied"))
        assertTrue(prompt.contains("Uzbek"))
        assertFalse(MoviePolicy.systemPrompt(false, "Russian").contains("SPOILER-FREE"))
    }
}
