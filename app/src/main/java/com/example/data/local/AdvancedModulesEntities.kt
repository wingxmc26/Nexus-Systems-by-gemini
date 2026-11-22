package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_rules")
data class AutomationRuleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val description: String = "",
  val enabled: Boolean = true,
  val triggerType: String, // TIME_DATE, BATTERY, CHARGING, SYSTEM_EVENT, NFC
  val triggerConfigJson: String,
  val actionsJson: String,
  val isTemplate: Boolean = false,
  val runCount: Int = 0,
  val lastTriggeredMs: Long = 0L,
  val createdAtMs: Long = System.currentTimeMillis(),
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "smart_rules")
data class SmartRuleEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val description: String = "",
  val enabled: Boolean = true,
  val priority: Int = 50,
  val cooldownMs: Long = 60_000L,
  val conditionLogicJson: String,
  val thenActionsJson: String,
  val elseActionsJson: String = "[]",
  val lastEvaluatedMs: Long = 0L,
  val lastTriggeredMs: Long = 0L,
  val lastOutcome: String = "",
  val lastConditionSignature: String = "",
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val sourceType: String, // AUTOMATION, SMART_RULE, GESTURE, OVERLAY, SYSTEM
  val sourceId: Long = 0L,
  val sourceName: String,
  val success: Boolean,
  val branch: String = "RUN", // RUN, THEN, ELSE, SKIP_COOLDOWN, SKIP_DUPLICATE, ERROR
  val details: String = "",
  val errorMessage: String? = null,
  val executedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "gesture_profiles")
data class GestureProfileEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val isActive: Boolean = false,
  val sensitivity: Float = 1.0f, // 0.4f .. 2.0f
  val inAppEnabled: Boolean = true,
  val systemOverlayEnabled: Boolean = false,
  val zoneWidthDp: Int = 28,
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "gesture_mappings")
data class GestureMappingEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val profileId: Long,
  val gestureType: String, // SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT, DOUBLE_TAP, LONG_PRESS, TWO_FINGER_TAP, TWO_FINGER_SWIPE_UP, TWO_FINGER_SWIPE_DOWN
  val activationZone: String = "FULL_SCREEN", // FULL_SCREEN, LEFT_EDGE, RIGHT_EDGE, BOTTOM_ZONE, TOP_ZONE
  val actionsJson: String,
  val label: String,
  val enabled: Boolean = true,
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "shared_shortcuts")
data class SharedShortcutEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val label: String,
  val category: String = "Quick Actions", // NEXUS, Apps, Quick Actions, Combos
  val isFavorite: Boolean = false,
  val sortOrder: Int = 0,
  val actionsJson: String, // JSON array of NexusActionSpec
  val targetSurface: String = "BOTH", // BOTH, FLOATING, EDGE
  val accentColorArgb: Long = 0xFFE53935L,
  val iconKey: String = "bolt",
)

@Entity(tableName = "quick_notes")
data class QuickNoteEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val content: String,
  val pinned: Boolean = false,
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "vault_folders")
data class VaultFolderEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val createdAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "vault_items")
data class VaultItemEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val folderId: Long = 0L,
  val displayName: String,
  val mimeType: String,
  val category: String, // PHOTO, VIDEO, AUDIO, DOCUMENT
  val encryptedFilePath: String,
  val sha256Hex: String,
  val originalSizeBytes: Long,
  val chunkSizeBytes: Int = 262_144,
  val createdAtMs: Long = System.currentTimeMillis(),
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "app_lock_profiles")
data class AppLockProfileEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val isActive: Boolean = false,
  val protectedPackagesJson: String = "[]",
  val protectedNexusRoutesJson: String = "[]",
  val autoLockDelayMs: Long = 30_000L,
  val lockOnScreenOff: Boolean = true,
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "auth_history")
data class AuthHistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val targetType: String, // VAULT, APP_LOCK_NEXUS, APP_LOCK_EXTERNAL, SECURITY_SETUP
  val targetName: String,
  val method: String, // PIN, BIOMETRIC, SYSTEM
  val success: Boolean,
  val failureReason: String = "",
  val timestampMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "saved_bookmarks")
data class SavedBookmarkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val url: String,
  val createdAtMs: Long = System.currentTimeMillis(),
)
