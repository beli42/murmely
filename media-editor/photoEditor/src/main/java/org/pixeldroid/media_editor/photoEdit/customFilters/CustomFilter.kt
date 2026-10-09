package org.pixeldroid.media_editor.photoEdit.customFilters

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer

@Entity
data class CustomFilter(
     val customName: String, val source: String,
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    ){
    fun toLayer() = CustomLayer(customName, source, uid=uid)
}

data class CustomLayer(
    override val customName: String,
    override val source: String,
    val uid: Int,
): ImagineLayer(initialIntensity = 1f){
    /**
     * We don't use the [name] in a [CustomLayer], but the [customName]
     */
    override val name = null
    fun toFilter() = CustomFilter(customName, source, uid)
}