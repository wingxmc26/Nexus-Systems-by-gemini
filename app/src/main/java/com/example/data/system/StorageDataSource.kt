package com.example.data.system

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import com.example.data.model.CategoryBreakdown
import com.example.data.model.FileCategory
import com.example.data.model.MetricValue
import com.example.data.model.SafAnalysisState
import com.example.data.model.SafFileEntry
import com.example.data.model.StorageOverview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class StorageDataSource(private val context: Context) {

  suspend fun readStorageOverview(): StorageOverview = withContext(Dispatchers.IO) {
    val dataDir = Environment.getDataDirectory()
    val stat = StatFs(dataDir.path)
    val total = stat.totalBytes.coerceAtLeast(0L)
    val available = stat.availableBytes.coerceAtLeast(0L)
    val used = (total - available).coerceAtLeast(0L)

    var externalLabel: String? = null
    var externalTotal: MetricValue<Long> = MetricValue.Unavailable("No removable/secondary volume")
    var externalAvail: MetricValue<Long> = MetricValue.Unavailable("No removable/secondary volume")

    runCatching {
      val dirs = context.getExternalFilesDirs(null)
      val secondary = dirs.drop(1).firstOrNull { it != null }
      if (secondary != null && Environment.getExternalStorageState(secondary) == Environment.MEDIA_MOUNTED) {
        val extStat = StatFs(secondary.path)
        externalLabel = "Removable / Secondary Volume"
        externalTotal = MetricValue.Available(extStat.totalBytes.coerceAtLeast(0L))
        externalAvail = MetricValue.Available(extStat.availableBytes.coerceAtLeast(0L))
      }
    }

    StorageOverview(
      internalTotalBytes = total,
      internalAvailableBytes = available,
      internalUsedBytes = used,
      externalLabel = externalLabel,
      externalTotalBytes = externalTotal,
      externalAvailableBytes = externalAvail,
    )
  }

  suspend fun analyzeSafTree(
    treeUri: Uri,
    onProgress: (Int, Long) -> Unit = { _, _ -> },
  ): SafAnalysisState = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val rootName = queryDisplayName(treeUri, rootDocId) ?: treeUri.lastPathSegment ?: "Selected Folder"

    val entries = mutableListOf<SafFileEntry>()
    val categoryTotals = FileCategory.entries.associateWith { 0L }.toMutableMap()
    val categoryCounts = FileCategory.entries.associateWith { 0 }.toMutableMap()
    var scannedFiles = 0
    var totalBytes = 0L

    val queue = ArrayDeque<Pair<String, String>>()
    queue.add(rootDocId to "")

    val projection = arrayOf(
      DocumentsContract.Document.COLUMN_DOCUMENT_ID,
      DocumentsContract.Document.COLUMN_DISPLAY_NAME,
      DocumentsContract.Document.COLUMN_MIME_TYPE,
      DocumentsContract.Document.COLUMN_SIZE,
      DocumentsContract.Document.COLUMN_LAST_MODIFIED,
    )

    while (queue.isNotEmpty() && entries.size < 15_000) {
      currentCoroutineContext().ensureActive()
      val (parentDocId, parentRelPath) = queue.removeFirst()
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

      runCatching {
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
          val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
          val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
          val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
          val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
          val modIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

          while (cursor.moveToNext()) {
            currentCoroutineContext().ensureActive()
            val docId = cursor.getString(idIdx) ?: continue
            val name = cursor.getString(nameIdx) ?: "Unnamed"
            val mime = cursor.getString(mimeIdx) ?: "application/octet-stream"
            val size = if (!cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx).coerceAtLeast(0L) else 0L
            val mod = if (!cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L
            val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
            val relPath = if (parentRelPath.isEmpty()) name else "$parentRelPath/$name"
            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val category = if (isDir) FileCategory.OTHER else classifyFile(name, mime)

            val entry = SafFileEntry(
              uriString = docUri.toString(),
              name = name,
              sizeBytes = size,
              mimeType = mime,
              isDirectory = isDir,
              lastModifiedMs = mod,
              category = category,
              relativePath = relPath,
            )
            entries.add(entry)

            if (isDir) {
              queue.add(docId to relPath)
            } else {
              scannedFiles++
              totalBytes += size
              categoryTotals[category] = (categoryTotals[category] ?: 0L) + size
              categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
              if (scannedFiles % 25 == 0) {
                onProgress(scannedFiles, totalBytes)
              }
            }
          }
        }
      }
    }

    val breakdowns = FileCategory.entries.map { cat ->
      CategoryBreakdown(
        category = cat,
        fileCount = categoryCounts[cat] ?: 0,
        totalBytes = categoryTotals[cat] ?: 0L,
      )
    }.sortedByDescending { it.totalBytes }

    val largest = entries
      .filter { !it.isDirectory }
      .sortedByDescending { it.sizeBytes }
      .take(40)

    SafAnalysisState(
      treeUriString = treeUri.toString(),
      rootName = rootName,
      isScanning = false,
      scannedCount = scannedFiles,
      totalScannedBytes = totalBytes,
      categories = breakdowns,
      largestFiles = largest,
      allEntries = entries,
      errorMessage = null,
    )
  }

  private fun queryDisplayName(treeUri: Uri, docId: String): String? {
    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    return runCatching {
      context.contentResolver.query(
        docUri,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
        null,
        null,
        null,
      )?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
      }
    }.getOrNull()
  }

  private fun classifyFile(name: String, mime: String): FileCategory {
    val lower = name.lowercase()
    return when {
      mime.startsWith("image/") || lower.endsWithAny(".jpg", ".jpeg", ".png", ".webp", ".gif", ".heic", ".raw") ->
        FileCategory.IMAGES
      mime.startsWith("video/") || lower.endsWithAny(".mp4", ".mkv", ".mov", ".avi", ".webm") ->
        FileCategory.VIDEO
      mime.startsWith("audio/") || lower.endsWithAny(".mp3", ".wav", ".flac", ".aac", ".ogg", ".m4a") ->
        FileCategory.AUDIO
      mime.contains("pdf") || mime.startsWith("text/") || mime.contains("document") ||
        lower.endsWithAny(".pdf", ".doc", ".docx", ".txt", ".md", ".csv", ".xls", ".xlsx", ".ppt", ".pptx") ->
        FileCategory.DOCUMENTS
      lower.endsWithAny(".zip", ".rar", ".7z", ".tar", ".gz", ".apk", ".aab") ->
        FileCategory.ARCHIVES
      else -> FileCategory.OTHER
    }
  }

  private fun String.endsWithAny(vararg suffixes: String): Boolean = suffixes.any { this.endsWith(it) }
}
