package org.videolan.vlc.gui.music.tags

import android.os.ParcelFileDescriptor
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import java.io.File
import java.io.IOException

sealed class TagResult {
    object Success : TagResult()
    class Failure(val reason: String) : TagResult()
}

/**
 * Writes tags without risking the song: the edit is made on a copy next to the original, the copy
 * is read back and compared, and only then does it replace the original.
 */
object TagWriter {
    private const val TMP_SUFFIX = ".vlctag.tmp"

    fun write(original: File, edit: TagEdit): TagResult {
        edit.validate()?.let { return TagResult.Failure(it) }
        if (!original.isFile) return TagResult.Failure("File not found")
        val parent = original.parentFile ?: return TagResult.Failure("No folder")
        if (!parent.canWrite()) return TagResult.Failure("Folder is read-only")
        val tmp = File(parent, ".${original.name}$TMP_SUFFIX")
        try {
            original.copyTo(tmp, overwrite = true)
            val originalLength = open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { audioLength(it) }
                ?: return TagResult.Failure("Unsupported or unreadable audio file")

            val current = open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { TagLib.getMetadata(it.detachFd(), false) }
                ?: return TagResult.Failure("Could not read existing tags")
            val properties = HashMap(current.propertyMap)
            for ((key, values) in edit.toProperties()) {
                if (values.isEmpty()) properties.remove(key) else properties[key] = values.toTypedArray()
            }
            val saved = open(tmp, ParcelFileDescriptor.MODE_READ_WRITE).use { TagLib.savePropertyMap(it.detachFd(), properties) }
            if (!saved) return TagResult.Failure("Could not write tags to this file type")

            when (val cover = edit.cover) {
                CoverEdit.Keep -> Unit
                CoverEdit.Remove -> if (!open(tmp, ParcelFileDescriptor.MODE_READ_WRITE).use { TagLib.savePictures(it.detachFd(), emptyArray()) })
                    return TagResult.Failure("Could not remove the cover")
                is CoverEdit.Replace -> {
                    val picture = Picture(cover.data, "", "Front Cover", cover.mimeType)
                    if (!open(tmp, ParcelFileDescriptor.MODE_READ_WRITE).use { TagLib.savePictures(it.detachFd(), arrayOf(picture)) })
                        return TagResult.Failure("Could not write the cover")
                }
            }

            verify(tmp, edit, originalLength)?.let { return TagResult.Failure(it) }
            if (!tmp.renameTo(original)) return TagResult.Failure("Could not replace the original file")
            return TagResult.Success
        } catch (e: IOException) {
            return TagResult.Failure(e.message ?: "I/O error (is there room for a copy of the file?)")
        } catch (e: SecurityException) {
            return TagResult.Failure("No permission to modify this file")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun verify(tmp: File, edit: TagEdit, originalLength: Int): String? {
        val metadata = open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use {
            TagLib.getMetadata(it.detachFd(), edit.cover is CoverEdit.Replace)
        } ?: return "Verification failed: edited file is unreadable"
        for ((key, expected) in edit.toProperties()) {
            val actual = metadata.propertyMap[key]?.toList().orEmpty()
            // Some formats normalise values (e.g. a full date); compare the year only for DATE.
            val same = if (key == "DATE") actual.firstOrNull()?.let { yearFromDate(it) } == expected.firstOrNull()?.let { yearFromDate(it) }
            else actual == expected
            if (!same) return "Verification failed for $key"
        }
        val cover = edit.cover
        if (cover is CoverEdit.Replace && metadata.pictures.none { it.data.contentEquals(cover.data) }) return "Verification failed for the cover"
        val newLength = open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { audioLength(it) }
        if (newLength == null || kotlin.math.abs(newLength - originalLength) > 50) return "Verification failed: audio length changed"
        return null
    }

    private fun audioLength(fd: ParcelFileDescriptor): Int? = TagLib.getAudioProperties(fd.detachFd())?.length

    private fun open(file: File, mode: Int) = ParcelFileDescriptor.open(file, mode)
}
