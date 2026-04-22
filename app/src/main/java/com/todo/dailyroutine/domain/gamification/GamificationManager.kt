package com.todo.dailyroutine.domain.gamification

import kotlin.math.floor
import kotlin.math.sqrt

object GamificationManager {
    
    fun calculateLevel(totalXp: Int): Int {
        // Level = floor(sqrt(XP / 50)) + 1
        // lvl 1: 0 XP, lvl 2: 50 XP, lvl 3: 200 XP, etc.
        return floor(sqrt(totalXp.toDouble() / 50.0)).toInt() + 1
    }

    fun getLevelTitle(level: Int): String {
        return when {
            level < 5 -> "Novice"
            level < 10 -> "Adept"
            level < 20 -> "Master"
            level < 40 -> "Grandmaster"
            else -> "Legendary Sage"
        }
    }

    fun calculateXpReward(energyRequired: Int, isBonus: Boolean = false): Int {
        val base = 10
        val multiplier = 1 + (energyRequired / 10f)
        var total = (base * multiplier).toInt()
        if (isBonus) total += 20
        return total
    }

    fun getXpProgress(totalXp: Int): Float {
        val currentLvl = calculateLevel(totalXp)
        val currentLvlStart = (currentLvl - 1) * (currentLvl - 1) * 50
        val nextLvlStart = currentLvl * currentLvl * 50
        
        if (nextLvlStart == currentLvlStart) return 0f
        return (totalXp - currentLvlStart).toFloat() / (nextLvlStart - currentLvlStart).toFloat()
    }
}
