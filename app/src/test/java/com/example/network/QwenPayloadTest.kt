package com.example.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.model.ChatMessage
import com.example.model.MessageSender
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class QwenPayloadTest {

    private fun encode(bitmap: Bitmap, retry: Boolean): String {
        val method = QwenService::class.java.getDeclaredMethod(
            "bitmapToDataUrl",
            Bitmap::class.java,
            Boolean::class.javaPrimitiveType
        ).apply { isAccessible = true }
        return method.invoke(QwenService, bitmap, retry) as String
    }

    private fun decodeDataUrl(dataUrl: String): Bitmap {
        val bytes = Base64.decode(dataUrl.substringAfter(','), Base64.NO_WRAP)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    @Test
    fun defaultAndRetryImagesRespectDimensionCaps() {
        val source = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
        try {
            val normal = decodeDataUrl(encode(source, false))
            val retry = decodeDataUrl(encode(source, true))
            try {
                assertTrue(maxOf(normal.width, normal.height) <= 768)
                assertTrue(maxOf(retry.width, retry.height) <= 512)
            } finally {
                normal.recycle()
                retry.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    @Test
    fun historicalImagesAreExcludedFromHuggingFacePayload() {
        val historical = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val current = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            val history = listOf(
                ChatMessage(
                    sender = MessageSender.USER,
                    text = "Earlier capture",
                    image = historical
                ),
                ChatMessage(
                    sender = MessageSender.AI,
                    text = "Earlier answer"
                )
            )

            val method = QwenService::class.java.getDeclaredMethod(
                "buildMessages",
                List::class.java,
                String::class.java,
                Bitmap::class.java,
                Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }

            val messages = method.invoke(
                QwenService,
                history,
                "Current capture",
                current,
                false
            ) as JSONArray

            val serialized = messages.toString()
            val imageOccurrences = "data:image/jpeg;base64,".toRegex()
                .findAll(serialized)
                .count()

            assertEquals(1, imageOccurrences)
            assertTrue(serialized.contains("Earlier capture"))
            assertTrue(serialized.contains("Earlier answer"))
        } finally {
            historical.recycle()
            current.recycle()
        }
    }
}
