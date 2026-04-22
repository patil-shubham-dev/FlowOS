package com.todo.dailyroutine.domain.ai

import com.todo.dailyroutine.data.model.HabitItem
import com.todo.dailyroutine.data.model.TaskItem
import com.todo.dailyroutine.data.repository.AiRepository
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

class SmartDayScheduler(private val aiRepository: AiRepository) {
    suspend fun scheduleDay(tasks: List<TaskItem>, habits: List<HabitItem>): List<ScheduledItem> {
        val prompt = """
            You are the FlowOS Smart Scheduler. Optimize the sequence of these items for peak performance.
            Tasks: ${tasks.filter { !it.completed }.joinToString { it.title }}
            Habits: ${habits.joinToString { it.name }}
            
            Return a JSON array of objects: [{"title": "...", "time": "HH:mm", "type": "task|habit"}]
        """.trimIndent()
        
        val result = aiRepository.chat(prompt).getOrNull() ?: return emptyList()
        
        return try {
            val jsonArray = JSONArray(result)
            val list = mutableListOf<ScheduledItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(ScheduledItem(
                    obj.getString("title"),
                    obj.getString("time"),
                    obj.getString("type")
                ))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}

data class ScheduledItem(val title: String, val time: String, val type: String)
