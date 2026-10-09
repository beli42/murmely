# Android Media Editor

This is a library that allows any Android application to quickly add Photo and Video editing.

It is very much in the early stages, and maybe the API is not well adapted for applications other than PixelDroid. Feedback and help is welcome!

## Installation

* Add the JitPack and repository to your build file

Add it in your root build.gradle at the end of repositories:
```gradle
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

* Now add the dependency to your app

In your app's build gradle, add:
```gradle
implementation 'org.pixeldroid.pixeldroid:android-media-editor:2.0'
```

(replace the version number by the version number of the latest release)

If you only need the photo editor or only the video editor functionality, you can also choose that:
```gradle
def ME-version = "2.0"
implementation 'org.pixeldroid.pixeldroid.android-media-editor:common:ME-version'
implementation 'org.pixeldroid.pixeldroid.android-media-editor:photoEdit:ME-version'
// OR
implementation 'org.pixeldroid.pixeldroid.android-media-editor:videoEdit:ME-version'
```

Especially if you don't need the video edit functionality, it can be good to omit it because it can get quite big.

If you do depend on the video edit module, then I recommend using split APKs (if you distribute your app on F-Droid or yourself, Google Play might handle this using the app bundles already) so that you don't bundle the native video processing code 4 times (one for each CPU architecture). See [this PixelDroid commit](https://gitlab.shinice.net/pixeldroid/PixelDroid/-/commit/234be72f594198359e9c8f5cc8f5029008bf367f) and [this F-Droid MR](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/14701) for how this was done for PixelDroid.

## Usage

To edit a photo, open the relevant activity with an Intent: 

```kotlin

val intent = Intent(this,
        if(video) VideoEditActivity::class.java else PhotoEditActivity::class.java
    )
    .putExtra(org.pixeldroid.media_editor.common.PICTURE_URI, model.getPhotoData().value!![position].imageUri)
    .putExtra(org.pixeldroid.media_editor.common.PICTURE_POSITION, position)

editResultContract.launch(intent)
```

where `editResultContract` is a ActivityResultLauncher like this:

```kotlin
private val editResultContract: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
    result: ActivityResult? ->
    if (result?.resultCode == Activity.RESULT_OK && result.data != null) {
        // The edit returned successfully, you can get the results from the intent data:

        // Position: this is the value you gave while creating the Intent
        val position: Int = result.data!!.getIntExtra(org.pixeldroid.media_editor.common.PICTURE_POSITION, 0)

        // If you edited a video:
        if(video){
            val modified: Boolean = data.getBooleanExtra(VideoEditActivity.MODIFIED, false)
            if(modified){
                val videoEncodingArguments: VideoEditActivity.VideoEditArguments? = data.getSerializableExtra(VideoEditActivity.VIDEO_ARGUMENTS_TAG) as? VideoEditActivity.VideoEditArguments

                // You need to track the encoding sessions and cancel them when needed.
                // Here, we cancel the previous session for this image, because we are about to start a new one
                sessionMap[imageUri]?.let { VideoEditActivity.cancelEncoding(it) }


                videoEncodingArguments?.let {
                    VideoEditActivity.startEncoding(imageUri, it, context,
                        // Callback that will register new sessions as they are started.
                        // See below for signature
                        registerNewFFmpegSession = ::registerNewFFmpegSession,
                        // Callback that will let you track temporary files as they are created, such as results of the video editing or files used to store video analysis results.
                        // Move or copy files you want to keep, then delete the rest to not leak space.
                        // See below for signature.
                        trackTempFile = ::trackTempFile,
                        // Callback used to inform you of progress in the video editing.
                        // See below for signature.
                        videoEncodeProgress = ::videoEncodeProgress
                    )
                }
            }

        }
        // Otherwise it was an image:
        else imageUri = data.getStringExtra(org.pixeldroid.media_editor.common.PICTURE_URI)!!.toUri()


    } else if(result?.resultCode != Activity.RESULT_CANCELED){
        Toast.makeText(applicationContext, "Error while editing", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Register a new session in a map (you can manage them as you prefer, but don't forget to cancel them whenever you can)
 */
fun registerNewFFmpegSession(uri: Uri, sessionId: Long) {
    sessionMap[uri] = sessionId
}

/**
 * Track temporary files. Here, they are just added to an ArrayList
 */
fun trackTempFile(file: File) {
    tempFiles.add(file)
}

/**
     * @param originalUri the Uri of the file you sent to be edited
     * @param progress percentage of (this pass of) encoding that is done
     * @param firstPass Whether this is the first pass (currently for analysis of video stabilization) or the second (and last) pass.
     * @param outputVideoPath when not null, it means the encoding is done and the result is saved in this file
     * @param error is true when there has been an error during encoding.
     */
fun videoEncodeProgress(originalUri: Uri, progress: Int, firstPass: Boolean, outputVideoPath: Uri?, error: Boolean){}

```

`PICTURE_POSITION` is an integer that you give in the Intent, and it gets passed back in the result data, to help you know which picture just finished editing. If you only have one picture, you don't need to put it in the Intent, or worry about it in the result.

You should keep track of the encoding sessions, and cancel them when you know you don't need them anymore. For example when the activity is destroyed, or when you launch a new encoding for the same video that should override the last one.


## Help, something is not working :(

If something is not clear, please go look in the [PixelDroid code](https://gitlab.shinice.net/pixeldroid/PixelDroid) for more precision in how the library is used. If that doesn't help, send a message on Matrix: `#pixeldroid:gnugen.ch`
