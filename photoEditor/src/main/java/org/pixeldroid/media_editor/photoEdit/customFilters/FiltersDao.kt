package org.pixeldroid.media_editor.photoEdit.customFilters

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FiltersDao {
    @Query("SELECT * FROM customfilter")
    fun getAll(): Flow<List<CustomFilter>>

    @Insert
     fun insertAll(vararg layers: CustomFilter)

    @Delete
    fun delete(layer: CustomFilter)
}
