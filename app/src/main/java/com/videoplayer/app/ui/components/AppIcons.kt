package com.videoplayer.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    private fun createIcon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector {
        return ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply(block).build()
    }

    val Folder: ImageVector = createIcon("Folder") {
        path(fill = SolidColor(Color.White)) {
            moveTo(10f, 4f); lineTo(4f, 4f); curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
            lineTo(2f, 18f); curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); lineTo(20f, 20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f); lineTo(22f, 8f)
            curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f); lineTo(12f, 6f); lineTo(10f, 4f); close()
        }
    }

    val VideoLibrary: ImageVector = createIcon("VideoLibrary") {
        path(fill = SolidColor(Color.White)) {
            moveTo(4f, 6f); lineTo(2f, 6f); lineTo(2f, 20f); lineTo(18f, 20f); lineTo(18f, 18f); lineTo(4f, 18f); close()
            moveTo(20f, 2f); lineTo(8f, 2f); curveTo(6.9f, 2f, 6f, 2.9f, 6f, 4f); lineTo(6f, 16f)
            curveTo(6f, 17.1f, 6.9f, 18f, 8f, 18f); lineTo(20f, 18f); curveTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
            lineTo(22f, 4f); curveTo(22f, 2.9f, 21.1f, 2f, 20f, 2f); close()
            moveTo(12f, 14.5f); lineTo(12f, 5.5f); lineTo(18f, 10f); close()
        }
    }

    val SkipPrevious: ImageVector = createIcon("SkipPrevious") {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 6f); lineTo(8f, 6f); lineTo(8f, 18f); lineTo(6f, 18f); close()
            moveTo(9.5f, 12f); lineTo(18f, 18f); lineTo(18f, 6f); close()
        }
    }

    val SkipNext: ImageVector = createIcon("SkipNext") {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 18f); lineTo(14.5f, 12f); lineTo(6f, 6f); close()
            moveTo(16f, 6f); lineTo(18f, 6f); lineTo(18f, 18f); lineTo(16f, 18f); close()
        }
    }

    val Pause: ImageVector = createIcon("Pause") {
        path(fill = SolidColor(Color.White)) {
            moveTo(6f, 19f); lineTo(10f, 19f); lineTo(10f, 5f); lineTo(6f, 5f); close()
            moveTo(14f, 5f); lineTo(14f, 19f); lineTo(18f, 19f); lineTo(18f, 5f); close()
        }
    }

    val PlayCircleFilled: ImageVector = createIcon("PlayCircleFilled") {
        path(fill = SolidColor(Color.White)) {
            moveTo(10f, 16.5f); lineTo(16f, 12f); lineTo(10f, 7.5f); close()
        }
    }

    val ScreenRotation: ImageVector = createIcon("ScreenRotation") {
        path(fill = SolidColor(Color.White)) {
            moveTo(16.48f, 2.52f); curveTo(19.78f, 4.07f, 22.09f, 7.24f, 22.45f, 11f); lineTo(23.95f, 11f)
            curveTo(23.44f, 4.84f, 18.29f, 0f, 12f, 0f); lineTo(11.34f, 0.03f); lineTo(15.15f, 3.84f); lineTo(16.48f, 2.52f); close()
            moveTo(10.23f, 1.75f); lineTo(1.75f, 10.23f); curveTo(0.97f, 11.01f, 0.97f, 12.28f, 1.75f, 13.06f)
            lineTo(10.94f, 22.25f); curveTo(11.72f, 23.03f, 12.99f, 23.03f, 13.77f, 22.25f); lineTo(22.25f, 13.77f)
            curveTo(23.03f, 12.99f, 23.03f, 11.72f, 22.25f, 10.94f); lineTo(13.06f, 1.75f)
            curveTo(12.28f, 0.97f, 11.01f, 0.97f, 10.23f, 1.75f); close()
        }
    }

    val AutoAwesome: ImageVector = createIcon("AutoAwesome") {
        path(fill = SolidColor(Color.White)) {
            moveTo(19f, 9f); lineTo(20.25f, 6.25f); lineTo(23f, 5f); lineTo(20.25f, 3.75f); lineTo(19f, 1f)
            lineTo(17.75f, 3.75f); lineTo(15f, 5f); lineTo(17.75f, 6.25f); close()
            moveTo(11.5f, 9.5f); lineTo(9f, 4f); lineTo(6.5f, 9.5f); lineTo(1f, 12f); lineTo(6.5f, 14.5f)
            lineTo(9f, 20f); lineTo(11.5f, 14.5f); lineTo(17f, 12f); close()
        }
    }

    val Tune: ImageVector = createIcon("Tune") {
        path(fill = SolidColor(Color.White)) {
            moveTo(3f, 17f); lineTo(3f, 19f); lineTo(9f, 19f); lineTo(9f, 17f); lineTo(3f, 17f); close()
            moveTo(3f, 5f); lineTo(3f, 7f); lineTo(13f, 7f); lineTo(13f, 5f); lineTo(3f, 5f); close()
            moveTo(13f, 21f); lineTo(13f, 19f); lineTo(21f, 19f); lineTo(21f, 17f); lineTo(13f, 17f); lineTo(13f, 15f); lineTo(11f, 15f); lineTo(11f, 21f); close()
            moveTo(7f, 9f); lineTo(7f, 11f); lineTo(3f, 11f); lineTo(3f, 13f); lineTo(7f, 13f); lineTo(7f, 15f); lineTo(9f, 15f); lineTo(9f, 9f); close()
            moveTo(21f, 13f); lineTo(21f, 11f); lineTo(11f, 11f); lineTo(11f, 13f); close()
            moveTo(15f, 9f); lineTo(17f, 9f); lineTo(17f, 7f); lineTo(21f, 7f); lineTo(21f, 5f); lineTo(17f, 5f); lineTo(17f, 3f); lineTo(15f, 3f); close()
        }
    }

    val LockOpen: ImageVector = createIcon("LockOpen") {
        path(fill = SolidColor(Color.White)) {
            moveTo(18f, 8f); lineTo(17f, 8f); lineTo(17f, 6f); curveTo(17f, 3.24f, 14.76f, 1f, 12f, 1f)
            curveTo(9.24f, 1f, 7f, 3.24f, 7f, 6f); lineTo(9f, 6f); curveTo(9f, 4.34f, 10.34f, 3f, 12f, 3f)
            curveTo(13.66f, 3f, 15f, 4.34f, 15f, 6f); lineTo(15f, 8f); lineTo(6f, 8f)
            curveTo(4.9f, 8f, 4f, 8.9f, 4f, 10f); lineTo(4f, 20f); curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f)
            lineTo(18f, 22f); curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f); lineTo(20f, 10f)
            curveTo(20f, 8.9f, 19.1f, 8f, 18f, 8f); close()
        }
    }

    val Key: ImageVector = createIcon("Key") {
        path(fill = SolidColor(Color.White)) {
            moveTo(12.65f, 10f); curveTo(11.83f, 7.67f, 9.61f, 6f, 7f, 6f)
            curveTo(3.69f, 6f, 1f, 8.69f, 1f, 12f); curveTo(1f, 15.31f, 3.69f, 18f, 7f, 18f)
            curveTo(9.61f, 18f, 11.83f, 16.33f, 12.65f, 14f); lineTo(17f, 14f); lineTo(17f, 18f)
            lineTo(21f, 18f); lineTo(21f, 14f); lineTo(23f, 14f); lineTo(23f, 10f); lineTo(12.65f, 10f); close()
            moveTo(7f, 14f); curveTo(5.9f, 14f, 5f, 13.1f, 5f, 12f); curveTo(5f, 10.9f, 5.9f, 10f, 7f, 10f)
            curveTo(8.1f, 10f, 9f, 10.9f, 9f, 12f); curveTo(9f, 13.1f, 8.1f, 14f, 7f, 14f); close()
        }
    }

    val Shield: ImageVector = createIcon("Shield") {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 1f); lineTo(3f, 5f); lineTo(3f, 11f)
            curveTo(3f, 16.55f, 6.84f, 21.74f, 12f, 23f)
            curveTo(17.16f, 21.74f, 21f, 16.55f, 21f, 11f); lineTo(21f, 5f); lineTo(12f, 1f); close()
        }
    }

    val CloudDownload: ImageVector = createIcon("CloudDownload") {
        path(fill = SolidColor(Color.White)) {
            moveTo(19.35f, 10.04f); curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
            curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f); curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
            curveTo(0f, 17.31f, 2.69f, 20f, 6f, 20f); lineTo(19f, 20f)
            curveTo(21.76f, 20f, 24f, 17.76f, 24f, 15f); curveTo(24f, 12.36f, 21.95f, 10.22f, 19.35f, 10.04f); close()
            moveTo(17f, 13f); lineTo(12f, 18f); lineTo(7f, 13f); lineTo(10f, 13f); lineTo(10f, 9f); lineTo(14f, 9f); lineTo(14f, 13f); lineTo(17f, 13f); close()
        }
    }

    val PhotoCamera: ImageVector = createIcon("PhotoCamera") {
        path(fill = SolidColor(Color.White)) {
            moveTo(9f, 2f); lineTo(7.17f, 4f); lineTo(4f, 4f); curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
            lineTo(2f, 18f); curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); lineTo(20f, 20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f); lineTo(22f, 6f); curveTo(22f, 4.9f, 21.1f, 4f, 20f, 4f)
            lineTo(16.83f, 4f); lineTo(15f, 2f); lineTo(9f, 2f); close()
            moveTo(12f, 17f); curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f); curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f)
            curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f); curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f); close()
        }
    }

    val PictureInPictureAlt: ImageVector = createIcon("PictureInPictureAlt") {
        path(fill = SolidColor(Color.White)) {
            moveTo(19f, 11f); lineTo(11f, 11f); lineTo(11f, 17f); lineTo(19f, 17f); lineTo(19f, 11f); close()
            moveTo(21f, 3f); lineTo(3f, 3f); curveTo(1.9f, 3f, 1f, 3.9f, 1f, 5f); lineTo(1f, 19f)
            curveTo(1f, 20.1f, 1.9f, 21f, 3f, 21f); lineTo(21f, 21f); curveTo(22.1f, 21f, 23f, 20.1f, 23f, 19f)
            lineTo(23f, 5f); curveTo(23f, 3.9f, 22.1f, 3f, 21f, 3f); close()
            moveTo(21f, 19.02f); lineTo(3f, 19.02f); lineTo(3f, 4.98f); lineTo(21f, 4.98f); lineTo(21f, 19.02f); close()
        }
    }

    val Headphones: ImageVector = createIcon("Headphones") {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 3f); curveTo(7.03f, 3f, 3f, 7.03f, 3f, 12f); lineTo(3f, 19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f); lineTo(9f, 21f); lineTo(9f, 13f); lineTo(5f, 13f); lineTo(5f, 12f)
            curveTo(5f, 8.13f, 8.13f, 5f, 12f, 5f); curveTo(15.87f, 5f, 19f, 8.13f, 19f, 12f); lineTo(19f, 13f)
            lineTo(15f, 13f); lineTo(15f, 21f); lineTo(19f, 21f); curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            lineTo(21f, 12f); curveTo(21f, 7.03f, 16.97f, 3f, 12f, 3f); close()
        }
    }

    val Sort: ImageVector = createIcon("Sort") {
        path(fill = SolidColor(Color.White)) {
            moveTo(3f, 18f); lineTo(9f, 18f); lineTo(9f, 16f); lineTo(3f, 16f); close()
            moveTo(3f, 6f); lineTo(3f, 8f); lineTo(21f, 8f); lineTo(21f, 6f); close()
            moveTo(3f, 13f); lineTo(15f, 13f); lineTo(15f, 11f); lineTo(3f, 11f); close()
        }
    }

    val GridView: ImageVector = createIcon("GridView") {
        path(fill = SolidColor(Color.White)) {
            moveTo(3f, 3f); lineTo(11f, 3f); lineTo(11f, 11f); lineTo(3f, 11f); close()
            moveTo(3f, 13f); lineTo(11f, 13f); lineTo(11f, 21f); lineTo(3f, 21f); close()
            moveTo(13f, 3f); lineTo(21f, 3f); lineTo(21f, 11f); lineTo(13f, 11f); close()
            moveTo(13f, 13f); lineTo(21f, 13f); lineTo(21f, 21f); lineTo(13f, 21f); close()
        }
    }

    val ViewList: ImageVector = createIcon("ViewList") {
        path(fill = SolidColor(Color.White)) {
            moveTo(4f, 14f); lineTo(8f, 14f); lineTo(8f, 10f); lineTo(4f, 10f); close()
            moveTo(4f, 19f); lineTo(8f, 19f); lineTo(8f, 15f); lineTo(4f, 15f); close()
            moveTo(4f, 9f); lineTo(8f, 9f); lineTo(8f, 5f); lineTo(4f, 5f); close()
            moveTo(9f, 14f); lineTo(21f, 14f); lineTo(21f, 10f); lineTo(9f, 10f); close()
            moveTo(9f, 19f); lineTo(21f, 19f); lineTo(21f, 15f); lineTo(9f, 15f); close()
            moveTo(9f, 5f); lineTo(9f, 9f); lineTo(21f, 9f); lineTo(21f, 5f); close()
        }
    }

    val Checklist: ImageVector = createIcon("Checklist") {
        path(fill = SolidColor(Color.White)) {
            moveTo(22f, 7f); lineTo(13f, 7f); lineTo(13f, 9f); lineTo(22f, 9f); close()
            moveTo(22f, 15f); lineTo(13f, 15f); lineTo(13f, 17f); lineTo(22f, 17f); close()
            moveTo(5.54f, 11f); lineTo(2f, 7.46f); lineTo(3.41f, 6.05f); lineTo(5.53f, 8.17f); lineTo(9.77f, 3.93f); lineTo(11.18f, 5.34f); close()
            moveTo(5.54f, 19f); lineTo(2f, 15.46f); lineTo(3.41f, 14.05f); lineTo(5.53f, 16.17f); lineTo(9.77f, 11.93f); lineTo(11.18f, 13.34f); close()
        }
    }

    val Stream: ImageVector = createIcon("Stream") {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 2f); curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f); curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
            curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f); curveTo(22f, 6.48f, 17.52f, 2f, 12f, 2f); close()
            moveTo(9.5f, 16.5f); lineTo(9.5f, 7.5f); lineTo(16.5f, 12f); close()
        }
    }

    val ContentCopy: ImageVector = createIcon("ContentCopy") {
        path(fill = SolidColor(Color.White)) {
            moveTo(16f, 1f); lineTo(4f, 1f); curveTo(2.9f, 1f, 2f, 1.9f, 2f, 3f); lineTo(2f, 17f); lineTo(4f, 17f); lineTo(4f, 3f); lineTo(16f, 3f); close()
            moveTo(19f, 5f); lineTo(8f, 5f); curveTo(6.9f, 5f, 6f, 5.9f, 6f, 7f); lineTo(6f, 21f)
            curveTo(6f, 22.1f, 6.9f, 23f, 8f, 23f); lineTo(19f, 23f); curveTo(20.1f, 23f, 21f, 22.1f, 21f, 21f)
            lineTo(21f, 7f); curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f); close()
            moveTo(19f, 21f); lineTo(8f, 21f); lineTo(8f, 7f); lineTo(19f, 7f); close()
        }
    }

    val DriveFileMove: ImageVector = createIcon("DriveFileMove") {
        path(fill = SolidColor(Color.White)) {
            moveTo(20f, 6f); lineTo(12f, 6f); lineTo(10f, 4f); lineTo(4f, 4f); curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f)
            lineTo(2f, 18f); curveTo(2f, 19.1f, 2.9f, 20f, 4f, 20f); lineTo(20f, 20f)
            curveTo(21.1f, 20f, 22f, 19.1f, 22f, 18f); lineTo(22f, 8f); curveTo(22f, 6.9f, 21.1f, 6f, 20f, 6f); close()
            moveTo(12f, 17f); lineTo(12f, 14f); lineTo(8f, 14f); lineTo(8f, 12f); lineTo(12f, 12f); lineTo(12f, 9f); lineTo(16f, 13f); close()
        }
    }

    val Palette: ImageVector = createIcon("Palette") {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 2f); curveTo(6.49f, 2f, 2f, 6.49f, 2f, 12f); curveTo(2f, 17.51f, 6.49f, 22f, 12f, 22f)
            curveTo(13.38f, 22f, 14.5f, 20.88f, 14.5f, 19.5f); curveTo(14.5f, 18.89f, 14.28f, 18.33f, 13.91f, 17.9f)
            curveTo(13.55f, 17.5f, 13.33f, 16.98f, 13.33f, 16.4f); curveTo(13.33f, 15.02f, 14.45f, 13.9f, 15.83f, 13.9f)
            lineTo(17.5f, 13.9f); curveTo(19.98f, 13.9f, 22f, 11.88f, 22f, 9.4f); curveTo(22f, 5.31f, 17.51f, 2f, 12f, 2f); close()
            moveTo(6.5f, 11.5f); curveTo(5.67f, 11.5f, 5f, 10.83f, 5f, 10f); curveTo(5f, 9.17f, 5.67f, 8.5f, 6.5f, 8.5f)
            curveTo(7.33f, 8.5f, 8f, 9.17f, 8f, 10f); curveTo(8f, 10.83f, 7.33f, 11.5f, 6.5f, 11.5f); close()
            moveTo(9.5f, 7.5f); curveTo(8.67f, 7.5f, 8f, 6.83f, 8f, 6f); curveTo(8f, 5.17f, 8.67f, 4.5f, 9.5f, 4.5f)
            curveTo(10.33f, 4.5f, 11f, 5.17f, 11f, 6f); curveTo(11f, 6.83f, 10.33f, 7.5f, 9.5f, 7.5f); close()
            moveTo(14.5f, 7.5f); curveTo(13.67f, 7.5f, 13f, 6.83f, 13f, 6f); curveTo(13f, 5.17f, 13.67f, 4.5f, 14.5f, 4.5f)
            curveTo(15.33f, 4.5f, 16f, 5.17f, 16f, 6f); curveTo(16f, 6.83f, 15.33f, 7.5f, 14.5f, 7.5f); close()
            moveTo(17.5f, 11.5f); curveTo(16.67f, 11.5f, 16f, 10.83f, 16f, 10f); curveTo(16f, 9.17f, 16.67f, 8.5f, 17.5f, 8.5f)
            curveTo(18.33f, 8.5f, 19f, 9.17f, 19f, 10f); curveTo(19f, 10.83f, 18.33f, 11.5f, 17.5f, 11.5f); close()
        }
    }

    // Hình giọt nước / thấu kính rõ ràng (sửa lỗi chấm nhỏ ở thẻ Chất liệu Kính)
    val BlurOn: ImageVector = createIcon("BlurOn") {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 2f); curveTo(12f, 2f, 5f, 10.15f, 5f, 15f)
            curveTo(5f, 18.87f, 8.13f, 22f, 12f, 22f); curveTo(15.87f, 22f, 19f, 18.87f, 19f, 15f)
            curveTo(19f, 10.15f, 12f, 2f, 12f, 2f); close()
            moveTo(12f, 19f); curveTo(9.79f, 19f, 8f, 17.21f, 8f, 15f); lineTo(10f, 15f)
            curveTo(10f, 16.1f, 10.9f, 17f, 12f, 17f); lineTo(12f, 19f); close()
        }
    }

    val Visibility: ImageVector = createIcon("Visibility") {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 4.5f); curveTo(7f, 4.5f, 2.73f, 7.61f, 1f, 12f)
            curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f); curveTo(17f, 19.5f, 21.27f, 16.39f, 23f, 12f)
            curveTo(21.27f, 7.61f, 17f, 4.5f, 12f, 4.5f); close()
            moveTo(12f, 17f); curveTo(9.24f, 17f, 7f, 14.76f, 7f, 12f)
            curveTo(7f, 9.24f, 9.24f, 7f, 12f, 7f); curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
            curveTo(17f, 14.76f, 14.76f, 17f, 12f, 17f); close()
            moveTo(12f, 9f); curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
            curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f); curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
            curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f); close()
        }
    }

    val VisibilityOff: ImageVector = createIcon("VisibilityOff") {
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 7f); curveTo(14.76f, 7f, 17f, 9.24f, 17f, 12f)
            curveTo(17f, 12.65f, 16.87f, 13.26f, 16.64f, 13.83f); lineTo(19.56f, 16.75f)
            curveTo(21.07f, 15.49f, 22.26f, 13.86f, 22.99f, 12f); curveTo(21.26f, 7.61f, 16.99f, 4.5f, 11.99f, 4.5f)
            curveTo(10.59f, 4.5f, 9.25f, 4.75f, 8.01f, 5.2f); lineTo(10.17f, 7.36f)
            curveTo(10.74f, 7.13f, 11.35f, 7f, 12f, 7f); close()
            moveTo(2f, 4.27f); lineTo(4.28f, 6.55f); lineTo(4.74f, 7.01f)
            curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f); curveTo(2.73f, 16.39f, 7f, 19.5f, 12f, 19.5f)
            curveTo(13.55f, 19.5f, 15.03f, 19.2f, 16.38f, 18.66f); lineTo(16.8f, 19.08f)
            lineTo(19.73f, 22f); lineTo(21f, 20.73f); lineTo(3.27f, 3f); lineTo(2f, 4.27f); close()
        }
    }

    val Public: ImageVector = Stream
}