package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

interface VaultKeyProvider {
  fun getOrCreateMasterKey(): SecretKey
  val isHardwareKeystoreBacked: Boolean
}

class AndroidKeystoreVaultKeyProvider(
  private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : VaultKeyProvider {

  override val isHardwareKeystoreBacked: Boolean = true

  override fun getOrCreateMasterKey(): SecretKey {
    val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = ks.getKey(keyAlias, null) as? SecretKey
    if (existing != null) return existing

    val spec = KeyGenParameterSpec.Builder(
      keyAlias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .setRandomizedEncryptionRequired(true)
      .build()

    val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    keyGen.init(spec)
    return keyGen.generateKey()
  }

  companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val DEFAULT_KEY_ALIAS = "nexus_vault_aes_master_v1"
  }
}

/**
 * Framed AES-256-GCM cryptographic engine backed by Android Keystore.
 *
 * Security invariants:
 * - Master key resides exclusively in Android Keystore (never persisted in plaintext).
 * - Every encrypted chunk and metadata blob receives a fresh 12-byte (96-bit) GCM nonce.
 * - Full 128-bit GCM authentication tag verifies integrity on every chunk read.
 * - Streaming chunk format (`NXSV1`) prevents plaintext temporary files even on large videos.
 */
class VaultCryptoEngine(
  private val keyProvider: VaultKeyProvider = AndroidKeystoreVaultKeyProvider(),
) {
  companion object {
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'X'.code.toByte(), 'S'.code.toByte(), 'V'.code.toByte(), 1)
    private val BACKUP_MAGIC = byteArrayOf('N'.code.toByte(), 'X'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), 1)
    const val DEFAULT_CHUNK_SIZE = 262_144 // 256 KB
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = 16
    private const val PBKDF2_ITERATIONS = 120_000
    private val secureRandom = SecureRandom()
  }

  data class EncryptionSummary(
    val originalSizeBytes: Long,
    val sha256Hex: String,
    val chunkSizeBytes: Int,
  )

  data class FrameIndexEntry(
    val fileOffset: Long,
    val plainStartOffset: Long,
    val plainLength: Int,
  )

  data class FramedHeaderInfo(
    val totalPlainSize: Long,
    val chunkSize: Int,
    val frames: List<FrameIndexEntry>,
  )

  fun encryptStreamToFile(
    inputStream: InputStream,
    outputFile: File,
    chunkSize: Int = DEFAULT_CHUNK_SIZE,
  ): EncryptionSummary {
    outputFile.parentFile?.mkdirs()
    val key = keyProvider.getOrCreateMasterKey()
    val sha = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(chunkSize)
    var totalRead = 0L

    DataOutputStream(FileOutputStream(outputFile).buffered()).use { dos ->
      dos.write(MAGIC)
      dos.writeInt(chunkSize)

      while (true) {
        val read = readFullChunk(inputStream, buffer)
        if (read <= 0) break
        totalRead += read
        sha.update(buffer, 0, read)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        require(iv.size == 12) { "Expected 12-byte GCM IV" }
        val cipherBytes = cipher.doFinal(buffer, 0, read)

        dos.writeByte(iv.size)
        dos.write(iv)
        dos.writeInt(cipherBytes.size)
        dos.write(cipherBytes)
      }
      // End sentinel: 0 IV length
      dos.writeByte(0)
      dos.flush()
    }

    val hex = sha.digest().joinToString("") { "%02x".format(it) }
    return EncryptionSummary(
      originalSizeBytes = totalRead,
      sha256Hex = hex,
      chunkSizeBytes = chunkSize,
    )
  }

  fun decryptFileToStream(
    sourceFile: File,
    outputStream: OutputStream,
    expectedSha256Hex: String? = null,
  ): String {
    val key = keyProvider.getOrCreateMasterKey()
    val sha = MessageDigest.getInstance("SHA-256")

    DataInputStream(FileInputStream(sourceFile).buffered()).use { dis ->
      val magic = ByteArray(MAGIC.size)
      dis.readFully(magic)
      require(magic.contentEquals(MAGIC)) { "Unsupported vault container header" }
      dis.readInt() // chunkSize

      while (true) {
        val ivLen = dis.readUnsignedByte()
        if (ivLen == 0) break
        require(ivLen == 12) { "Invalid GCM nonce length: $ivLen" }
        val iv = ByteArray(ivLen)
        dis.readFully(iv)
        val cipherLen = dis.readInt()
        require(cipherLen in (GCM_TAG_BYTES + 1)..(8 * 1024 * 1024)) { "Invalid frame length" }
        val cipherBytes = ByteArray(cipherLen)
        dis.readFully(cipherBytes)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = cipher.doFinal(cipherBytes)
        sha.update(plain)
        outputStream.write(plain)
      }
      outputStream.flush()
    }

    val actualHex = sha.digest().joinToString("") { "%02x".format(it) }
    if (!expectedSha256Hex.isNullOrBlank()) {
      require(actualHex.equals(expectedSha256Hex, ignoreCase = true)) {
        "Integrity check failed: decrypted digest mismatch"
      }
    }
    return actualHex
  }

  fun decryptFileToBytes(
    sourceFile: File,
    expectedSha256Hex: String? = null,
    maxBytes: Int = 32 * 1024 * 1024,
  ): ByteArray {
    val baos = ByteArrayOutputStream()
    val actual = decryptFileToStream(sourceFile, baos, expectedSha256Hex)
    require(baos.size() <= maxBytes) { "File exceeds in-memory preview limit ($maxBytes bytes)" }
    if (!expectedSha256Hex.isNullOrBlank()) {
      require(actual.equals(expectedSha256Hex, ignoreCase = true)) { "Digest mismatch" }
    }
    return baos.toByteArray()
  }

  fun indexFramedFile(sourceFile: File): FramedHeaderInfo {
    val frames = mutableListOf<FrameIndexEntry>()
    var plainOffset = 0L
    var chunkSize = DEFAULT_CHUNK_SIZE
    RandomAccessFile(sourceFile, "r").use { raf ->
      val magic = ByteArray(MAGIC.size)
      raf.readFully(magic)
      require(magic.contentEquals(MAGIC)) { "Unsupported vault container header" }
      chunkSize = raf.readInt()
      while (true) {
        val frameStart = raf.filePointer
        val ivLen = try {
          raf.readUnsignedByte()
        } catch (_: EOFException) {
          break
        }
        if (ivLen == 0) break
        raf.skipBytes(ivLen)
        val cipherLen = raf.readInt()
        val plainLen = (cipherLen - GCM_TAG_BYTES).coerceAtLeast(0)
        frames += FrameIndexEntry(
          fileOffset = frameStart,
          plainStartOffset = plainOffset,
          plainLength = plainLen,
        )
        plainOffset += plainLen
        raf.skipBytes(cipherLen)
      }
    }
    return FramedHeaderInfo(
      totalPlainSize = plainOffset,
      chunkSize = chunkSize,
      frames = frames,
    )
  }

  fun decryptSingleFrame(sourceFile: File, entry: FrameIndexEntry): ByteArray {
    val key = keyProvider.getOrCreateMasterKey()
    RandomAccessFile(sourceFile, "r").use { raf ->
      raf.seek(entry.fileOffset)
      val ivLen = raf.readUnsignedByte()
      val iv = ByteArray(ivLen)
      raf.readFully(iv)
      val cipherLen = raf.readInt()
      val cipherBytes = ByteArray(cipherLen)
      raf.readFully(cipherBytes)

      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
      return cipher.doFinal(cipherBytes)
    }
  }

  fun encryptSmallBlob(plainBytes: ByteArray): ByteArray {
    val key = keyProvider.getOrCreateMasterKey()
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key)
    val iv = cipher.iv
    val cipherBytes = cipher.doFinal(plainBytes)
    val out = ByteArrayOutputStream()
    DataOutputStream(out).use { dos ->
      dos.writeByte(iv.size)
      dos.write(iv)
      dos.writeInt(cipherBytes.size)
      dos.write(cipherBytes)
    }
    return out.toByteArray()
  }

  fun decryptSmallBlob(blobBytes: ByteArray): ByteArray {
    val key = keyProvider.getOrCreateMasterKey()
    DataInputStream(ByteArrayInputStream(blobBytes)).use { dis ->
      val ivLen = dis.readUnsignedByte()
      val iv = ByteArray(ivLen)
      dis.readFully(iv)
      val cipherLen = dis.readInt()
      val cipherBytes = ByteArray(cipherLen)
      dis.readFully(cipherBytes)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
      return cipher.doFinal(cipherBytes)
    }
  }

  /**
   * Exports encrypted backup using a PBKDF2-derived AES-256-GCM wrapping key from a user
   * backup passphrase (completely independent of Keystore hardware binding so backups can
   * restore on another device).
   */
  fun exportEncryptedBackup(
    passphrase: String,
    manifestJson: String,
    items: List<Pair<String, File>>, // (archiveEntryName, encryptedVaultFile)
    outStream: OutputStream,
  ) {
    require(passphrase.length >= 6) { "Backup passphrase must be at least 6 characters" }
    val zipBytes = ByteArrayOutputStream().use { baos ->
      ZipOutputStream(baos).use { zos ->
        zos.putNextEntry(ZipEntry("manifest.json"))
        zos.write(manifestJson.toByteArray(Charsets.UTF_8))
        zos.closeEntry()

        for ((entryName, encFile) in items) {
          if (!encFile.exists()) continue
          zos.putNextEntry(ZipEntry("items/$entryName"))
          decryptFileToStream(encFile, zos)
          zos.closeEntry()
        }
      }
      baos.toByteArray()
    }

    val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
    val backupKey = derivePassphraseKey(passphrase, salt)
    val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, backupKey, GCMParameterSpec(GCM_TAG_BITS, iv))
    val cipherBytes = cipher.doFinal(zipBytes)

    DataOutputStream(outStream).use { dos ->
      dos.write(BACKUP_MAGIC)
      dos.writeByte(salt.size)
      dos.write(salt)
      dos.writeByte(iv.size)
      dos.write(iv)
      dos.writeInt(cipherBytes.size)
      dos.write(cipherBytes)
      dos.flush()
    }
  }

  data class RestoredBackupPayload(
    val manifestJson: String,
    val decryptedEntries: Map<String, ByteArray>,
  )

  fun decryptBackupArchive(
    passphrase: String,
    inputStream: InputStream,
  ): RestoredBackupPayload {
    val zipBytes = DataInputStream(inputStream).use { dis ->
      val magic = ByteArray(BACKUP_MAGIC.size)
      dis.readFully(magic)
      require(magic.contentEquals(BACKUP_MAGIC)) { "Invalid NEXUS backup archive format" }
      val saltLen = dis.readUnsignedByte()
      val salt = ByteArray(saltLen).also { dis.readFully(it) }
      val ivLen = dis.readUnsignedByte()
      val iv = ByteArray(ivLen).also { dis.readFully(it) }
      val cipherLen = dis.readInt()
      require(cipherLen > GCM_TAG_BYTES) { "Corrupted backup archive" }
      val cipherBytes = ByteArray(cipherLen).also { dis.readFully(it) }

      val key = derivePassphraseKey(passphrase, salt)
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
      cipher.doFinal(cipherBytes)
    }

    var manifestJson = "{}"
    val files = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
      while (true) {
        val entry = zis.nextEntry ?: break
        val content = zis.readBytes()
        if (entry.name == "manifest.json") {
          manifestJson = content.toString(Charsets.UTF_8)
        } else if (entry.name.startsWith("items/")) {
          files[entry.name.removePrefix("items/")] = content
        }
        zis.closeEntry()
      }
    }
    return RestoredBackupPayload(manifestJson, files)
  }

  private fun derivePassphraseKey(passphrase: String, salt: ByteArray): SecretKeySpec {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
    val derived = factory.generateSecret(spec).encoded
    return SecretKeySpec(derived, "AES")
  }

  private fun readFullChunk(input: InputStream, buffer: ByteArray): Int {
    var offset = 0
    while (offset < buffer.size) {
      val r = input.read(buffer, offset, buffer.size - offset)
      if (r == -1) break
      offset += r
    }
    return offset
  }
}
