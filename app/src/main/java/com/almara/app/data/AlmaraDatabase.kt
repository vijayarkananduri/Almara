package com.almara.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [UriMetadataEntity::class, VirtualRuleEntity::class, FolderEntity::class, FolderFileRefEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AlmaraDatabase : RoomDatabase() {
    abstract fun uriMetadataDao(): UriMetadataDao
    abstract fun virtualRuleDao(): VirtualRuleDao
    abstract fun folderDao(): FolderDao

    companion object {
        /** Supply an application-scoped key repository; this does not log or expose the passphrase. */
        fun open(context: Context, keys: SqlCipherKeyRepository, name: String = "almara.db"): AlmaraDatabase {
            val passphrase = keys.getOrCreatePassphrase()
            val factory = SupportFactory(passphrase)
            return try {
                Room.databaseBuilder(context.applicationContext, AlmaraDatabase::class.java, name)
                    .openHelperFactory(factory)
                    .build()
            } catch (failure: Throwable) {
                throw failure
            }
        }
    }
}
