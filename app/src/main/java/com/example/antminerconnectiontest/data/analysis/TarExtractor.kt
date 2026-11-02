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
     *
     * Handles GNU long-name ('L') entries, pax extended headers ('x', the
     * "path" record), pax global headers ('g') and the ustar prefix field —
     * without long-name support, entries whose paths exceed 100 chars were
     * extracted under truncated names and the log analysis silently found
     * nothing.
     */
    fun extractStream(input: InputStream, targetDir: File) {
        val header = ByteArray(BLOCK_SIZE)
        val canonicalTargetPath = targetDir.canonicalPath
        var pendingLongName: String? = null

        while (true) {
            val bytesRead = readFully(input, header, BLOCK_SIZE)
            if (bytesRead < BLOCK_SIZE) break

            // Check for end of archive (all zeros in header)
            if (isAllZero(header)) {
                break
            }

            // Extract file size in octal (bytes 124..135)
            val size = parseOctal(header, 124, 12)

            // Extract type flag (byte 156): '0' or 0 for normal file, '5' for directory,
            // 'L' GNU long name, 'x' pax extended header, 'g' pax global header
            val typeFlag = header[156]

            when (typeFlag) {
                'L'.code.toByte() -> {
                    // This entry's data is the real name of the NEXT entry.
                    pendingLongName = String(readEntryData(input, size), Charsets.UTF_8)
                        .trimEnd('\u0000')
                        .trim()
                    continue
                }
                'x'.code.toByte() -> {
                    parsePaxPath(readEntryData(input, size))?.let { pendingLongName = it }
                    continue
                }
                'g'.code.toByte() -> {
                    skipEntryData(input, size)
                    continue
                }
            }

            // Entry name: pending long name wins, then ustar prefix + name,
            // then the plain 100-byte name field.
            val nameField = parseString(header, 0, 100)
            val name = pendingLongName ?: run {
                val prefix = parseString(header, 345, 155)
                if (prefix.isNotBlank()) "$prefix/$nameField" else nameField
            }
            pendingLongName = null

            if (name.isBlank()) {
                skipEntryData(input, size)
                continue
            }

            val entryFile = File(targetDir, name)

            // Prevent Zip Slip / Path Traversal vulnerability. The target
            // itself or a path strictly below it only — a bare prefix match
            // would also accept siblings like "extracted_evil/...".
            val entryCanonical = entryFile.canonicalPath
            if (entryCanonical != canonicalTargetPath &&
                !entryCanonical.startsWith(canonicalTargetPath + File.separator)
            ) {
                throw SecurityException("Invalid archive path traversing outside target directory: $name")
            }

            if (typeFlag == '5'.code.toByte() || name.endsWith("/")) {
                entryFile.mkdirs()
                skipEntryData(input, size)
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

                skipPadding(input, size)
            }
        }
    }

    /** Reads one entry's data blocks (plus 512-byte padding) and returns the bytes. */
    private fun readEntryData(input: InputStream, size: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var remaining = size
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            out.write(buffer, 0, read)
            remaining -= read
        }
        skipPadding(input, size)
        return out.toByteArray()
    }

    /** Discards one entry's data blocks (plus padding) without storing them. */
    private fun skipEntryData(input: InputStream, size: Long) {
        skipFully(input, size)
        skipPadding(input, size)
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val padding = (BLOCK_SIZE - (size % BLOCK_SIZE)) % BLOCK_SIZE
        if (padding > 0) {
            skipFully(input, padding)
        }
    }

    /** Extracts the "path" record from pax extended-header data, if present. */
    private fun parsePaxPath(data: ByteArray): String? {
        val text = String(data, Charsets.UTF_8)
        var index = 0
        while (index < text.length) {
            val space = text.indexOf(' ', index)
            if (space == -1) return null
            val length = text.substring(index, space).toIntOrNull() ?: return null
            if (length <= 0 || index + length > text.length) return null
            val record = text.substring(space + 1, index + length).trim()
            if (record.startsWith("path=")) {
                return record.removePrefix("path=")
            }
            index += length
        }
        return null
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
