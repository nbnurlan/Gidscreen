package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import com.example.model.ChatMessage
import com.example.model.MessageSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Private, backup-excluded storage. Only cropped images ever reach this store. */
internal class ChatSessionStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "floating-chat").apply { mkdirs() }
    private val journal = AtomicFile(File(directory, "session.json"))
    private val lock = Mutex()

    data class Session(val messages: List<ChatMessage>, val interrupted: Boolean)

    suspend fun load(): Session = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!journal.baseFile.exists()) return@withLock Session(emptyList(), false)
            val json = JSONObject(journal.openRead().bufferedReader().use { it.readText() })
            val rows = json.getJSONArray("messages")
            val messages = (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                val id = row.getString("id")
                require(id.matches(Regex("[a-zA-Z0-9-]+")))
                val image = if (row.optBoolean("image")) {
                    requireNotNull(BitmapFactory.decodeFile(File(directory, "$id.png").path))
                } else null
                ChatMessage(id, MessageSender.valueOf(row.getString("sender")),
                    row.getString("text"), image, row.getLong("timestamp"), row.getBoolean("visible"))
            }
            Session(messages, json.optBoolean("pending"))
        }
    }

    suspend fun save(messages: List<ChatMessage>, pending: Boolean) = withContext(Dispatchers.IO) {
        lock.withLock {
            val rows = JSONArray()
            val retained = mutableSetOf<String>()
            messages.forEach { message ->
                require(message.id.matches(Regex("[a-zA-Z0-9-]+")))
                message.image?.let { bitmap ->
                    val filename = "${message.id}.png"
                    retained.add(filename)
                    val imageFile = AtomicFile(File(directory, filename))
                    if (!imageFile.baseFile.exists()) {
                        val stream = imageFile.startWrite()
                        try {
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                            imageFile.finishWrite(stream)
                        } catch (error: Exception) {
                            imageFile.failWrite(stream)
                            throw error
                        }
                    }
                }
                rows.put(JSONObject().put("id", message.id).put("sender", message.sender.name)
                    .put("text", message.text).put("timestamp", message.timestamp)
                    .put("visible", message.isVisible).put("image", message.image != null))
            }
            val stream = journal.startWrite()
            try {
                stream.write(JSONObject().put("messages", rows).put("pending", pending)
                    .toString().toByteArray(Charsets.UTF_8))
                journal.finishWrite(stream)
            } catch (error: Exception) {
                journal.failWrite(stream)
                throw error
            }
            // Delete obsolete crops only after the new journal is durable.
            directory.listFiles()?.filter { it.extension == "png" && it.name !in retained }
                ?.forEach { it.delete() }
        }
    }
}
