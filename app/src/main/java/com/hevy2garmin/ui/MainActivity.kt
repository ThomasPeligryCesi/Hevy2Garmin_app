package com.hevy2garmin.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.hevy2garmin.R
import com.hevy2garmin.converter.ExerciseMapper
import com.hevy2garmin.converter.GarminCsvExporter
import com.hevy2garmin.converter.HevyCsvParser
import com.hevy2garmin.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val pickCsvLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> processFile(uri) }
        }
    }

    private val saveCsvLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> saveConvertedFile(uri) }
        }
    }

    private var pendingGarminCsv: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ExerciseMapper.init(this)

        binding.btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/octet-stream"))
            }
            pickCsvLauncher.launch(intent)
        }

        binding.btnExport.setOnClickListener {
            if (pendingGarminCsv != null) {
                promptSaveFile()
            } else {
                Toast.makeText(this, getString(R.string.no_data), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnExport.isEnabled = false
    }

    private fun processFile(uri: Uri) {
        binding.tvStatus.text = getString(R.string.processing)
        binding.btnExport.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open file")

                val rows = HevyCsvParser.parse(inputStream)
                if (rows.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.tvStatus.text = getString(R.string.error_empty_csv)
                    }
                    return@launch
                }

                val activities = HevyCsvParser.groupIntoActivities(rows)
                val garminActivities = GarminCsvExporter.convertAll(activities)

                val outputStream = java.io.ByteArrayOutputStream()
                GarminCsvExporter.writeCsv(garminActivities, outputStream)
                pendingGarminCsv = outputStream.toString(Charsets.UTF_8.name())

                val totalActivities = garminActivities.size
                val totalExercises = garminActivities.sumOf { it.exercises.size }
                val totalSets = garminActivities.sumOf { act -> act.exercises.sumOf { it.sets.size } }

                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = getString(
                        R.string.conversion_success,
                        totalActivities,
                        totalExercises,
                        totalSets
                    )
                    binding.btnExport.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = getString(R.string.error_processing, e.message ?: "Unknown error")
                }
            }
        }
    }

    private fun promptSaveFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "hevy_garmin_export.csv")
        }
        saveCsvLauncher.launch(intent)
    }

    private fun saveConvertedFile(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(pendingGarminCsv!!.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, getString(R.string.file_saved), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_saving, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }
}
