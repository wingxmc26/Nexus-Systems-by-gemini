package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteActivityDao {
  @Query("SELECT * FROM favorite_activities ORDER BY createdAtMs DESC")
  fun observeFavorites(): Flow<List<FavoriteActivityEntity>>

  @Query("SELECT * FROM favorite_activities ORDER BY createdAtMs DESC LIMIT 4")
  suspend fun getTopFavorites(): List<FavoriteActivityEntity>

  @Query("SELECT * FROM favorite_activities WHERE packageName = :pkg AND ((activityClassName IS NULL AND :cls IS NULL) OR activityClassName = :cls) LIMIT 1")
  suspend fun findMatching(pkg: String, cls: String?): FavoriteActivityEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(favorite: FavoriteActivityEntity): Long

  @Update
  suspend fun update(favorite: FavoriteActivityEntity)

  @Query("DELETE FROM favorite_activities WHERE id = :id")
  suspend fun deleteById(id: Long)
}
