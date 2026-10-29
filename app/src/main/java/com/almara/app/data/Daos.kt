package com.almara.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface UriMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(items: List<UriMetadataEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: UriMetadataEntity)

    @Query("SELECT * FROM uri_metadata WHERE uri = :uri")
    suspend fun get(uri: String): UriMetadataEntity?

    @Query("SELECT * FROM uri_metadata WHERE isStale = 0 ORDER BY displayName COLLATE NOCASE")
    fun observeAll(): Flow<List<UriMetadataEntity>>

    @Query("SELECT * FROM uri_metadata WHERE isStale = 0 ORDER BY modifiedAtMillis DESC")
    suspend fun getAll(): List<UriMetadataEntity>

    @Query("SELECT * FROM uri_metadata WHERE uri IN (:uris)")
    suspend fun getByUris(uris: List<String>): List<UriMetadataEntity>

    @Query("DELETE FROM uri_metadata WHERE isStale = 1 AND indexedAtMillis < :cutoffMillis")
    suspend fun deleteStale(cutoffMillis: Long): Int

    @Query("UPDATE uri_metadata SET isStale = 1")
    suspend fun markAllStale(): Int

    @Query("DELETE FROM uri_metadata")
    suspend fun clear()

    /** Reconcile a complete scan atomically without a giant NOT IN bind list; stale rows are retained for safe cleanup. */
    @Transaction
    suspend fun applyFullScan(items: List<UriMetadataEntity>): Int {
        markAllStale()
        items.distinctBy { it.uri }.chunked(300).forEach { batch -> upsert(batch.map { it.copy(isStale = false) }) }
        return items.size
    }

    @Query("SELECT contentDate AS day, COUNT(*) AS count FROM uri_metadata WHERE isStale = 0 AND contentDate IS NOT NULL GROUP BY contentDate ORDER BY contentDate DESC")
    fun observeDailyCounts(): Flow<List<DailyCount>>

    @Query("SELECT contentDate AS day, COUNT(*) AS count FROM uri_metadata WHERE isStale = 0 AND contentDate = :day GROUP BY contentDate")
    suspend fun dailyCount(day: String): DailyCount?
}

data class DailyCount(val day: String, val count: Int)

@Dao
interface VirtualRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: VirtualRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<VirtualRuleEntity>)

    @Query("SELECT * FROM virtual_rules ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<VirtualRuleEntity>>

    @Query("SELECT * FROM virtual_rules WHERE id = :id")
    suspend fun get(id: String): VirtualRuleEntity?

    @Query("SELECT * FROM virtual_rules ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<VirtualRuleEntity>

    @Query("DELETE FROM virtual_rules")
    suspend fun clear()

    @Query("DELETE FROM virtual_rules WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(folder: FolderEntity)

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun get(id: String): FolderEntity?

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE expiresAtMillis IS NOT NULL ORDER BY expiresAtMillis")
    suspend fun getExpiring(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE archivedUntilMillis IS NOT NULL AND archivedUntilMillis <= :nowMillis")
    suspend fun getArchivesPastRetention(nowMillis: Long): List<FolderEntity>

    @Query("DELETE FROM folders")
    suspend fun clear()

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFile(ref: FolderFileRefEntity)

    @Query("DELETE FROM folder_file_refs WHERE folderId = :folderId AND uri = :uri")
    suspend fun removeFile(folderId: String, uri: String): Int

    @Query("SELECT uri FROM folder_file_refs WHERE folderId = :folderId ORDER BY addedAtMillis DESC")
    fun observeFileUris(folderId: String): Flow<List<String>>

    @Query("SELECT * FROM folder_file_refs WHERE folderId = :folderId ORDER BY addedAtMillis DESC")
    suspend fun getFileRefs(folderId: String): List<FolderFileRefEntity>

    @Query("DELETE FROM folder_file_refs WHERE folderId = :folderId")
    suspend fun clearFiles(folderId: String)

    @Transaction
    suspend fun deleteFolderAndMembership(folderId: String) {
        clearFiles(folderId)
        delete(folderId)
    }
}
