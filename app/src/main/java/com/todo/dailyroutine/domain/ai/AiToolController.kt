package com.todo.dailyroutine.domain.ai

import com.todo.dailyroutine.data.model.AiToolCall
import com.todo.dailyroutine.data.model.ToolExecutionResult
import com.todo.dailyroutine.data.repository.AiRepository
import com.todo.dailyroutine.data.session.SessionManager
import org.json.JSONObject

class AiToolController(
    private val toolExecutor: OracleToolExecutor,
    private val aiRepository: AiRepository,
    private val sessionManager: SessionManager
) {
    suspend fun handleToolCalls(toolCalls: List<AiToolCall>): List<ToolExecutionResult> {
        val userId = sessionManager.getUserId() ?: return emptyList()
        
        return toolCalls.map { call ->
            val result = toolExecutor.executeToolCall(
                call.function.name,
                call.function.arguments,
                userId
            )
            ToolExecutionResult(
                toolCallId = call.id,
                content = result
            )
        }
    }
    
    fun getToolSchemas(): List<Map<String, Any>> {
        return listOf(
            mapOf("type" to "function", "function" to mapOf(
                "name" to "create_task",
                "description" to "Create a new task/objective in the protocol",
                "parameters" to mapOf("type" to "object", "properties" to mapOf(
                    "title" to mapOf("type" to "string", "description" to "The title of the task"),
                    "category" to mapOf("type" to "string", "enum" to listOf("work", "personal", "health", "focus")),
                    "priority" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 5),
                    "energyRequired" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 10),
                    "timeBlock" to mapOf("type" to "string", "enum" to listOf("Morning", "Deep Work", "Evening", "Night"))
                ), "required" to listOf("title"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to "complete_task",
                "description" to "Mark a specific task as complete",
                "parameters" to mapOf("type" to "object", "properties" to mapOf(
                    "taskId" to mapOf("type" to "string", "description" to "The unique ID of the task")
                ), "required" to listOf("taskId"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to "create_habit",
                "description" to "Initialize a new daily ritual/habit",
                "parameters" to mapOf("type" to "object", "properties" to mapOf(
                    "name" to mapOf("type" to "string", "description" to "Name of the habit"),
                    "timeBlock" to mapOf("type" to "string", "enum" to listOf("Morning", "Deep Work", "Evening", "Night"))
                ), "required" to listOf("name"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to "write_journal_entry",
                "description" to "Document a reflection or journey entry",
                "parameters" to mapOf("type" to "object", "properties" to mapOf(
                    "content" to mapOf("type" to "string", "description" to "The narrative content of the entry"),
                    "vibeRating" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 10)
                ), "required" to listOf("content"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to "get_daily_summary",
                "description" to "Retrieve the current day's progress and Flow Score",
                "parameters" to mapOf("type" to "object", "properties" to mapOf<String, Any>())
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to "analyze_energy_trends",
                "description" to "Analyze historical task data to identify peak performance windows",
                "parameters" to mapOf("type" to "object", "properties" to mapOf<String, Any>())
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to "set_focus_mode",
                "description" to "Activate internal FlowOS Focus Protocol (Zen Mode)",
                "parameters" to mapOf("type" to "object", "properties" to mapOf(
                    "enabled" to mapOf("type" to "boolean")
                ), "required" to listOf("enabled"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to "navigate_to_feature",
                "description" to "Navigate the user to a specific app protocol/tab",
                "parameters" to mapOf("type" to "object", "properties" to mapOf(
                    "tab" to mapOf("type" to "string", "enum" to listOf("state", "flow", "oracle", "journal", "settings"))
                ), "required" to listOf("tab"))
            )),
            mapOf("type" to "function", "function" to mapOf(
                "name" to "reschedule_conflicts",
                "description" to "Automatically resolve schedule overlaps for optimal flow",
                "parameters" to mapOf("type" to "object", "properties" to mapOf<String, Any>())
            ))
        )
    }
}
