package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.data.local.dao.MessageDao
import com.todo.dailyroutine.data.local.entity.LocalMessage
import com.todo.dailyroutine.data.model.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChatRepository(private val messageDao: MessageDao) {

    suspend fun getRecentMessages(limit: Int = 50): List<ChatMessage> = withContext(Dispatchers.IO) {
        messageDao.getRecentMessages(limit).reversed().map { it.toChatMessage() }
    }

    suspend fun saveMessage(message: ChatMessage, userId: String) = withContext(Dispatchers.IO) {
        messageDao.insertMessage(
            LocalMessage(
                userId = userId,
                role = message.role,
                content = message.content,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        messageDao.clearHistory()
    }

    private fun LocalMessage.toChatMessage() = ChatMessage(
        role = role,
        content = content
    )
}
