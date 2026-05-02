package com.todo.dailyroutine.domain.scheduling

import com.todo.dailyroutine.data.local.entity.LocalTask
import com.todo.dailyroutine.data.local.entity.LocalHabit

data class ScheduleConflict(
    val type: ConflictType,
    val message: String,
    val affectedItems: List<String>
)

enum class ConflictType {
    ENERGY_OVERLOAD,
    PRIORITY_COLLISION,
    TIME_BLOCK_DENSITY
}

class ScheduleConflictDetector {

    fun detectConflicts(tasks: List<LocalTask>, habits: List<LocalHabit>): List<ScheduleConflict> {
        val conflicts = mutableListOf<ScheduleConflict>()
        val activeTasks = tasks.filter { !it.completed }
        
        // Group by Time Block
        val timeBlocks = activeTasks.groupBy { it.timeBlock }
        
        timeBlocks.forEach { (block, blockTasks) ->
            // 1. Energy Overload Detection
            val totalEnergy = blockTasks.sumOf { it.energyRequired }
            if (totalEnergy > 18) { // 18 is an arbitrary threshold for "too much" in one block
                conflicts.add(ScheduleConflict(
                    type = ConflictType.ENERGY_OVERLOAD,
                    message = "Energy requirement in $block exceeds sustainable limits ($totalEnergy/10).",
                    affectedItems = blockTasks.map { it.title }
                ))
            }
            
            // 2. Priority Collision (Multiple High Priority)
            val highPriorityTasks = blockTasks.filter { it.priority >= 3 }
            if (highPriorityTasks.size > 2) {
                conflicts.add(ScheduleConflict(
                    type = ConflictType.PRIORITY_COLLISION,
                    message = "Conflict detected: Multiple critical objectives in $block block.",
                    affectedItems = highPriorityTasks.map { it.title }
                ))
            }
            
            // 3. Density Check
            if (blockTasks.size > 5) {
                conflicts.add(ScheduleConflict(
                    type = ConflictType.TIME_BLOCK_DENSITY,
                    message = "High density in $block block. Suggest moving non-essential tasks.",
                    affectedItems = blockTasks.drop(3).map { it.title }
                ))
            }
        }
        
        return conflicts
    }
}
