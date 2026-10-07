package com.example.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ModelDefaultTest {
    @Test fun freshInstallUses25FlashAndSavedChoiceSurvivesReinitialization() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("gemini_model_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        GeminiModelManager.init(context)
        assertEquals("gemini-2.5-flash", GeminiModelManager.selectedModelId.value)
        GeminiModelManager.setSelectedModel(context, "gemini-2.5-pro")
        GeminiModelManager.init(context)
        assertEquals("gemini-2.5-pro", GeminiModelManager.selectedModelId.value)
    }
}
