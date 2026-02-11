package com.hevy2garmin.model

/**
 * Represents a single row (set) from the Hevy CSV export.
 */
data class HevyWorkoutRow(
    val title: String,
    val startTime: String,
    val endTime: String,
    val description: String,
    val exerciseTitle: String,
    val supersetId: String?,
    val exerciseNotes: String,
    val setIndex: Int,
    val setType: String,
    val weightKg: Double?,
    val reps: Int?,
    val distanceKm: Double?,
    val durationSeconds: Double?,
    val rpe: Double?
)

data class HevySet(
    val setIndex: Int,
    val setType: String,
    val weightKg: Double?,
    val reps: Int?,
    val distanceKm: Double?,
    val durationSeconds: Double?,
    val rpe: Double?
)

data class HevyExercise(
    val title: String,
    val notes: String,
    val supersetId: String?,
    val sets: List<HevySet>
)

data class HevyActivity(
    val title: String,
    val startTime: String,
    val endTime: String,
    val description: String,
    val exercises: List<HevyExercise>
)
