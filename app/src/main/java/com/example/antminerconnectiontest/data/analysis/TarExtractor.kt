package com.example.antminerconnectiontest.data.analysis

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Lightweight, zero-dependency streaming extractor for POSIX ustar archives.
 */
object TarExtractor {

    private const val BLOCK_SIZE = 512

    /**
     * Extracts an uncompressed TAR file into the specified target directory.
     *
     * @param tarFile The source .tar archive.
     * @param targetDir The directory where files will be unpacked.
     * @return The target directory containing the extracted contents.
     */
    fun extractTar(tarFile: File, targetDir: File): File {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        FileInputStream(tarFile).use { fis ->
            extractStream(fis, targetDir)
        }
        return targetDir
    }

    /**
     * Extracts a TAR stream into a target directory.
     */
    fun extractStream(input: InputStream, targetDir: File) {
        val header = ByteArray(BLOCK_SIZE)
        val canonicalTargetPath = targetDir.canonicalPath

        while (true) {
            val bytesRead = readFully(input, header, BLOCK_SIZE)
            if (bytesRead < BLOCK_SIZE) break

            // Check for end of archive (all zeros in header)
            if (isAllZero(header)) {
                break
            }

            // Extract entry name (bytes 0..99)
            val name = parseString(header, 0, 100)
            if (name.isBlank()) continue

            // Extract file size in octal (bytes 124..135)
            val size = parseOctal(header, 124, 12)

            // Extract type flag (byte 156): '0' or 0 for normal file, '5' for directory
            val typeFlag = header[156]

            val entryFile = File(targetDir, name)

            // Prevent Zip Slip / Path Traversal vulnerability
            if (!entryFile.canonicalPath.startsWith(canonicalTargetPath)) {
                throw SecurityException("Invalid archive path traversing outside target directory: $name")
            }

            if (typeFlag == '5'.code.toByte() || name.endsWith("/")) {
                entryFile.mkdirs()
            } else {
                entryFile.parentFile?.mkdirs()
                FileOutputStream(entryFile).use { fos ->
                    var remaining = size
                    val buffer = ByteArray(8192)
                    while (remaining > 0) {
                        val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        fos.write(buffer, 0, read)
                        remaining -= read
                    }
                }

                // Skip padding to 512-byte boundary
                val padding = (BLOCK_SIZE - (size % BLOCK_SIZE)) % BLOCK_SIZE
                if (padding > 0) {
                    skipFully(input, padding)
                }
            }
        }
    }

    private fun parseString(buffer: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val max = offset + length
        while (end < max && buffer[end] != 0.toByte()) {
            end++
        }
        return String(buffer, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun parseOctal(buffer: ByteArray, offset: Int, length: Int): Long {
        val str = parseString(buffer, offset, length)
        if (str.isBlank()) return 0L
        return try {
            str.toLong(8)
        } catch (_: Exception) {
            0L
        }
    }

    private fun isAllZero(buffer: ByteArray): Boolean {
        for (b in buffer) {
            if (b != 0.toByte()) return false
        }
        return true
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Int {
        var total = 0
        while (total < length) {
            val read = input.read(buffer, total, length - total)
            if (read == -1) break
            total += read
        }
        return total
    }

    private fun skipFully(input: InputStream, length: Long) {
        var remaining = length
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                // Read and discard if skip returns 0
                if (input.read() == -1) break
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }
}
