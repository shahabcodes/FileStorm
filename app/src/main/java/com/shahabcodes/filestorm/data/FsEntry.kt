package com.shahabcodes.filestorm.data

import java.io.File

enum class FileKind { FOLDER, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, OTHER }

data class FsEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val kind: FileKind,
    val childCount: Int = -1,
) {
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    fun toFile(): File = File(path)

    companion object {
        private val imageExt = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "raw", "dng")
        private val videoExt = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "flv", "wmv")
        private val audioExt = setOf("mp3", "wav", "m4a", "ogg", "flac", "aac", "opus", "wma", "mid", "amr")
        private val docExt = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "rtf", "csv", "epub", "html", "htm", "json", "xml")
        private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso")

        fun kindOf(name: String, isDir: Boolean): FileKind {
            if (isDir) return FileKind.FOLDER
            return when (name.substringAfterLast('.', "").lowercase()) {
                in imageExt -> FileKind.IMAGE
                in videoExt -> FileKind.VIDEO
                in audioExt -> FileKind.AUDIO
                in docExt -> FileKind.DOCUMENT
                in archiveExt -> FileKind.ARCHIVE
                "apk" -> FileKind.APK
                else -> FileKind.OTHER
            }
        }

        fun from(file: File): FsEntry {
            val isDir = file.isDirectory
            return FsEntry(
                path = file.absolutePath,
                name = file.name,
                isDirectory = isDir,
                size = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                kind = kindOf(file.name, isDir),
                childCount = if (isDir) file.list()?.size ?: 0 else -1,
            )
        }
    }
}
