package org.pixeldroid.media_editor.photoEdit.customFilters

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CustomFilter::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun filtersDao(): FiltersDao
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

    private fun buildRoomDB(context: Context) =
        Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java, "custom-filters"
        ).allowMainThreadQueries().build()

}