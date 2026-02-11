package com.hevy2garmin.converter

import com.hevy2garmin.model.HevyActivity
import com.hevy2garmin.model.HevyExercise
import com.hevy2garmin.model.HevySet
import com.hevy2garmin.model.HevyWorkoutRow
import com.opencsv.CSVReaderBuilder
import java.io.InputStream
import java.io.InputStreamReader

object HevyCsvParser {

    fun parse(inputStream: InputStream): List<HevyWorkoutRow> {
        val reader = CSVReaderBuilder(InputStreamReader(inputStream, Charsets.UTF_8)).build()
        val allRows = reader.readAll()
        reader.close()

        if (allRows.isEmpty()) return emptyList()

        val headers = allRows[0].map { it.trim().removeSurrounding("\"").lowercase() }
        val dataRows = allRows.drop(1)

        return dataRows.mapNotNull { cols ->
            if (cols.size < headers.size) return@mapNotNull null
            val map = headers.zip(cols.map { it.trim() }).toMap()
            try {
                HevyWorkoutRow(
                    title = map["title"].orEmpty(),
                    startTime = map["start_time"].orEmpty(),
                    endTime = map["end_time"].orEmpty(),
                    description = map["description"].orEmpty(),
                    exerciseTitle = map["exercise_title"].orEmpty(),
                    supersetId = map["superset_id"]?.takeIf { it.isNotBlank() },
                    exerciseNotes = map["exercise_notes"].orEmpty(),
                    setIndex = map["set_index"]?.toIntOrNull() ?: 0,
                    setType = map["set_type"].orEmpty(),
                    weightKg = map["weight_kg"]?.toDoubleOrNull(),
                    reps = map["reps"]?.toDoubleOrNull()?.toInt(),
                    distanceKm = map["distance_km"]?.toDoubleOrNull(),
                    durationSeconds = map["duration_seconds"]?.toDoubleOrNull(),
                    rpe = map["rpe"]?.toDoubleOrNull()
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    fun groupIntoActivities(rows: List<HevyWorkoutRow>): List<HevyActivity> {
        return rows
            .groupBy { Triple(it.title, it.startTime, it.endTime) }
            .map { (key, activityRows) ->
                val exercises = activityRows
                    .groupBy { it.exerciseTitle }
                    .map { (exerciseTitle, exerciseRows) ->
                        val sorted = exerciseRows.sortedBy { it.setIndex }
                        HevyExercise(
                            title = exerciseTitle,
                            notes = sorted.firstOrNull()?.exerciseNotes.orEmpty(),
                            supersetId = sorted.firstOrNull()?.supersetId,
                            sets = sorted.map { row ->
                                HevySet(
                                    setIndex = row.setIndex,
                                    setType = row.setType,
                                    weightKg = row.weightKg,
                                    reps = row.reps,
                                    distanceKm = row.distanceKm,
                                    durationSeconds = row.durationSeconds,
                                    rpe = row.rpe
                                )
                            }
                        )
                    }
                HevyActivity(
                    title = key.first,
                    startTime = key.second,
                    endTime = key.third,
                    description = activityRows.firstOrNull()?.description.orEmpty(),
                    exercises = exercises
                )
            }
            .sortedByDescending { it.endTime }
    }
}
