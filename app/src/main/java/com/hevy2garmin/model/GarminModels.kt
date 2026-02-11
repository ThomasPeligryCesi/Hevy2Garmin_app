package com.hevy2garmin.model

data class GarminExerciseMapping(
    val categoryId: Int,
    val categoryName: String,
    val exerciseId: Int,
    val exerciseName: String
)

data class GarminSet(
    val exerciseName: String,
    val categoryId: Int,
    val exerciseId: Int,
    val setOrder: Int,
    val setType: String,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Double?,
    val distanceKm: Double?,
    val timestampSeconds: Long
)

data class GarminActivity(
    val activityName: String,
    val sport: String,
    val startTimeIso: String,
    val endTimeIso: String,
    val totalDurationSeconds: Long,
    val estimatedCalories: Int,
    val exercises: List<GarminExerciseEntry>
)

data class GarminExerciseEntry(
    val exerciseName: String,
    val garminName: String,
    val categoryId: Int,
    val exerciseId: Int,
    val sets: List<GarminSet>
)
