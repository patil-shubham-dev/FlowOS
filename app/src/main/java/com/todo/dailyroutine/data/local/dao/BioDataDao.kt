package com.todo.dailyroutine.data.local.dao

import androidx.room.*
import com.todo.dailyroutine.data.local.entity.LocalBioData
import kotlinx.coroutines.flow.Flow

@Dao
interface BioDataDao {
    @Query("SELECT * FROM bio_data WHERE userId = :userId AND date = :date LIMIT 1")
    fun getBioDataByDate(userId: String, date: String): Flow<LocalBioData?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBioData(data: LocalBioData)

    @Query("DELETE FROM bio_data WHERE userId = :userId")
    suspend fun clearBioData(userId: String)
}
