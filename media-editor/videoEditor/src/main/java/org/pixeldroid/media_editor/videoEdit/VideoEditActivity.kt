package org.pixeldroid.media_editor.videoEdit

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.Log
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.google.android.material.slider.RangeSlider
import org.pixeldroid.media_editor.common.PICTURE_POSITION
import org.pixeldroid.media_editor.common.PICTURE_URI
import org.pixeldroid.media_editor.common.dpToPx
import org.pixeldroid.media_editor.videoEdit.databinding.ActivityVideoEditBinding
import java.io.File
import kotlin.concurrent.thread

const val TAG = "VideoEditActivity"

/**
 * Video editor: keep a part of the video, change the speed, mute, crop. Playback uses Media3
 * ExoPlayer; the conversion itself is done later by [VideoEncoder] (see [startEncoding]).
 */
class VideoEditActivity : AppCompatActivity() {

    private lateinit var videoUri: Uri
    private lateinit var player: ExoPlayer
    private var videoPosition: Int = -1

    private var cropRelativeDimensions: RelativeCropPosition = RelativeCropPosition()

    private var speed: Int = VideoEditPlan.NORMAL_SPEED_INDEX
        set(value) {
            field = value
            player.setPlaybackSpeed(VideoEditPlan.SPEEDS[value])
            // A changed speed removes the audio, shown as muted
            if (value != VideoEditPlan.NORMAL_SPEED_INDEX && !binding.muter.isSelected) binding.muter.callOnClick()
        }

    private lateinit var binding: ActivityVideoEditBinding

    private val positionHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.topBar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        // Handle back pressed button
        onBackPressedDispatcher.addCallback(this) {
            if (binding.cropImageView.visibility == View.VISIBLE) {
                showCropInterface(false)
            } else if (noEdits()) {
                this.isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            } else {
                AlertDialog.Builder(binding.root.context).apply {
                    setMessage(R.string.save_before_returning)
                    setPositiveButton(android.R.string.ok) { _, _ -> returnWithValues() }
                    setNegativeButton(R.string.no_cancel_edit) { _, _ ->
                        this@addCallback.isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }.show()
            }
        }

        binding.videoRangeSeekBar.setCustomThumbDrawablesForValues(R.drawable.thumb_left, R.drawable.double_circle, R.drawable.thumb_right)
        binding.videoRangeSeekBar.thumbRadius = 20.dpToPx(this)

        videoUri = IntentCompat.getParcelableExtra(intent, PICTURE_URI, Uri::class.java)!!
        videoPosition = intent.getIntExtra(PICTURE_POSITION, -1)

        // Duration in seconds, or null
        val duration: Float? = videoDurationSeconds(videoUri)

        binding.videoRangeSeekBar.valueFrom = 0f
        binding.videoRangeSeekBar.valueTo = duration ?: 100f
        binding.videoRangeSeekBar.values = listOf(0f, (duration ?: 100f) / 2, duration ?: 100f)

        player = ExoPlayer.Builder(this).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
        }
        binding.videoView.player = player

        binding.muter.setOnClickListener {
            if (!binding.muter.isSelected) player.volume = 0f
            else {
                player.volume = 1f
                speed = VideoEditPlan.NORMAL_SPEED_INDEX
            }
            binding.muter.isSelected = !binding.muter.isSelected
        }

        binding.cropper.setOnClickListener {
            showCropInterface(show = true, uri = videoUri)
        }

        binding.saveCropButton.setOnClickListener {
            // This is the rectangle selected by the crop
            val cropRect = binding.cropImageView.cropWindowRect

            // This is the rectangle of the whole image
            val fullImageRect: Rect = binding.cropImageView.getInitialCropWindowRect()

            // x, y are coordinates of top left, in the ImageView
            val x = cropRect.left - fullImageRect.left
            val y = cropRect.top - fullImageRect.top

            // width and height selected by the crop
            val width = cropRect.width()
            val height = cropRect.height()

            // To avoid having to calculate the dimensions of the video here, we pass
            // relative width, height and x, y back (see VideoEditPlan.cropNdc)
            cropRelativeDimensions = RelativeCropPosition(
                relativeWidth = width/fullImageRect.width(),
                relativeHeight = height/fullImageRect.height(),
                relativeX = x/fullImageRect.width(),
                relativeY = y/fullImageRect.height()
            )

            // If a crop was saved, change the color of the crop button to give a visual indication
            if(!cropRelativeDimensions.notCropped()){
                val typedValue = TypedValue()
                val color: Int = if (binding.checkMarkCropped.context.theme
                        .resolveAttribute(R.attr.colorSecondary, typedValue, true)
                ) typedValue.data else Color.TRANSPARENT

                binding.cropper.drawable.setTint(color)
            } else {
                // Else reset the tint
                binding.cropper.drawable.setTintList(null)
            }

            showCropInterface(show = false)
        }

        player.seekTo((binding.videoRangeSeekBar.values[1] * 1000).toLong())

        // Move the middle thumb along with the playback
        positionHandler.post(object : Runnable {
            override fun run() {
                val current = player.currentPosition / 1000f
                val values = binding.videoRangeSeekBar.values
                if (current >= values[0] && current <= values[2]) {
                    binding.videoRangeSeekBar.values = listOf(values[0], current, values[2])
                }
                positionHandler.postDelayed(this, 1000)
            }
        })

        binding.videoRangeSeekBar.addOnChangeListener { rangeSlider: RangeSlider, value, fromUser ->
            // Responds to when the middle slider's value is changed
            if (fromUser && value != rangeSlider.values[0] && value != rangeSlider.values[2]) {
                player.seekTo((rangeSlider.values[1] * 1000).toLong())
            }
        }

        binding.videoRangeSeekBar.setLabelFormatter { value: Float ->
            DateUtils.formatElapsedTime(value.toLong())
        }

        binding.speeder.setOnClickListener {
            AlertDialog.Builder(this).apply {
                setIcon(R.drawable.speed)
                setTitle(R.string.video_speed)
                setSingleChoiceItems(VideoEditPlan.SPEEDS.map { speedLabel(it) }.toTypedArray(), speed) { dialog, which ->
                    speed = which
                    dialog.dismiss()
                }
                setNegativeButton(android.R.string.cancel) { _, _ -> }
            }.show()
        }

        duration?.let { loadThumbnails(it) }

        resetControls()
    }

    /** "0.5x", "1x", "2x" */
    private fun speedLabel(speed: Float): String =
        (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "x"

    /** Fills the reel below the video with frames, read in the background */
    private fun loadThumbnails(duration: Float) {
        val views = listOf(
            binding.thumbnail1, binding.thumbnail2, binding.thumbnail3, binding.thumbnail4,
            binding.thumbnail5, binding.thumbnail6, binding.thumbnail7,
        )
        val context = applicationContext
        thread(name = "VideoThumbnails") {
            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, videoUri)
                    views.forEachIndexed { index, view ->
                        // Middle of each seventh of the video
                        val timeUs = (duration * 1_000_000 * (index + 0.5f) / views.size).toLong()
                        val frame = retriever.getScaledFrameAtTime(
                            timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMBNAIL_SIZE, THUMBNAIL_SIZE
                        )
                        if (frame != null) showThumbnail(view, frame)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not read video frames", e)
            }
        }
    }

    private fun showThumbnail(view: ImageView, frame: Bitmap) = runOnUiThread {
        if (!isFinishing && !isDestroyed) Glide.with(this).load(frame).centerCrop().into(view)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.edit_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressedDispatcher.onBackPressed()
            R.id.action_save -> returnWithValues()
            R.id.action_reset -> resetControls()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun noEdits(): Boolean {
        val wholeVideo = binding.videoRangeSeekBar.values.let {
            it[0] == 0f && it[2] == binding.videoRangeSeekBar.valueTo
        }
        val muted = binding.muter.isSelected
        val speedUnchanged = speed == VideoEditPlan.NORMAL_SPEED_INDEX
        return !muted && wholeVideo && speedUnchanged && cropRelativeDimensions.notCropped()
    }

    private fun showCropInterface(show: Boolean, uri: Uri? = null) {
        val visibilityOfOthers = if (show) View.GONE else View.VISIBLE
        val visibilityOfCrop = if (show) View.VISIBLE else View.GONE

        if (show) player.pause()

        if (show) binding.cropSavedCard.visibility = View.GONE
        else if (!cropRelativeDimensions.notCropped()) binding.cropSavedCard.visibility = View.VISIBLE

        binding.muter.visibility = visibilityOfOthers
        binding.speeder.visibility = visibilityOfOthers
        binding.cropper.visibility = visibilityOfOthers
        binding.videoRangeSeekBar.visibility = visibilityOfOthers
        binding.videoView.visibility = visibilityOfOthers
        binding.thumbnail1.visibility = visibilityOfOthers
        binding.thumbnail2.visibility = visibilityOfOthers
        binding.thumbnail3.visibility = visibilityOfOthers
        binding.thumbnail4.visibility = visibilityOfOthers
        binding.thumbnail5.visibility = visibilityOfOthers
        binding.thumbnail6.visibility = visibilityOfOthers
        binding.thumbnail7.visibility = visibilityOfOthers

        binding.cropImageView.visibility = visibilityOfCrop
        binding.saveCropButton.visibility = visibilityOfCrop

        if (show && uri != null) binding.cropImageView.setImageUriAsync(uri, cropRelativeDimensions)
    }

    private fun returnWithValues() {
        val intent = Intent().apply {
            putExtra(PICTURE_POSITION, videoPosition)
            putExtra(
                VIDEO_ARGUMENTS_TAG, VideoEditArguments(
                    muted = binding.muter.isSelected,
                    videoStart = binding.videoRangeSeekBar.values.first(),
                    videoEnd = binding.videoRangeSeekBar.values[2],
                    speedIndex = speed,
                    videoCrop = cropRelativeDimensions,
                )
            )
            putExtra(MODIFIED, !noEdits())
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun resetControls() {
        binding.videoRangeSeekBar.values = listOf(0f, binding.videoRangeSeekBar.valueTo / 2, binding.videoRangeSeekBar.valueTo)
        binding.muter.isSelected = false
        player.volume = 1f
        speed = VideoEditPlan.NORMAL_SPEED_INDEX

        binding.cropImageView.resetCropRect()
        cropRelativeDimensions = RelativeCropPosition()
        binding.cropper.drawable.setTintList(null)
        binding.cropSavedCard.visibility = View.GONE
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        positionHandler.removeCallbacksAndMessages(null)
        player.release()
    }

    companion object {
        const val VIDEO_ARGUMENTS_TAG = "org.pixeldroid.media_editor.VideoEditTag"
        const val MODIFIED = "VideoEditModifiedTag"
        private const val THUMBNAIL_SIZE = 256

        /**
         * Converts [originalUri] with the edits in [arguments], see [VideoEncoder.start].
         *
         * @param targetUri File to save to. If null, a temporary file is used and reported with trackTempFile
         */
        fun startEncoding(
            originalUri: Uri,
            targetUri: Uri? = null,
            arguments: VideoEditArguments,
            context: Context,
            registerSession: (Uri, Long) -> Unit,
            trackTempFile: (File) -> Unit,
            videoEncodeProgress: (Uri, Int, Boolean, Uri?, Boolean) -> Unit,
        ) = VideoEncoder.start(context, originalUri, targetUri, arguments, registerSession, trackTempFile, videoEncodeProgress)

        fun cancelEncoding() = VideoEncoder.cancelAll()

        fun cancelEncoding(sessionId: Long) = VideoEncoder.cancel(sessionId)
    }
}
