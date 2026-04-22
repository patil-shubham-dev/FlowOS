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
    sortOrder = sortOrder
)

fun LocalTask.toModel() = TaskItem(
    id = id,
    userId = userId,
    title = title,
    category = category,
    completed = completed,
    priority = priority,
    sortOrder = sortOrder
)

fun HabitItem.toEntity() = LocalHabit(id, userId, name, streak, completedToday)
fun LocalHabit.toModel() = HabitItem(id, userId, name, streak, completedToday)
