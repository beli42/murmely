package org.pixeldroid.media_editor.photoEdit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import org.pixeldroid.media_editor.photoEdit.databinding.ActivityLogviewBinding
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader

class LogViewActivity: AppCompatActivity() {
    companion object {
        private var logFile: File? = null

        private const val TAG = "LogViewActivity"

        fun initLogFile(cacheDir: File) {
            logFile = File(cacheDir, "shaderLogFile.txt")
        }

        fun deleteLogFile() {
            logFile?.delete()
        }

        fun launchLogView(context: Context) = View.OnClickListener {
            val intent = Intent(context, LogViewActivity::class.java)
            context.startActivity(intent)
        }

        fun appendToLogFile(tag: String, text: String) {
            Log.e(tag, text)
            try {
                logFile?.let {
                    val fileWriter = FileWriter(logFile, true)
                    fileWriter.append(text)
                    fileWriter.close()
                } ?: Log.e(TAG, "Log file null, cannot log above error to file")
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
    private lateinit var binding: ActivityLogviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLogviewBinding.inflate(layoutInflater)

        setContentView(binding.root)
        loadTextFile()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Shader logs"
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private fun loadTextFile() {
        try {
            logFile?.let {
                val reader = BufferedReader(InputStreamReader(logFile?.inputStream()))
                val sb = StringBuilder()
                var line: String?
                while ((reader.readLine().also { line = it }) != null) {
                    sb.append(line).append("\n")
                }
                reader.close()
                binding.webView.loadDataWithBaseURL(null, sb.toString(), "text/plain", "utf-8", null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}