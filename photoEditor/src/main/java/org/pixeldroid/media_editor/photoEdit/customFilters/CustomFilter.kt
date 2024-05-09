package org.pixeldroid.media_editor.photoEdit.customFilters

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

@Entity
data class CustomFilter(
     val customName: String?, val source: String,
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    ){
    fun toLayer() = CustomLayer(customName, source)
}

data class CustomLayer(
    override val customName: String?,
    override val source: String,
): ImagineLayer(initialIntensity = 1f){
    /**
     * We don't use the [name] in a [CustomLayer], but the [customName]
     */
    override val name = null
}