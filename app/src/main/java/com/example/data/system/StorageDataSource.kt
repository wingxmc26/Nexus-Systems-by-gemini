package com.example.data.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.example.data.model.CategoryBreakdown
import com.example.data.model.DirectFileEntry
import com.example.data.model.DuplicateFileGroup
import com.example.data.model.FileCategory
import com.example.data.model.MetricValue
import com.example.data.model.SafAnalysisState
import com.example.data.model.SafFileEntry
import com.example.data.model.StorageOverview
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class StorageDataSource(private val context: Context) {

  fun getRootStoragePath(): String = Environment.getExternalStorageDirectory().absolutePath

  fun getDownloadsPath(): String =
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

  fun isProtectedAndroidSubdir(path: String): Boolean {
    val norm = path.replace('\\', '/')
    return norm.contains("/Android/data") || norm.contains("/Android/obb")
  }

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

  suspend fun listDirectDirectory(path: String): Result<List<DirectFileEntry>> = withContext(Dispatchers.IO) {
    runCatching {
      val dir = File(path)
      require(dir.exists() && dir.isDirectory) { "Directory is not accessible" }
      val restricted = isProtectedAndroidSubdir(dir.absolutePath)
      val children = dir.listFiles()
        ?: if (restricted) error("Android 11+ restricts direct access to /Android/data and /Android/obb even with All Files Access.")
        else emptyArray()

      children.map { f ->
        val isDir = f.isDirectory
        val isSubRestricted = isDir && isProtectedAndroidSubdir(f.absolutePath)
        val childCount = if (isDir && !isSubRestricted) runCatching { f.listFiles()?.size ?: 0 }.getOrDefault(0) else 0
        DirectFileEntry(
          path = f.absolutePath,
          name = f.name,
          sizeBytes = if (isDir) 0L else f.length().coerceAtLeast(0L),
          isDirectory = isDir,
          lastModifiedMs = f.lastModified(),
          category = if (isDir) FileCategory.OTHER else classifyFile(f.name, ""),
          isRestricted = isSubRestricted,
          childCount = childCount,
        )
      }
    }
  }

  suspend fun scanDirectOverviewStats(rootPath: String = getRootStoragePath()): Triple<List<CategoryBreakdown>, List<DirectFileEntry>, List<DirectFileEntry>> =
    withContext(Dispatchers.IO) {
      val catTotals = FileCategory.entries.associateWith { 0L }.toMutableMap()
      val catCounts = FileCategory.entries.associateWith { 0 }.toMutableMap()
      val recent = mutableListOf<DirectFileEntry>()
      val large = mutableListOf<DirectFileEntry>()
      val queue = ArrayDeque<File>()
      queue.add(File(rootPath))
      var visited = 0

      while (queue.isNotEmpty() && visited < 8000) {
        currentCoroutineContext().ensureActive()
        val cur = queue.removeFirst()
        if (isProtectedAndroidSubdir(cur.absolutePath)) continue
        val files = runCatching { cur.listFiles() }.getOrNull() ?: continue
        for (f in files) {
          visited++
          if (f.isDirectory) {
            if (!isProtectedAndroidSubdir(f.absolutePath)) queue.add(f)
          } else {
            val sz = f.length().coerceAtLeast(0L)
            val cat = classifyFile(f.name, "")
            catTotals[cat] = (catTotals[cat] ?: 0L) + sz
            catCounts[cat] = (catCounts[cat] ?: 0) + 1
            val entry = DirectFileEntry(
              path = f.absolutePath,
              name = f.name,
              sizeBytes = sz,
              isDirectory = false,
              lastModifiedMs = f.lastModified(),
              category = cat,
            )
            recent.add(entry)
            if (sz >= 10L * 1024L * 1024L) large.add(entry)
          }
          if (visited >= 8000) break
        }
      }

      val breakdowns = FileCategory.entries.map { cat ->
        CategoryBreakdown(cat, catCounts[cat] ?: 0, catTotals[cat] ?: 0L)
      }.sortedByDescending { it.totalBytes }

      Triple(
        breakdowns,
        recent.sortedByDescending { it.lastModifiedMs }.take(40),
        large.sortedByDescending { it.sizeBytes }.take(40),
      )
    }

  suspend fun createDirectFolder(parentPath: String, folderName: String): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val clean = folderName.trim().replace("/", "_")
      require(clean.isNotEmpty()) { "Folder name cannot be empty" }
      val target = File(parentPath, clean)
      require(!target.exists()) { "A file or folder named '$clean' already exists" }
      check(target.mkdirs()) { "Unable to create folder in this location" }
      target
    }
  }

  suspend fun renameDirectFile(path: String, newName: String): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val src = File(path)
      val clean = newName.trim().replace("/", "_")
      require(src.exists()) { "Source file no longer exists" }
      require(clean.isNotEmpty()) { "New name cannot be empty" }
      val dst = File(src.parentFile, clean)
      require(!dst.exists()) { "Destination '$clean' already exists" }
      check(src.renameTo(dst)) { "Rename rejected by filesystem" }
      dst
    }
  }

  suspend fun copyFileOrDir(srcPath: String, destDirPath: String): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val src = File(srcPath)
      val destDir = File(destDirPath)
      require(src.exists() && destDir.isDirectory) { "Invalid source or destination directory" }
      var target = File(destDir, src.name)
      if (target.exists()) {
        val base = src.nameWithoutExtension
        val ext = src.extension.let { if (it.isNotEmpty()) ".$it" else "" }
        target = File(destDir, "${base}_copy_${System.currentTimeMillis() % 10000}$ext")
      }
      if (src.isDirectory) {
        src.copyRecursively(target, overwrite = false)
      } else {
        src.copyTo(target, overwrite = false)
      }
      target
    }
  }

  suspend fun moveFileOrDir(srcPath: String, destDirPath: String): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val src = File(srcPath)
      val destDir = File(destDirPath)
      require(src.exists() && destDir.isDirectory) { "Invalid source or destination directory" }
      val target = File(destDir, src.name)
      require(!target.exists()) { "Target '${src.name}' already exists in destination" }
      if (!src.renameTo(target)) {
        if (src.isDirectory) {
          src.copyRecursively(target, overwrite = false)
          src.deleteRecursively()
        } else {
          src.copyTo(target, overwrite = false)
          src.delete()
        }
      }
      target
    }
  }

  suspend fun deleteDirectPaths(paths: List<String>): Result<Int> = withContext(Dispatchers.IO) {
    runCatching {
      var count = 0
      for (p in paths) {
        val f = File(p)
        val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
        if (ok) count++
      }
      count
    }
  }

  suspend fun extractZipArchive(zipPath: String): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val zipFile = File(zipPath)
      require(zipFile.exists() && zipFile.isFile) { "Archive not found" }
      val outDir = File(zipFile.parentFile, zipFile.nameWithoutExtension)
      if (!outDir.exists()) outDir.mkdirs()
      val canonicalDest = outDir.canonicalPath + File.separator

      ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
        var entry: ZipEntry? = zis.nextEntry
        val buf = ByteArray(16 * 1024)
        while (entry != null) {
          val outFile = File(outDir, entry.name)
          // Zip-Slip path traversal protection
          if (!outFile.canonicalPath.startsWith(canonicalDest)) {
            error("Blocked unsafe archive entry path: ${entry.name}")
          }
          if (entry.isDirectory) {
            outFile.mkdirs()
          } else {
            outFile.parentFile?.mkdirs()
            FileOutputStream(outFile).use { fos ->
              var len: Int
              while (zis.read(buf).also { len = it } != -1) {
                fos.write(buf, 0, len)
              }
            }
          }
          zis.closeEntry()
          entry = zis.nextEntry
        }
      }
      outDir
    }
  }

  suspend fun compressFilesToZip(paths: List<String>, outputZipPath: String): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
      val outFile = File(outputZipPath)
      ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
        for (p in paths) {
          val src = File(p)
          if (src.exists()) {
            addFileToZip(zos, src, src.name)
          }
        }
      }
      outFile
    }
  }

  private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
    if (file.isDirectory) {
      file.listFiles()?.forEach { child ->
        addFileToZip(zos, child, "$entryName/${child.name}")
      }
    } else {
      zos.putNextEntry(ZipEntry(entryName))
      FileInputStream(file).use { fis ->
        val buf = ByteArray(16 * 1024)
        var len: Int
        while (fis.read(buf).also { len = it } != -1) {
          zos.write(buf, 0, len)
        }
      }
      zos.closeEntry()
    }
  }

  fun buildShareIntentForFile(filePath: String): Result<Intent> {
    return runCatching {
      val file = File(filePath)
      require(file.exists() && file.isFile) { "Select a readable file to share" }
      val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
      val share = Intent(Intent.ACTION_SEND).apply {
        type = guessMimeType(file.name)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      Intent.createChooser(share, "Share ${file.name}").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    }
  }

  suspend fun calculateFolderSize(path: String): Long = withContext(Dispatchers.IO) {
    var total = 0L
    val queue = ArrayDeque<File>()
    queue.add(File(path))
    var count = 0
    while (queue.isNotEmpty() && count < 10_000) {
      val cur = queue.removeFirst()
      if (isProtectedAndroidSubdir(cur.absolutePath)) continue
      cur.listFiles()?.forEach { f ->
        count++
        if (f.isDirectory) queue.add(f) else total += f.length().coerceAtLeast(0L)
      }
    }
    total
  }

  fun buildDownloadsInitialUri(): Uri? {
    return runCatching {
      DocumentsContract.buildRootUri("com.android.providers.downloads.documents", "downloads")
    }.getOrNull()
  }

  suspend fun analyzeSafTree(
    treeUri: Uri,
    onProgress: (Int, Long) -> Unit = { _, _ -> },
  ): SafAnalysisState = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
    val rootName = queryDisplayName(treeUri, rootDocId) ?: treeUri.lastPathSegment ?: "Selected Folder"

    val entries = mutableListOf<SafFileEntry>()
    val emptyFolders = mutableListOf<SafFileEntry>()
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

    while (queue.isNotEmpty() && entries.size < 12_000) {
      currentCoroutineContext().ensureActive()
      val (parentDocId, parentRelPath) = queue.removeFirst()
      val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

      var childCount = 0
      runCatching {
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
          val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
          val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
          val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
          val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
          val modIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

          while (cursor.moveToNext()) {
            currentCoroutineContext().ensureActive()
            childCount++
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

      if (childCount == 0 && parentRelPath.isNotEmpty()) {
        entries.firstOrNull { it.isDirectory && it.relativePath == parentRelPath }?.let {
          emptyFolders.add(it)
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

    val nonDirFiles = entries.filter { !it.isDirectory }
    val largest = nonDirFiles.sortedByDescending { it.sizeBytes }.take(40)
    val duplicates = findExactDuplicates(nonDirFiles)

    SafAnalysisState(
      treeUriString = treeUri.toString(),
      rootName = rootName,
      isScanning = false,
      scannedCount = scannedFiles,
      totalScannedBytes = totalBytes,
      categories = breakdowns,
      largestFiles = largest,
      duplicateGroups = duplicates,
      emptyFolders = emptyFolders,
      allEntries = entries,
      errorMessage = null,
    )
  }

  suspend fun deleteSafDocument(uriString: String): Boolean = withContext(Dispatchers.IO) {
    runCatching {
      DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(uriString))
    }.getOrDefault(false)
  }

  private suspend fun findExactDuplicates(files: List<SafFileEntry>): List<DuplicateFileGroup> {
    val sizeCandidates = files
      .filter { it.sizeBytes > 0L && it.sizeBytes <= 100L * 1024L * 1024L }
      .groupBy { it.sizeBytes }
      .filter { it.value.size > 1 }

    val groups = mutableListOf<DuplicateFileGroup>()
    for ((size, candidates) in sizeCandidates) {
      currentCoroutineContext().ensureActive()
      val byHash = mutableMapOf<String, MutableList<SafFileEntry>>()
      for (file in candidates) {
        currentCoroutineContext().ensureActive()
        val hash = computeSha256(Uri.parse(file.uriString)) ?: continue
        byHash.getOrPut(hash) { mutableListOf() }.add(file)
      }
      byHash.forEach { (hash, matched) ->
        if (matched.size > 1) {
          groups.add(DuplicateFileGroup(sha256 = hash, sizeBytes = size, files = matched))
        }
      }
    }
    return groups.sortedByDescending { it.sizeBytes * it.files.size }
  }

  private fun computeSha256(uri: Uri): String? {
    return runCatching {
      val digest = MessageDigest.getInstance("SHA-256")
      context.contentResolver.openInputStream(uri)?.use { stream ->
        val buf = ByteArray(16 * 1024)
        var read: Int
        while (stream.read(buf).also { read = it } != -1) {
          digest.update(buf, 0, read)
        }
      } ?: return null
      digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()
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

  private fun guessMimeType(name: String): String {
    val lower = name.lowercase()
    return when {
      lower.endsWith(".mp4") || lower.endsWith(".mkv") -> "video/*"
      lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") -> "audio/*"
      lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") -> "image/*"
      lower.endsWith(".pdf") -> "application/pdf"
      lower.endsWith(".zip") -> "application/zip"
      lower.endsWith(".txt") || lower.endsWith(".html") -> "text/plain"
      else -> "*/*"
    }
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
