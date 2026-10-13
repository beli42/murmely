package eu.artectrex.bunny

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.PICTURE_URI

/** The flow on the start screen: pick a file, edit it, save, collage, share from other apps */
@RunWith(AndroidJUnit4::class)
class EditViewModelTest {

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application

    @get:Rule val savedImage = SavedTestImage(app)

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) = EditViewModel(app, state)

    private val picked = Uri.parse("content://media/external/images/media/4711")
    private fun result(uri: Uri) = Intent().putExtra(PICTURE_URI, uri.toString())

    @Test fun pickingAFileRemembersIt() {
        val vm = viewModel()
        vm.filePickResult(picked)
        assertEquals(picked, vm.uiState.value.uriToEdit)
    }

    @Test fun pickingNothingChangesNothing() {
        val vm = viewModel()
        vm.filePickResult(null)
        assertEquals(EditFlowUiState(), vm.uiState.value)
    }

    @Test fun launchingTheEditorMovesThePickedFile() {
        val vm = viewModel()
        vm.filePickResult(picked)
        vm.launchedEdit()
        assertNull(vm.uiState.value.uriToEdit)              // not launched a second time
        assertEquals(picked, vm.uiState.value.videoUri)     // remembered as the original
    }

    @Test fun savedImageShowsLocationAndSize() {
        val vm = viewModel()
        vm.filePickResult(picked)
        vm.launchedEdit()
        vm.editResult(result(savedImage.uri))

        val size = app.contentResolver.openFileDescriptor(savedImage.uri, "r")!!.use { it.statSize }
        val expected = app.getString(
            R.string.image_saved_as_size, savedImage.location, Formatter.formatShortFileSize(app, size)
        )
        assertEquals(expected, vm.uiState.value.savedMessage)
        assertEquals("start screen stays", Screen.START, vm.uiState.value.screen)
    }

    @Test fun messageIsShownOnlyOnce() {
        val vm = viewModel()
        vm.launchedCollage()
        vm.editResult(result(savedImage.uri))
        vm.savedMessageShown()
        assertNull(vm.uiState.value.savedMessage)
    }

    @Test fun originalComingBackMeansNothingWasSaved() {
        val vm = viewModel()
        vm.filePickResult(picked)
        vm.launchedEdit()
        vm.editResult(result(picked))
        assertNull(vm.uiState.value.savedMessage)
    }

    /** Regression: after editing a video, a collage was treated as a video */
    @Test fun collageAfterVideoIsAnImage() {
        val vm = viewModel()
        vm.filePickResult(Uri.parse("file:///sdcard/Movies/clip.mp4"))   // type from extension: video/mp4
        assertEquals("video/mp4", vm.uiState.value.type)
        vm.launchedCollage()
        assertEquals("image/png", vm.uiState.value.type)

        vm.editResult(result(savedImage.uri))
        assertFalse("must not start video encoding", vm.uiState.value.startVideoEncoding)
        assertEquals(true, vm.uiState.value.savedMessage?.contains(savedImage.displayName))
    }

    @Test fun fileSharedByAnotherAppIsOpened() {
        val vm = viewModel(SavedStateHandle(mapOf(Intent.EXTRA_STREAM to picked)))
        assertEquals(picked, vm.uiState.value.uriToEdit)
    }

    @Test fun cancellingVideoProcessingReturnsToTheStartScreen() {
        val vm = viewModel()
        vm.cancelVideoProcessing()
        assertEquals(Screen.START, vm.uiState.value.screen)
        assertNull(vm.uiState.value.video)
    }

    /** Security: a file:// Uri could point to the app's own private files */
    @Test fun sharedFileUrisAreRejected() {
        val privateFile = Uri.fromFile(app.filesDir.resolve("secret.txt"))
        val vm = viewModel(SavedStateHandle(mapOf(Intent.EXTRA_STREAM to privateFile)))
        assertNull(vm.uiState.value.uriToEdit)
    }
}
