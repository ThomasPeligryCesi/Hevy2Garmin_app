package com.hevy2garmin.converter

import android.content.Context
import com.hevy2garmin.model.GarminExerciseMapping
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

object ExerciseMapper {

    private var lookupTable: List<LookupEntry> = emptyList()
    private val keywordMappings = buildKeywordMappings()

    data class LookupEntry(
        val hevyTitle: String?,
        val garmin: GarminExerciseMapping
    )

    fun init(context: Context) {
        if (lookupTable.isNotEmpty()) return
        try {
            val inputStream = context.assets.open("exercise-lookup-table.json")
            val json = BufferedReader(InputStreamReader(inputStream)).readText()
            val array = JSONArray(json)
            val entries = mutableListOf<LookupEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val garminObj = obj.getJSONObject("garmin")
                val hevyObj = obj.optJSONObject("hevy")
                entries.add(
                    LookupEntry(
                        hevyTitle = hevyObj?.optString("exerciseTitle")?.takeIf { it.isNotBlank() && it != "null" },
                        garmin = GarminExerciseMapping(
                            categoryId = garminObj.getInt("categoryId"),
                            categoryName = garminObj.optString("categoryName", ""),
                            exerciseId = garminObj.getInt("exerciseId"),
                            exerciseName = garminObj.optString("exerciseName", "")
                        )
                    )
                )
            }
            lookupTable = entries
        } catch (e: Exception) {
            lookupTable = emptyList()
        }
    }

    fun findMapping(hevyExerciseName: String): GarminExerciseMapping {
        // Stage 1: Exact match in lookup table
        lookupTable.firstOrNull {
            it.hevyTitle.equals(hevyExerciseName, ignoreCase = true)
        }?.let { return it.garmin }

        // Stage 2: Fuzzy match in lookup table (threshold 0.7)
        val normalized = normalize(hevyExerciseName)
        var bestScore = 0.0
        var bestMapping: GarminExerciseMapping? = null

        for (entry in lookupTable) {
            val entryName = entry.hevyTitle ?: entry.garmin.exerciseName
            val score = similarity(normalized, normalize(entryName))
            if (score > bestScore) {
                bestScore = score
                bestMapping = entry.garmin
            }
        }
        if (bestScore > 0.7 && bestMapping != null) return bestMapping

        // Stage 3: Keyword-based matching (threshold 0.6)
        val lowerName = hevyExerciseName.lowercase(Locale.ROOT)
        var bestKeywordScore = 0.0
        var bestKeywordMapping: GarminExerciseMapping? = null
        for (mapping in keywordMappings) {
            val matchCount = mapping.keywords.count { lowerName.contains(it) }
            if (matchCount > 0) {
                val score = matchCount.toDouble() / mapping.keywords.size
                if (score > bestKeywordScore) {
                    bestKeywordScore = score
                    bestKeywordMapping = mapping.garmin
                }
            }
        }
        if (bestKeywordScore > 0.6 && bestKeywordMapping != null) return bestKeywordMapping

        // Stage 4: Fuzzy match against all Garmin names (threshold 0.5)
        if (bestScore > 0.5 && bestMapping != null) return bestMapping

        // Default fallback: Total Body
        return GarminExerciseMapping(
            categoryId = 29,
            categoryName = "Total Body",
            exerciseId = 0,
            exerciseName = hevyExerciseName
        )
    }

    private fun normalize(s: String): String {
        return s.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return 0.8
        val wordsA = a.split(" ").toSet()
        val wordsB = b.split(" ").toSet()
        val common = wordsA.intersect(wordsB).size
        val maxLen = maxOf(wordsA.size, wordsB.size)
        if (maxLen > 0 && common > 0) {
            return 0.5 + (common.toDouble() / maxLen) * 0.3
        }
        val charsA = a.toSet()
        val charsB = b.toSet()
        val commonChars = charsA.intersect(charsB).size
        val maxChars = maxOf(charsA.size, charsB.size)
        return if (maxChars > 0) (commonChars.toDouble() / maxChars) * 0.5 else 0.0
    }

    data class KeywordMapping(
        val keywords: List<String>,
        val garmin: GarminExerciseMapping
    )

    private fun buildKeywordMappings(): List<KeywordMapping> = listOf(
        KeywordMapping(listOf("bench", "press", "barbell"), GarminExerciseMapping(0, "Bench Press", 0, "Barbell Bench Press")),
        KeywordMapping(listOf("bench", "press", "dumbbell"), GarminExerciseMapping(0, "Bench Press", 1, "Dumbbell Bench Press")),
        KeywordMapping(listOf("incline", "bench", "press"), GarminExerciseMapping(0, "Bench Press", 2, "Incline Barbell Bench Press")),
        KeywordMapping(listOf("squat", "barbell"), GarminExerciseMapping(6, "Squat", 0, "Barbell Back Squat")),
        KeywordMapping(listOf("front", "squat"), GarminExerciseMapping(6, "Squat", 1, "Front Squat")),
        KeywordMapping(listOf("goblet", "squat"), GarminExerciseMapping(6, "Squat", 2, "Goblet Squat")),
        KeywordMapping(listOf("deadlift", "barbell"), GarminExerciseMapping(4, "Deadlift", 0, "Barbell Deadlift")),
        KeywordMapping(listOf("deadlift", "romanian"), GarminExerciseMapping(4, "Deadlift", 1, "Romanian Deadlift")),
        KeywordMapping(listOf("deadlift", "sumo"), GarminExerciseMapping(4, "Deadlift", 2, "Sumo Deadlift")),
        KeywordMapping(listOf("pull", "up"), GarminExerciseMapping(17, "Pull Up", 0, "Pull Up")),
        KeywordMapping(listOf("chin", "up"), GarminExerciseMapping(17, "Pull Up", 1, "Chin Up")),
        KeywordMapping(listOf("lat", "pulldown"), GarminExerciseMapping(17, "Pull Up", 2, "Lat Pulldown")),
        KeywordMapping(listOf("barbell", "row"), GarminExerciseMapping(15, "Row", 0, "Barbell Row")),
        KeywordMapping(listOf("dumbbell", "row"), GarminExerciseMapping(15, "Row", 1, "Dumbbell Row")),
        KeywordMapping(listOf("cable", "row"), GarminExerciseMapping(15, "Row", 2, "Cable Row")),
        KeywordMapping(listOf("seated", "row"), GarminExerciseMapping(15, "Row", 3, "Seated Cable Row")),
        KeywordMapping(listOf("overhead", "press"), GarminExerciseMapping(21, "Shoulder Press", 0, "Overhead Press")),
        KeywordMapping(listOf("shoulder", "press"), GarminExerciseMapping(21, "Shoulder Press", 0, "Shoulder Press")),
        KeywordMapping(listOf("military", "press"), GarminExerciseMapping(21, "Shoulder Press", 1, "Military Press")),
        KeywordMapping(listOf("lateral", "raise"), GarminExerciseMapping(21, "Shoulder Press", 2, "Lateral Raise")),
        KeywordMapping(listOf("bicep", "curl"), GarminExerciseMapping(1, "Curl", 0, "Bicep Curl")),
        KeywordMapping(listOf("hammer", "curl"), GarminExerciseMapping(1, "Curl", 1, "Hammer Curl")),
        KeywordMapping(listOf("tricep", "extension"), GarminExerciseMapping(23, "Triceps Extension", 0, "Triceps Extension")),
        KeywordMapping(listOf("tricep", "pushdown"), GarminExerciseMapping(23, "Triceps Extension", 1, "Triceps Pushdown")),
        KeywordMapping(listOf("leg", "press"), GarminExerciseMapping(10, "Leg Press", 0, "Leg Press")),
        KeywordMapping(listOf("leg", "curl"), GarminExerciseMapping(8, "Leg Curl", 0, "Leg Curl")),
        KeywordMapping(listOf("leg", "extension"), GarminExerciseMapping(9, "Leg Extension", 0, "Leg Extension")),
        KeywordMapping(listOf("calf", "raise"), GarminExerciseMapping(2, "Calf Raise", 0, "Calf Raise")),
        KeywordMapping(listOf("plank"), GarminExerciseMapping(3, "Core", 0, "Plank")),
        KeywordMapping(listOf("crunch"), GarminExerciseMapping(3, "Core", 1, "Crunch")),
        KeywordMapping(listOf("sit", "up"), GarminExerciseMapping(3, "Core", 2, "Sit Up")),
        KeywordMapping(listOf("hip", "thrust"), GarminExerciseMapping(7, "Hip", 0, "Hip Thrust")),
        KeywordMapping(listOf("lunge"), GarminExerciseMapping(11, "Lunge", 0, "Lunge")),
        KeywordMapping(listOf("dip"), GarminExerciseMapping(0, "Bench Press", 3, "Dip")),
        KeywordMapping(listOf("fly", "chest"), GarminExerciseMapping(5, "Flye", 0, "Chest Fly")),
        KeywordMapping(listOf("flye"), GarminExerciseMapping(5, "Flye", 0, "Chest Flye")),
        KeywordMapping(listOf("shrug"), GarminExerciseMapping(22, "Shrug", 0, "Shrug")),
        KeywordMapping(listOf("face", "pull"), GarminExerciseMapping(15, "Row", 4, "Face Pull")),
        KeywordMapping(listOf("push", "up"), GarminExerciseMapping(0, "Bench Press", 4, "Push Up"))
    )
}
