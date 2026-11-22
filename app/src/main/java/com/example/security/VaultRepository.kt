package com.example.security

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.VaultDao
import com.example.data.local.VaultFolderEntity
import com.example.data.local.VaultItemEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class VaultRepository(
  private val context: Context,
  private val vaultDao: VaultDao,
  val cryptoEngine: VaultCryptoEngine = VaultCryptoEngine(),
) {
  private val appCtx = context.applicationContext
  private val vaultDir: File
    get() = File(appCtx.filesDir, "secure_vault").apply { if (!exists()) mkdirs() }
  private val encryptedManifestFile: File
    get() = File(vaultDir, "vault_manifest.nxsm")

  val folders: Flow<List<VaultFolderEntity>> = vaultDao.observeFolders()
  val items: Flow<List<VaultItemEntity>> = vaultDao.observeItems()

  suspend fun createFolder(name: String): Long = withContext(Dispatchers.IO) {
    val clean = name.trim().ifBlank { "Untitled Folder" }
    val id = vaultDao.insertFolder(VaultFolderEntity(name = clean))
    syncEncryptedManifest()
    id
  }

  suspend fun renameFolder(id: Long, name: String) = withContext(Dispatchers.IO) {
    val clean = name.trim()
    if (clean.isNotBlank()) {
      vaultDao.renameFolder(id, clean)
      syncEncryptedManifest()
    }
  }

  suspend fun deleteFolder(id: Long) = withContext(Dispatchers.IO) {
    vaultDao.moveItemsOutOfFolder(id)
    vaultDao.deleteFolder(id)
    syncEncryptedManifest()
  }

  /**
   * Imports content via Storage Access Framework / Photo Picker directly into AES-256-GCM storage.
   * Never writes plaintext temp files and never automatically deletes original source files.
   */
  suspend fun importFromUri(
    uri: Uri,
    folderId: Long = 0L,
    forcedCategory: String? = null,
  ): Result<VaultItemEntity> = withContext(Dispatchers.IO) {
    runCatching {
      val (displayName, mimeType) = resolveNameAndMime(uri)
      val category = forcedCategory ?: classifyCategory(mimeType, displayName)
      val targetFile = File(vaultDir, "${UUID.randomUUID()}.nxsv")

      val summary = appCtx.contentResolver.openInputStream(uri)?.use { input ->
        cryptoEngine.encryptStreamToFile(input, targetFile)
      } ?: error("Cannot open selected source URI")

      val entity = VaultItemEntity(
        folderId = folderId,
        displayName = displayName,
        mimeType = mimeType,
        category = category,
        encryptedFilePath = targetFile.absolutePath,
        sha256Hex = summary.sha256Hex,
        originalSizeBytes = summary.originalSizeBytes,
        chunkSizeBytes = summary.chunkSizeBytes,
      )
      val id = vaultDao.insertItem(entity)
      syncEncryptedManifest()
      entity.copy(id = id)
    }
  }

  suspend fun exportItemToUri(item: VaultItemEntity, destUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      val encFile = File(item.encryptedFilePath)
      require(encFile.exists()) { "Encrypted vault container missing" }
      appCtx.contentResolver.openOutputStream(destUri)?.use { out ->
        cryptoEngine.decryptFileToStream(encFile, out, item.sha256Hex)
      } ?: error("Cannot write to destination URI")
      Unit
    }
  }

  suspend fun decryptItemBytesForPreview(
    item: VaultItemEntity,
    maxBytes: Int = 24 * 1024 * 1024,
  ): Result<ByteArray> = withContext(Dispatchers.IO) {
    runCatching {
      val encFile = File(item.encryptedFilePath)
      require(encFile.exists()) { "Encrypted vault container missing" }
      cryptoEngine.decryptFileToBytes(encFile, item.sha256Hex, maxBytes)
    }
  }

  suspend fun verifyItemIntegrity(item: VaultItemEntity): Result<Boolean> = withContext(Dispatchers.IO) {
    runCatching {
      val encFile = File(item.encryptedFilePath)
      if (!encFile.exists()) return@runCatching false
      val nullOut = object : java.io.OutputStream() {
        override fun write(b: Int) {}
        override fun write(b: ByteArray, off: Int, len: Int) {}
      }
      val digest = cryptoEngine.decryptFileToStream(encFile, nullOut, item.sha256Hex)
      digest.equals(item.sha256Hex, ignoreCase = true)
    }
  }

  suspend fun renameItem(itemId: Long, newName: String) = withContext(Dispatchers.IO) {
    val clean = newName.trim()
    if (clean.isBlank()) return@withContext
    val current = vaultDao.getItemById(itemId) ?: return@withContext
    vaultDao.updateItem(current.copy(displayName = clean, updatedAtMs = System.currentTimeMillis()))
    syncEncryptedManifest()
  }

  suspend fun moveItemToFolder(itemId: Long, targetFolderId: Long) = withContext(Dispatchers.IO) {
    val current = vaultDao.getItemById(itemId) ?: return@withContext
    vaultDao.updateItem(current.copy(folderId = targetFolderId, updatedAtMs = System.currentTimeMillis()))
    syncEncryptedManifest()
  }

  suspend fun deleteItem(item: VaultItemEntity) = withContext(Dispatchers.IO) {
    val encFile = File(item.encryptedFilePath)
    if (encFile.exists()) {
      encFile.delete()
    }
    vaultDao.deleteItemById(item.id)
    syncEncryptedManifest()
  }

  suspend fun exportEncryptedBackup(passphrase: String, destUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
    runCatching {
      val foldersList = vaultDao.getFolders()
      val itemsList = vaultDao.getAllItems()
      val manifest = buildManifestJson(foldersList, itemsList)
      val pairs = itemsList.mapNotNull { item ->
        val f = File(item.encryptedFilePath)
        if (f.exists()) "${item.id}_${f.name}" to f else null
      }
      appCtx.contentResolver.openOutputStream(destUri)?.use { out ->
        cryptoEngine.exportEncryptedBackup(passphrase, manifest, pairs, out)
      } ?: error("Unable to open backup destination")
      pairs.size
    }
  }

  suspend fun restoreEncryptedBackup(passphrase: String, srcUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
    runCatching {
      val payload = appCtx.contentResolver.openInputStream(srcUri)?.use { input ->
        cryptoEngine.decryptBackupArchive(passphrase, input)
      } ?: error("Unable to read backup archive")

      val root = JSONObject(payload.manifestJson)
      val folderIdMap = mutableMapOf<Long, Long>()
      val foldersArr = root.optJSONArray("folders") ?: JSONArray()
      val existingFolders = vaultDao.getFolders()
      for (i in 0 until foldersArr.length()) {
        val obj = foldersArr.optJSONObject(i) ?: continue
        val oldId = obj.optLong("id")
        val name = obj.optString("name", "Restored Folder")
        val match = existingFolders.firstOrNull { it.name.equals(name, ignoreCase = true) }
        val newId = match?.id ?: vaultDao.insertFolder(VaultFolderEntity(name = name))
        folderIdMap[oldId] = newId
      }

      var restoredCount = 0
      val itemsArr = root.optJSONArray("items") ?: JSONArray()
      for (i in 0 until itemsArr.length()) {
        val obj = itemsArr.optJSONObject(i) ?: continue
        val archiveKey = obj.optString("archiveKey")
        val plainBytes = payload.decryptedEntries[archiveKey] ?: continue
        val targetFile = File(vaultDir, "${UUID.randomUUID()}.nxsv")
        val summary = plainBytes.inputStream().use { input ->
          cryptoEngine.encryptStreamToFile(input, targetFile)
        }
        val oldFolderId = obj.optLong("folderId", 0L)
        vaultDao.insertItem(
          VaultItemEntity(
            folderId = folderIdMap[oldFolderId] ?: 0L,
            displayName = obj.optString("displayName", "Restored Item"),
            mimeType = obj.optString("mimeType", "application/octet-stream"),
            category = obj.optString("category", "DOCUMENT"),
            encryptedFilePath = targetFile.absolutePath,
            sha256Hex = summary.sha256Hex,
            originalSizeBytes = summary.originalSizeBytes,
            chunkSizeBytes = summary.chunkSizeBytes,
          )
        )
        restoredCount++
      }
      syncEncryptedManifest()
      restoredCount
    }
  }

  suspend fun readEncryptedManifestStatus(): Boolean = withContext(Dispatchers.IO) {
    val file = encryptedManifestFile
    if (!file.exists()) return@withContext false
    runCatching {
      val json = cryptoEngine.decryptSmallBlob(file.readBytes()).toString(Charsets.UTF_8)
      JSONObject(json).has("items")
    }.getOrDefault(false)
  }

  private suspend fun syncEncryptedManifest() {
    runCatching {
      val manifest = buildManifestJson(vaultDao.getFolders(), vaultDao.getAllItems())
      val enc = cryptoEngine.encryptSmallBlob(manifest.toByteArray(Charsets.UTF_8))
      encryptedManifestFile.writeBytes(enc)
    }
  }

  private fun buildManifestJson(
    foldersList: List<VaultFolderEntity>,
    itemsList: List<VaultItemEntity>,
  ): String {
    val root = JSONObject()
    root.put("version", 1)
    root.put("updatedAtMs", System.currentTimeMillis())
    val fArr = JSONArray()
    foldersList.forEach { f ->
      fArr.put(JSONObject().apply {
        put("id", f.id)
        put("name", f.name)
      })
    }
    root.put("folders", fArr)

    val iArr = JSONArray()
    itemsList.forEach { it ->
      val fName = File(it.encryptedFilePath).name
      iArr.put(JSONObject().apply {
        put("id", it.id)
        put("folderId", it.folderId)
        put("displayName", it.displayName)
        put("mimeType", it.mimeType)
        put("category", it.category)
        put("sha256Hex", it.sha256Hex)
        put("originalSizeBytes", it.originalSizeBytes)
        put("archiveKey", "${it.id}_$fName")
      })
    }
    root.put("items", iArr)
    return root.toString()
  }

  private fun resolveNameAndMime(uri: Uri): Pair<String, String> {
    val mime = appCtx.contentResolver.getType(uri) ?: "application/octet-stream"
    var name: String? = null
    runCatching {
      appCtx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
          val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
          if (idx >= 0) name = c.getString(idx)
        }
      }
    }
    val fallback = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "vault_${System.currentTimeMillis()}"
    return (name ?: fallback) to mime
  }

  private fun classifyCategory(mimeType: String, name: String): String {
    val lowerMime = mimeType.lowercase()
    val lowerName = name.lowercase()
    return when {
      lowerMime.startsWith("image/") || lowerName.endsWith(".jpg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") -> "PHOTO"
      lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".webm") -> "VIDEO"
      lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") -> "AUDIO"
      else -> "DOCUMENT"
    }
  }
}
