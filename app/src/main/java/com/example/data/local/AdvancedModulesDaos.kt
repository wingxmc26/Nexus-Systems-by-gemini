package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationRuleDao {
  @Query("SELECT * FROM automation_rules WHERE isTemplate = 0 ORDER BY updatedAtMs DESC")
  fun observeRules(): Flow<List<AutomationRuleEntity>>

  @Query("SELECT * FROM automation_rules WHERE isTemplate = 0 ORDER BY updatedAtMs DESC")
  suspend fun getAllRules(): List<AutomationRuleEntity>

  @Query("SELECT * FROM automation_rules WHERE isTemplate = 0 AND enabled = 1")
  suspend fun getEnabledRules(): List<AutomationRuleEntity>

  @Query("SELECT * FROM automation_rules WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): AutomationRuleEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(rule: AutomationRuleEntity): Long

  @Update
  suspend fun update(rule: AutomationRuleEntity)

  @Query("UPDATE automation_rules SET enabled = :enabled, updatedAtMs = :ts WHERE id = :id")
  suspend fun setEnabled(id: Long, enabled: Boolean, ts: Long = System.currentTimeMillis())

  @Query("UPDATE automation_rules SET runCount = runCount + 1, lastTriggeredMs = :ts WHERE id = :id")
  suspend fun recordTriggered(id: Long, ts: Long = System.currentTimeMillis())

  @Query("DELETE FROM automation_rules WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface SmartRuleDao {
  @Query("SELECT * FROM smart_rules ORDER BY priority DESC, updatedAtMs DESC")
  fun observeAll(): Flow<List<SmartRuleEntity>>

  @Query("SELECT * FROM smart_rules WHERE enabled = 1 ORDER BY priority DESC, id ASC")
  suspend fun getEnabledOrdered(): List<SmartRuleEntity>

  @Query("SELECT * FROM smart_rules WHERE id = :id LIMIT 1")
  suspend fun getById(id: Long): SmartRuleEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(rule: SmartRuleEntity): Long

  @Update
  suspend fun update(rule: SmartRuleEntity)

  @Query("UPDATE smart_rules SET enabled = :enabled, updatedAtMs = :ts WHERE id = :id")
  suspend fun setEnabled(id: Long, enabled: Boolean, ts: Long = System.currentTimeMillis())

  @Query(
    "UPDATE smart_rules SET lastEvaluatedMs = :evalTs, lastTriggeredMs = :trigTs, lastOutcome = :outcome, lastConditionSignature = :sig WHERE id = :id"
  )
  suspend fun updateEvalState(
    id: Long,
    evalTs: Long,
    trigTs: Long,
    outcome: String,
    sig: String,
  )

  @Query("DELETE FROM smart_rules WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface ExecutionLogDao {
  @Query("SELECT * FROM execution_logs ORDER BY executedAtMs DESC LIMIT 200")
  fun observeRecent(): Flow<List<ExecutionLogEntity>>

  @Query("SELECT * FROM execution_logs WHERE sourceType = :type ORDER BY executedAtMs DESC LIMIT 120")
  fun observeByType(type: String): Flow<List<ExecutionLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(log: ExecutionLogEntity): Long

  @Query("DELETE FROM execution_logs")
  suspend fun clearAll()
}

@Dao
interface GestureStudioDao {
  @Query("SELECT * FROM gesture_profiles ORDER BY isActive DESC, updatedAtMs DESC")
  fun observeProfiles(): Flow<List<GestureProfileEntity>>

  @Query("SELECT * FROM gesture_profiles ORDER BY isActive DESC, id ASC")
  suspend fun getProfiles(): List<GestureProfileEntity>

  @Query("SELECT * FROM gesture_profiles WHERE isActive = 1 LIMIT 1")
  suspend fun getActiveProfile(): GestureProfileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertProfile(profile: GestureProfileEntity): Long

  @Update
  suspend fun updateProfile(profile: GestureProfileEntity)

  @Query("UPDATE gesture_profiles SET isActive = 0")
  suspend fun clearActiveProfiles()

  @Query("UPDATE gesture_profiles SET isActive = 1, updatedAtMs = :ts WHERE id = :id")
  suspend fun activateProfile(id: Long, ts: Long = System.currentTimeMillis())

  @Query("DELETE FROM gesture_profiles WHERE id = :id")
  suspend fun deleteProfile(id: Long)

  @Query("SELECT * FROM gesture_mappings WHERE profileId = :profileId ORDER BY updatedAtMs DESC")
  fun observeMappings(profileId: Long): Flow<List<GestureMappingEntity>>

  @Query("SELECT * FROM gesture_mappings WHERE profileId = :profileId AND enabled = 1")
  suspend fun getEnabledMappings(profileId: Long): List<GestureMappingEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertMapping(mapping: GestureMappingEntity): Long

  @Update
  suspend fun updateMapping(mapping: GestureMappingEntity)

  @Query("DELETE FROM gesture_mappings WHERE id = :id")
  suspend fun deleteMapping(id: Long)

  @Query("DELETE FROM gesture_mappings WHERE profileId = :profileId")
  suspend fun deleteMappingsForProfile(profileId: Long)
}

@Dao
interface SharedShortcutDao {
  @Query("SELECT * FROM shared_shortcuts ORDER BY isFavorite DESC, sortOrder ASC, id ASC")
  fun observeAll(): Flow<List<SharedShortcutEntity>>

  @Query("SELECT * FROM shared_shortcuts ORDER BY isFavorite DESC, sortOrder ASC, id ASC")
  suspend fun getAll(): List<SharedShortcutEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(shortcut: SharedShortcutEntity): Long

  @Update
  suspend fun update(shortcut: SharedShortcutEntity)

  @Query("UPDATE shared_shortcuts SET isFavorite = :favorite WHERE id = :id")
  suspend fun setFavorite(id: Long, favorite: Boolean)

  @Query("UPDATE shared_shortcuts SET sortOrder = :sortOrder WHERE id = :id")
  suspend fun setSortOrder(id: Long, sortOrder: Int)

  @Query("DELETE FROM shared_shortcuts WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface QuickNoteDao {
  @Query("SELECT * FROM quick_notes ORDER BY pinned DESC, updatedAtMs DESC")
  fun observeAll(): Flow<List<QuickNoteEntity>>

  @Query("SELECT * FROM quick_notes ORDER BY pinned DESC, updatedAtMs DESC")
  suspend fun getAll(): List<QuickNoteEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(note: QuickNoteEntity): Long

  @Query("DELETE FROM quick_notes WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface VaultDao {
  @Query("SELECT * FROM vault_folders ORDER BY name ASC")
  fun observeFolders(): Flow<List<VaultFolderEntity>>

  @Query("SELECT * FROM vault_folders ORDER BY name ASC")
  suspend fun getFolders(): List<VaultFolderEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertFolder(folder: VaultFolderEntity): Long

  @Query("UPDATE vault_folders SET name = :name WHERE id = :id")
  suspend fun renameFolder(id: Long, name: String)

  @Query("DELETE FROM vault_folders WHERE id = :id")
  suspend fun deleteFolder(id: Long)

  @Query("UPDATE vault_items SET folderId = 0 WHERE folderId = :folderId")
  suspend fun moveItemsOutOfFolder(folderId: Long)

  @Query("SELECT * FROM vault_items ORDER BY createdAtMs DESC")
  fun observeItems(): Flow<List<VaultItemEntity>>

  @Query("SELECT * FROM vault_items ORDER BY createdAtMs DESC")
  suspend fun getAllItems(): List<VaultItemEntity>

  @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
  suspend fun getItemById(id: Long): VaultItemEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertItem(item: VaultItemEntity): Long

  @Update
  suspend fun updateItem(item: VaultItemEntity)

  @Query("DELETE FROM vault_items WHERE id = :id")
  suspend fun deleteItemById(id: Long)
}

@Dao
interface AppLockDao {
  @Query("SELECT * FROM app_lock_profiles ORDER BY isActive DESC, updatedAtMs DESC")
  fun observeProfiles(): Flow<List<AppLockProfileEntity>>

  @Query("SELECT * FROM app_lock_profiles ORDER BY isActive DESC, id ASC")
  suspend fun getAllProfiles(): List<AppLockProfileEntity>

  @Query("SELECT * FROM app_lock_profiles WHERE isActive = 1 LIMIT 1")
  suspend fun getActiveProfile(): AppLockProfileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertProfile(profile: AppLockProfileEntity): Long

  @Update
  suspend fun updateProfile(profile: AppLockProfileEntity)

  @Query("UPDATE app_lock_profiles SET isActive = 0")
  suspend fun clearActiveProfiles()

  @Query("UPDATE app_lock_profiles SET isActive = 1, updatedAtMs = :ts WHERE id = :id")
  suspend fun activateProfile(id: Long, ts: Long = System.currentTimeMillis())

  @Query("DELETE FROM app_lock_profiles WHERE id = :id")
  suspend fun deleteProfile(id: Long)

  @Query("SELECT * FROM auth_history ORDER BY timestampMs DESC LIMIT 100")
  fun observeAuthHistory(): Flow<List<AuthHistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAuthHistory(entry: AuthHistoryEntity): Long

  @Query("DELETE FROM auth_history")
  suspend fun clearAuthHistory()
}

@Dao
interface SavedBookmarkDao {
  @Query("SELECT * FROM saved_bookmarks ORDER BY createdAtMs DESC")
  fun observeAll(): Flow<List<SavedBookmarkEntity>>

  @Query("SELECT * FROM saved_bookmarks ORDER BY createdAtMs DESC")
  suspend fun getAll(): List<SavedBookmarkEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(bookmark: SavedBookmarkEntity): Long

  @Query("DELETE FROM saved_bookmarks WHERE id = :id")
  suspend fun deleteById(id: Long)
}
