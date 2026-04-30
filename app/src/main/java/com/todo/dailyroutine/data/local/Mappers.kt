package com.todo.dailyroutine.data.local

import com.todo.dailyroutine.data.local.entity.LocalHabit
import com.todo.dailyroutine.data.local.entity.LocalTask
import com.todo.dailyroutine.data.model.HabitItem
import com.todo.dailyroutine.data.model.TaskItem

fun TaskItem.toEntity() = LocalTask(
    id = id,
    userId = userId,
    title = title,
    category = category,
    completed = completed,
    priority = priority,
    energyRequired = energyRequired,
    timeBlock = timeBlock,
    scheduledTime = scheduledTime,
    sortOrder = sortOrder,
    lastUpdated = System.currentTimeMillis(),
    syncStatus = 0
)

fun LocalTask.toModel() = TaskItem(
    id = id,
    userId = userId,
    title = title,
    category = category,
    completed = completed,
    priority = priority,
    energyRequired = energyRequired,
    timeBlock = timeBlock,
    scheduledTime = scheduledTime,
    sortOrder = sortOrder
)

fun HabitItem.toEntity() = LocalHabit(
    id = id,
    userId = userId,
    name = name,
    streak = streak,
    completedToday = completedToday,
    timeBlock = timeBlock,
    scheduledTime = scheduledTime,
    sortOrder = sortOrder,
    lastUpdated = System.currentTimeMillis(),
    syncStatus = 0
)

fun LocalHabit.toModel() = HabitItem(
    id = id,
    userId = userId,
    name = name,
    streak = streak,
    completedToday = completedToday,
    timeBlock = timeBlock,
    scheduledTime = scheduledTime,
    sortOrder = sortOrder
)
