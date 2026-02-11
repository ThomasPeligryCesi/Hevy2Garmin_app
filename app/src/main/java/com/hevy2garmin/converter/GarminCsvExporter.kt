package com.hevy2garmin.converter

import com.hevy2garmin.model.GarminActivity
import com.hevy2garmin.model.GarminExerciseEntry
import com.hevy2garmin.model.GarminSet
import com.hevy2garmin.model.HevyActivity
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GarminCsvExporter {

    private const val DEFAULT_WEIGHT_KG = 70.0
    private const val DEFAULT_MET_VALUE = 5.0
    private const val DEFAULT_REST_TIME_SEC = 60
    private const val DEFAULT_EXERCISE_TIME_SEC = 60

    fun convertAll(activities: List<HevyActivity>): List<GarminActivity> {
        return activities.map { convert(it) }
    }

    fun convert(activity: HevyActivity): GarminActivity {
        val startDate = parseHevyTimestamp(activity.startTime)
        val endDate = parseHevyTimestamp(activity.endTime)
        val durationSec = if (startDate != null && endDate != null) {
            (endDate.time - startDate.time) / 1000
        } else 0L

        val calories = estimateCalories(DEFAULT_MET_VALUE, DEFAULT_WEIGHT_KG, durationSec)

        var currentTimestamp = startDate?.time?.div(1000) ?: 0L

        val exercises = activity.exercises.map { exercise ->
            val mapping = ExerciseMapper.findMapping(exercise.title)
            val garminSets = exercise.sets
                .filter { it.weightKg != null || it.reps != null || it.durationSeconds != null }
                .mapIndexed { index, set ->
                    val setTimestamp = currentTimestamp
                    currentTimestamp += DEFAULT_EXERCISE_TIME_SEC + DEFAULT_REST_TIME_SEC
                    GarminSet(
                        exerciseName = exercise.title,
                        categoryId = mapping.categoryId,
                        exerciseId = mapping.exerciseId,
                        setOrder = index + 1,
                        setType = if (set.setType == "normal" || set.setType == "warmup") "active" else "rest",
                        weightKg = set.weightKg,
                        reps = set.reps,
                        durationSeconds = set.durationSeconds,
                        distanceKm = set.distanceKm,
                        timestampSeconds = setTimestamp
                    )
                }
            GarminExerciseEntry(
                exerciseName = exercise.title,
                garminName = mapping.exerciseName,
                categoryId = mapping.categoryId,
                exerciseId = mapping.exerciseId,
                sets = garminSets
            )
        }

        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

        return GarminActivity(
            activityName = activity.title,
            sport = "strength_training",
            startTimeIso = startDate?.let { isoFormatter.format(it) } ?: activity.startTime,
            endTimeIso = endDate?.let { isoFormatter.format(it) } ?: activity.endTime,
            totalDurationSeconds = durationSec,
            estimatedCalories = calories,
            exercises = exercises
        )
    }

    fun writeCsv(activities: List<GarminActivity>, outputStream: OutputStream) {
        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
        // Garmin Connect CSV import format for strength training
        writer.write("Activity Name,Activity Type,Start Time,End Time,Duration (s),Calories,")
        writer.write("Exercise Name,Garmin Exercise Name,Category ID,Exercise ID,")
        writer.write("Set Order,Set Type,Weight (kg),Reps,Duration (s),Distance (km)\n")

        for (activity in activities) {
            for (exercise in activity.exercises) {
                for (set in exercise.sets) {
                    writer.write(escapeCsv(activity.activityName))
                    writer.write(",")
                    writer.write(escapeCsv(activity.sport))
                    writer.write(",")
                    writer.write(escapeCsv(activity.startTimeIso))
                    writer.write(",")
                    writer.write(escapeCsv(activity.endTimeIso))
                    writer.write(",")
                    writer.write(activity.totalDurationSeconds.toString())
                    writer.write(",")
                    writer.write(activity.estimatedCalories.toString())
                    writer.write(",")
                    writer.write(escapeCsv(set.exerciseName))
                    writer.write(",")
                    writer.write(escapeCsv(exercise.garminName))
                    writer.write(",")
                    writer.write(set.categoryId.toString())
                    writer.write(",")
                    writer.write(set.exerciseId.toString())
                    writer.write(",")
                    writer.write(set.setOrder.toString())
                    writer.write(",")
                    writer.write(set.setType)
                    writer.write(",")
                    writer.write(set.weightKg?.toString() ?: "")
                    writer.write(",")
                    writer.write(set.reps?.toString() ?: "")
                    writer.write(",")
                    writer.write(set.durationSeconds?.toString() ?: "")
                    writer.write(",")
                    writer.write(set.distanceKm?.toString() ?: "")
                    writer.write("\n")
                }
            }
        }
        writer.flush()
        writer.close()
    }

    fun writeSummaryCsv(activities: List<GarminActivity>, outputStream: OutputStream) {
        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
        writer.write("Activity Name,Activity Type,Start Time,Duration (s),Calories,Exercises,Total Sets\n")
        for (activity in activities) {
            val totalSets = activity.exercises.sumOf { it.sets.size }
            val exerciseNames = activity.exercises.joinToString("; ") { it.exerciseName }
            writer.write(escapeCsv(activity.activityName))
            writer.write(",")
            writer.write(activity.sport)
            writer.write(",")
            writer.write(escapeCsv(activity.startTimeIso))
            writer.write(",")
            writer.write(activity.totalDurationSeconds.toString())
            writer.write(",")
            writer.write(activity.estimatedCalories.toString())
            writer.write(",")
            writer.write(escapeCsv(exerciseNames))
            writer.write(",")
            writer.write(totalSets.toString())
            writer.write("\n")
        }
        writer.flush()
        writer.close()
    }

    private fun parseHevyTimestamp(timestamp: String): Date? {
        val formats = listOf(
            SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH),
            SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH),
            SimpleDateFormat("MMM d, yyyy HH:mm", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH)
        )
        for (fmt in formats) {
            try {
                return fmt.parse(timestamp.trim())
            } catch (_: Exception) {
                // Try next format
            }
        }
        return null
    }

    private fun estimateCalories(met: Double, weightKg: Double, durationSec: Long): Int {
        return (met * weightKg * (durationSec.toDouble() / 3600.0)).toInt()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
