package com.videoplayer.app.network.cloud

import android.content.Context

object CloudRepository {
    suspend fun listDirectory(context: Context, source: CloudSourceConfig, folderPathOrId: String): Result<List<CloudEntry>> {
        return when (source.type) {
            CloudSourceType.GOOGLE_DRIVE -> GoogleDriveProvider.listDirectory(context, source, folderPathOrId)
            CloudSourceType.WEBDAV -> WebDavProvider.listDirectory(source, folderPathOrId)
            CloudSourceType.SMB -> SmbProvider.listDirectory(source, folderPathOrId)
        }
    }

    suspend fun resolvePlayableUrl(context: Context, source: CloudSourceConfig, entry: CloudEntry): Result<String> {
        return when (source.type) {
            CloudSourceType.GOOGLE_DRIVE -> GoogleDriveProvider.resolvePrivatePlayableUrl(context, source, entry)
            CloudSourceType.WEBDAV -> WebDavProvider.resolvePlayableUrl(source, entry)
            CloudSourceType.SMB -> SmbProvider.resolvePlayableUrl(source, entry)
        }
    }
}