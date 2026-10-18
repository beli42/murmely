package org.pixeldroid.media_editor.photoEdit

import android.database.sqlite.SQLiteDatabase
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.pixeldroid.media_editor.common.ui.BunnyTheme
import org.pixeldroid.media_editor.photoEdit.PhotoEditViewModel.Change
import org.pixeldroid.media_editor.photoEdit.customFilters.CustomLayer
import org.pixeldroid.media_editor.photoEdit.customFilters.DatabaseBuilder
import org.pixeldroid.media_editor.photoEdit.customFilters.SavedTemplate
import org.pixeldroid.media_editor.photoEdit.frame.FrameSettings
import org.pixeldroid.media_editor.photoEdit.frame.FrameStyle
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.ConfiguredFilter
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.FilterSettings
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.MirrorPart
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.base
import org.pixeldroid.media_editor.photoEdit.imagine.core.types.settings
import org.pixeldroid.media_editor.photoEdit.imagine.layers.ElsaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SepiaLayer
import org.pixeldroid.media_editor.photoEdit.imagine.layers.SwirlLayer
import org.pixeldroid.media_editor.photoEdit.panel.FilterCategory
import org.pixeldroid.media_editor.photoEdit.panel.FilterPanel
import org.pixeldroid.media_editor.photoEdit.panel.FilterTags
import org.pixeldroid.media_editor.photoEdit.panel.TemplateItem
import org.pixeldroid.media_editor.photoEdit.templates.Template
import org.pixeldroid.media_editor.photoEdit.templates.TemplateFilter
import org.pixeldroid.media_editor.photoEdit.templates.TemplateJson

/** Templates: saved looks of sliders, filters and frame */
@RunWith(AndroidJUnit4::class)
class TemplateTest {

    @get:Rule val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val builtIn = BuiltInFilters()

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private val sample = Template(
        name = "Sommer",
        sliders = Sliders().with(Adjustment.WARMTH, 0.4f).with(Adjustment.CONTRAST, -0.2f),
        filters = listOf(
            TemplateFilter(key = "swirl", settings = FilterSettings(strength = 0.5f, centerX = 0.3f)),
            TemplateFilter(key = "mirror_horizontal", settings = FilterSettings(mirrorPart = MirrorPart.END)),
            TemplateFilter(customName = "Mein Filter", source = "vec4 process(vec4 c, sampler2D i, vec2 t) { return c; }"),
        ),
        frame = FrameSettings.of(FrameStyle.POLAROID, size = 0.08f).copy(hue = 30f, lightness = 0.9f),
    )

    @Test fun jsonKeepsEverything() {
        val json = TemplateJson.write(sample)
        assertEquals(sample, TemplateJson.read("Sommer", json))
        // Unknown values fall back instead of failing
        val odd = TemplateJson.read("x", """{"filters":[{"key":"swirl","mirrorPart":"SIDEWAYS"}],"frame":{"style":"HEXAGON"}}""")
        assertEquals(MirrorPart.UNDECIDED, odd.filters.single().settings.mirrorPart)
        assertEquals(FrameStyle.SOLID, odd.frame!!.style)
    }

    @Test fun everyBuiltInFilterHasAKey() {
        assertEquals(builtIn.color.size + builtIn.geometry.size, BuiltInFilters.KEYS.toSet().size)
        for (filter in builtIn.color + builtIn.geometry) {
            val key = builtIn.keyOf(filter)!!
            assertEquals(filter::class, builtIn.byKey[key]!!::class)
        }
    }

    /** The current look, including the filter being tried, and the frame */
    @Test fun currentLookBecomesATemplate() {
        lateinit var template: Template
        onMain {
            val model = PhotoEditViewModel()
            model.doChange(Change.SlidersChange(Sliders().with(Adjustment.WARMTH, 0.4f)))
            model.doChange(Change.SelectFilter(ElsaLayer()))
            model.applySelectedFilter()
            model.doChange(Change.SelectFilter(SwirlLayer(), FilterSettings(strength = 0.5f)))
            model.chooseFrame(FrameSettings.of(FrameStyle.POLAROID))
            template = model.currentTemplate("Mein Look", builtIn)
        }
        assertEquals("Mein Look", template.name)
        assertEquals(0.4f, template.sliders[Adjustment.WARMTH], 0f)
        assertEquals(listOf("elsa", "swirl"), template.filters.map { it.key })
        assertEquals("the tried filter with its strength", 0.5f, template.filters[1].settings.strength, 0f)
        assertEquals(FrameStyle.POLAROID, template.frame!!.style)
    }

    /** Applying replaces sliders, filters and frame in one step; undo brings back what was there */
    @Test fun applyingReplacesAndCanBeUndone() {
        lateinit var model: PhotoEditViewModel
        onMain {
            model = PhotoEditViewModel()
            model.doChange(Change.SlidersChange(Sliders().with(Adjustment.EXPOSURE, 0.5f)))
            model.doChange(Change.SelectFilter(SepiaLayer()))
            model.applySelectedFilter()
            model.doChange(Change.SelectFilter(ElsaLayer()))   // being tried
            model.addText("Hi", 0.5f, 0.5f)
        }
        val before = model.changes.size
        onMain { model.applyTemplate(sample, builtIn) }
        assertEquals(sample.sliders, model.sliders.value)
        val applied = model.appliedFilters.value
        assertEquals(3, applied.size)
        assertEquals(SwirlLayer::class, applied[0].base::class)
        assertEquals(0.5f, (applied[0] as ConfiguredFilter).settings.strength, 0f)
        assertEquals(MirrorPart.END, applied[1].settings.mirrorPart)
        assertEquals("own filter from its saved shader", sample.filters[2].source, (applied[2].base as CustomLayer).source)
        assertNull("nothing tried any more", model.filter.value)
        assertEquals(sample.frame, model.frame.value)
        assertEquals("texts stay", 1, model.overlay.value.texts.size)
        assertEquals("one step", before + 1, model.changes.size)

        onMain { model.undoChange() }
        assertEquals(0.5f, model.sliders.value[Adjustment.EXPOSURE], 0f)
        assertEquals(listOf(SepiaLayer::class), model.appliedFilters.value.map { it.base::class })
        assertNull(model.frame.value)
    }

    @Test fun unknownFiltersAreLeftOut() {
        lateinit var model: PhotoEditViewModel
        onMain {
            model = PhotoEditViewModel()
            model.applyTemplate(Template("x", filters = listOf(TemplateFilter(key = "from_the_future"), TemplateFilter(key = "sepia"))), builtIn)
        }
        assertEquals(listOf(SepiaLayer::class), model.appliedFilters.value.map { it.base::class })
    }

    /** An older database (only own filters) gets the templates and keeps its filters */
    @Test fun databaseMigrationKeepsOwnFilters() = runBlocking {
        val name = "migration-test"
        context.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `CustomFilter` (`customName` TEXT NOT NULL, `source` TEXT NOT NULL, `uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("INSERT INTO CustomFilter (customName, source) VALUES ('Alt', 'vec4 process(vec4 c, sampler2D i, vec2 t) { return c; }')")
            db.version = 1
        }
        val database = DatabaseBuilder.build(context, name)
        try {
            assertEquals(listOf("Alt"), database.filtersDao().getAll().first().map { it.customName })
            database.templatesDao().insert(SavedTemplate("Neu", TemplateJson.write(sample)))
            assertEquals(listOf("Neu"), database.templatesDao().getAll().first().map { it.name })
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    /** In the panel: save with a name, apply by tapping, rename and delete with a long press */
    @Test fun panelSavesAppliesRenamesAndDeletes() {
        var saved: String? = null
        var applied: Int? = null
        var renamed: Pair<Int, String>? = null
        var deleted: Int? = null
        compose.setContent {
            BunnyTheme {
                FilterPanel(
                    groups = mapOf(FilterCategory.COLOR to emptyList()),
                    thumbnailOf = { null }, selected = null, vertical = false,
                    onSelect = {}, onApply = {}, onLongPress = {}, onCreate = {},
                    templates = listOf(TemplateItem(7, "Sommer", null)),
                    onSaveTemplate = { saved = it },
                    onApplyTemplate = { applied = it },
                    onRenameTemplate = { id, n -> renamed = id to n },
                    onDeleteTemplate = { deleted = it },
                )
            }
        }
        compose.onNodeWithTag(FilterTags.category(FilterCategory.TEMPLATES)).performScrollTo().performClick()
        compose.onNodeWithTag(FilterTags.SAVE_TEMPLATE).performClick()
        compose.onNodeWithTag(FilterTags.TEMPLATE_NAME).performTextReplacement("Winter")
        compose.onNodeWithTag(FilterTags.TEMPLATE_OK).performClick()
        assertEquals("Winter", saved)

        compose.onNodeWithTag(FilterTags.template(7)).performClick()
        assertEquals(7, applied)

        compose.onNodeWithTag(FilterTags.template(7)).performTouchInput { longClick() }
        compose.onNodeWithTag(FilterTags.TEMPLATE_NAME).performTextReplacement("Sommer 2")
        compose.onNodeWithTag(FilterTags.TEMPLATE_OK).performClick()
        assertEquals(7 to "Sommer 2", renamed)

        compose.onNodeWithTag(FilterTags.template(7)).performTouchInput { longClick() }
        compose.onNodeWithTag(FilterTags.TEMPLATE_DELETE).performClick()
        assertEquals(7, deleted)
    }
}
