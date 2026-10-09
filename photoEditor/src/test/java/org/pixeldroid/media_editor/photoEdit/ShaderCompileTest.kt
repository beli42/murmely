package org.pixeldroid.media_editor.photoEdit

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Compiles the GLSL of every layer (layer_base.frag + the layer's process()) with
 * glslangValidator as OpenGL ES 3.0, so a broken shader is found before it reaches a device.
 * Skipped if glslangValidator is not installed (Linux: package glslang-tools).
 */
class ShaderCompileTest {

    private val moduleDir = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "src/main/res/raw/layer_base.frag").exists() }
    private val base = File(moduleDir, "src/main/res/raw/layer_base.frag").readText()
    private val layerDir = File(moduleDir, "src/main/java/org/pixeldroid/media_editor/photoEdit/imagine/layers")

    private fun validatorAvailable() = try {
        ProcessBuilder("glslangValidator", "--version").start().waitFor(10, TimeUnit.SECONDS)
    } catch (e: Exception) { false }

    @Test fun allLayerShadersCompile() {
        assumeTrue("glslangValidator not installed", validatorAvailable())
        val sources = layerDir.listFiles { f -> f.extension == "kt" }!!.sortedBy { it.name }.mapNotNull { file ->
            Regex("source: String = \"\"\"(.*?)\"\"\"", RegexOption.DOT_MATCHES_ALL)
                .find(file.readText())?.groupValues?.get(1)?.trimIndent()?.let { file.name to it }
        }
        assertTrue("no layer sources found in $layerDir", sources.size >= 10)

        val failures = sources.mapNotNull { (name, source) ->
            val shader = File.createTempFile("layer", ".frag").apply { writeText(base + "\n" + source) }
            val process = ProcessBuilder("glslangValidator", shader.absolutePath).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(30, TimeUnit.SECONDS)
            shader.delete()
            if (process.exitValue() != 0) "$name:\n$output" else null
        }
        assertTrue("Shaders that don't compile:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
