# R8 rules for apps that use the photo editor (applied when the app is shrunk)

# Every filter is its own class: the editor tells filters apart by their class (the one being
# tried, the key saved in templates). R8 would otherwise merge filters of the same shape into
# one class, so they could no longer be told apart. Names may still be shortened.
-keep,allowobfuscation class * extends org.pixeldroid.media_editor.photoEdit.imagine.core.types.ImagineLayer
