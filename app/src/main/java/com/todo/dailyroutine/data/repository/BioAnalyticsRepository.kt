package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.data.local.dao.BioDataDao
import com.todo.dailyroutine.data.local.entity.LocalBioData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class BioAnalyticsRepository(private val bioDataDao: BioDataDao) {

    fun getBioDataForDate(userId: String, date: String): Flow<LocalBioData?> {
        return bioDataDao.getBioDataByDate(userId, date)
    }

    suspend fun syncWithHealthProvider(userId: String) = withContext(Dispatchers.IO) {
        // MOCK: In a real implementation, this would call Google Fit API
        // or Health Connect API using OAuth 2.0.
        
        val mockData = LocalBioData(
            userId = userId,
            date = LocalDate.now().toString(),
            steps = (4000..12000).random(),
            sleepMinutes = (360..540).random(),
            avgHeartRate = (60..85).random(),
            hrvScore = (40..90).random()
        )
        
        bioDataDao.insertBioData(mockData)
    }
}
