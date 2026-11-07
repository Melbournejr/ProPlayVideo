package com.videoplayer.app.data.model

data class VideoItem(
    val id: String,
    val title: String,
    val displayName: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val dateAdded: Long,
    val folder: String,
    val mimeType: String = "video/*",
    val thumbnail: String? = null
) {
    val durationFormatted: String get() = formatDuration(duration)
    val sizeFormatted: String get() = formatSize(size)

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024
        if (kb < 1024) return "$kb KB"
        val mb = kb / 1024
        if (mb < 1024) return "$mb MB"
        return "${mb / 1024} GB"
    }
}
