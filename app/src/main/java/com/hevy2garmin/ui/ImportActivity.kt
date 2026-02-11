package com.hevy2garmin.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.hevy2garmin.R
import com.hevy2garmin.converter.ExerciseMapper
import com.hevy2garmin.converter.GarminCsvExporter
import com.hevy2garmin.converter.HevyCsvParser
import com.hevy2garmin.databinding.ActivityImportBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Handles CSV files shared from Hevy (or any other app) via ACTION_SEND or ACTION_VIEW.
 */
class ImportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImportBinding
    private var pendingGarminCsv: String? = null

    private val saveCsvLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> saveConvertedFile(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ExerciseMapper.init(this)

        binding.btnExportGarmin.setOnClickListener {
            if (pendingGarminCsv != null) {
                promptSaveFile()
            }
        }

        binding.btnExportGarmin.isEnabled = false

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }

        if (uri != null) {
            processFile(uri)
        } else {
            binding.tvImportStatus.text = getString(R.string.error_no_file)
        }
    }

    private fun processFile(uri: Uri) {
        binding.tvImportStatus.text = getString(R.string.processing)
        binding.btnExportGarmin.isEnabled = false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Cannot open file")

                val rows = HevyCsvParser.parse(inputStream)
                if (rows.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        binding.tvImportStatus.text = getString(R.string.error_empty_csv)
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
                    binding.tvImportStatus.text = getString(
                        R.string.import_success,
                        totalActivities,
                        totalExercises,
                        totalSets
                    )
                    binding.btnExportGarmin.isEnabled = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvImportStatus.text = getString(R.string.error_processing, e.message ?: "Unknown error")
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
            binding.tvImportStatus.text = getString(R.string.export_complete)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.error_saving, e.message ?: ""), Toast.LENGTH_LONG).show()
        }
    }
}
