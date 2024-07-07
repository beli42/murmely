package org.pixeldroid.media_editor.photoEdit.customFilters

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FiltersDao {
    @Query("SELECT * FROM customfilter")
    fun getAll(): Flow<List<CustomFilter>>

    /**
     * Insert a user, if it already exists return -1
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(layers: CustomFilter): Long

    @Transaction
    suspend fun insertOrUpdate(filter: CustomFilter) {
        if (insert(filter) == -1L) {
            update(filter)
        }
    }

    @Update
    suspend fun update(layers: CustomFilter)

    @Delete
    fun delete(layer: CustomFilter)
}
