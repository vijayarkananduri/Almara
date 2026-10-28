package com.almara.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "uri_metadata", indices = [Index("mimeType"), Index("modifiedAtMillis"), Index("contentDate"), Index("source")])
data class UriMetadataEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    /** ISO-8601 local date when known (yyyy-MM-dd); deliberately stored as text for portable queries. */
    val contentDate: String?,
    val source: String,
    val indexedAtMillis: Long,
    val isStale: Boolean = false,
    val contentHash: String? = null
)

@Entity(tableName = "virtual_rules", indices = [Index("updatedAtMillis")])
data class VirtualRuleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val query: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val enabled: Boolean = true,
    val priority: Int = 0
)

@Entity(tableName = "folders", indices = [Index("updatedAtMillis")])
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val ruleId: String? = null,
    val expiresAtMillis: Long? = null,
    val archiveOnExpiry: Boolean = false,
    val archivedUntilMillis: Long? = null,
    val noticeSentAtMillis: Long? = null
)

@Entity(tableName = "folder_file_refs", primaryKeys = ["folderId", "uri"], indices = [Index("uri")])
data class FolderFileRefEntity(
    val folderId: String,
    val uri: String,
    val addedAtMillis: Long
)
