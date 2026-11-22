package com.example.security

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.IOException

/**
 * Custom Media3 DataSource that decrypts framed AES-256-GCM (`NXSV1`) vault files strictly in RAM
 * chunk-by-chunk with full seek support — never writing plaintext temporary media files to disk.
 */
@OptIn(UnstableApi::class)
class VaultFramedMediaDataSource(
  private val sourceFile: File,
  private val cryptoEngine: VaultCryptoEngine,
) : BaseDataSource(/* isNetwork = */ false) {

  private var dataSpec: DataSpec? = null
  private var header: VaultCryptoEngine.FramedHeaderInfo? = null
  private var readPosition: Long = 0L
  private var bytesRemaining: Long = 0L
  private var opened = false

  private var cachedFrameIndex = -1
  private var cachedFrameBytes: ByteArray = ByteArray(0)

  override fun open(dataSpec: DataSpec): Long {
    this.dataSpec = dataSpec
    transferInitializing(dataSpec)
    val info = try {
      cryptoEngine.indexFramedFile(sourceFile)
    } catch (t: Throwable) {
      throw IOException("Failed to open encrypted vault stream", t)
    }
    header = info

    val total = info.totalPlainSize
    if (dataSpec.position > total) {
      throw IOException("Requested position ${dataSpec.position} beyond decrypted size $total")
    }
    readPosition = dataSpec.position
    bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
      minOf(dataSpec.length, total - readPosition)
    } else {
      total - readPosition
    }
    opened = true
    transferStarted(dataSpec)
    return bytesRemaining
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length == 0) return 0
    if (bytesRemaining <= 0L) return C.RESULT_END_OF_INPUT
    val info = header ?: return C.RESULT_END_OF_INPUT

    val frameIdx = info.frames.indexOfFirst { f ->
      readPosition >= f.plainStartOffset && readPosition < (f.plainStartOffset + f.plainLength)
    }
    if (frameIdx == -1) return C.RESULT_END_OF_INPUT

    val frame = info.frames[frameIdx]
    if (frameIdx != cachedFrameIndex) {
      cachedFrameBytes = try {
        cryptoEngine.decryptSingleFrame(sourceFile, frame)
      } catch (t: Throwable) {
        throw IOException("AES-GCM authentication failed during media stream", t)
      }
      cachedFrameIndex = frameIdx
    }

    val offsetInFrame = (readPosition - frame.plainStartOffset).toInt()
    val availInFrame = (cachedFrameBytes.size - offsetInFrame).coerceAtLeast(0)
    if (availInFrame == 0) return C.RESULT_END_OF_INPUT

    val toCopy = minOf(length.toLong(), bytesRemaining, availInFrame.toLong()).toInt()
    System.arraycopy(cachedFrameBytes, offsetInFrame, buffer, offset, toCopy)
    readPosition += toCopy
    bytesRemaining -= toCopy
    bytesTransferred(toCopy)
    return toCopy
  }

  override fun getUri(): Uri? = dataSpec?.uri ?: Uri.fromFile(sourceFile)

  override fun close() {
    cachedFrameBytes.fill(0)
    cachedFrameBytes = ByteArray(0)
    cachedFrameIndex = -1
    header = null
    dataSpec = null
    if (opened) {
      opened = false
      transferEnded()
    }
  }

  class Factory(
    private val sourceFile: File,
    private val cryptoEngine: VaultCryptoEngine,
  ) : DataSource.Factory {
    override fun createDataSource(): DataSource = VaultFramedMediaDataSource(sourceFile, cryptoEngine)
  }
}
