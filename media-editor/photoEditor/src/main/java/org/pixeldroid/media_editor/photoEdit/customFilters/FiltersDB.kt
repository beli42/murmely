package org.pixeldroid.media_editor.photoEdit.customFilters

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(entities = [CustomFilter::class, SavedTemplate::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun filtersDao(): FiltersDao
    abstract fun templatesDao(): TemplatesDao
}

/** Version 2 adds the templates; the own filters stay as they are */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `SavedTemplate` " +
                "(`name` TEXT NOT NULL, `json` TEXT NOT NULL, `uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)"
        )
    }
}

object DatabaseBuilder {

    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
        if (INSTANCE == null) {
            synchronized(AppDatabase::class) {
                if (INSTANCE == null) {
                    INSTANCE = buildRoomDB(context)
                }
            }
        }
        return INSTANCE!!
    }

    private fun buildRoomDB(context: Context) = build(context, "custom-filters")

    /** The database in the file [name] (another name for tests) */
    fun build(context: Context, name: String): AppDatabase =
        Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2)
            .build()

}