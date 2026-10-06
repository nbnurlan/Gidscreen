package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.model.ChatMessage
import com.example.model.MessageSender
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class ChatSessionStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun clean() { File(context.noBackupFilesDir, "floating-chat").deleteRecursively() }

    @Test fun restoresHiddenImageContextAndPendingRequestAfterRecreation() = runTest {
        val crop = Bitmap.createBitmap(20, 12, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val request = ChatMessage(sender = MessageSender.USER, text = "Analyze this", image = crop, isVisible = false)
        val answer = ChatMessage(sender = MessageSender.AI, text = "First answer")
        ChatSessionStore(context).save(listOf(request, answer), pending = true)
        val restored = ChatSessionStore(context).load()
        assertTrue(restored.interrupted)
        assertEquals(listOf(request.id, answer.id), restored.messages.map { it.id })
        assertFalse(restored.messages.first().isVisible)
        assertEquals(request.timestamp, restored.messages.first().timestamp)
        assertEquals(Color.RED, restored.messages.first().image!!.getPixel(5, 5))
        assertEquals(20, restored.messages.first().image!!.width)
        assertEquals(answer.text, restored.messages.last().text)
    }

    @Test fun interruptedAtomicWriteRecoversPreviousJournal() = runTest {
        val message = ChatMessage(sender = MessageSender.AI, text = "Saved answer")
        ChatSessionStore(context).save(listOf(message), false)
        val journal = File(context.noBackupFilesDir, "floating-chat/session.json")
        assertTrue(journal.renameTo(File(journal.path + ".bak")))
        assertEquals(message.id, ChatSessionStore(context).load().messages.single().id)
    }

    @Test fun clearingSessionRemovesPersistedCropsAndContext() = runTest {
        val store = ChatSessionStore(context)
        store.save(listOf(ChatMessage(sender = MessageSender.USER, text = "image",
            image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))), false)
        store.save(emptyList(), false)
        val restored = ChatSessionStore(context).load()
        assertTrue(restored.messages.isEmpty())
        assertFalse(restored.interrupted)
        assertTrue(File(context.noBackupFilesDir, "floating-chat").listFiles()!!.none { it.extension == "png" })
    }
}
