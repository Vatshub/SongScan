package com.example.analyzer

import com.example.data.model.CamelotInfo

object MusicTheory {
    data class KeyData(
        val standardKey: String,
        val camelotCode: String,
        val isMinor: Boolean,
        val relativeKey: String,
        val notes: List<String>,
        val commonProgressions: List<String>
    )

    private val KEY_MAP = listOf(
        // Minor Keys (Camelot A)
        KeyData("Ab Minor", "1A", true, "B Major (1B)", listOf("Ab", "Bb", "Cb", "Db", "Eb", "Fb", "Gb"), listOf("Abm - E - B - F#", "Abm - Dbm - Ebm - Abm")),
        KeyData("G# Minor", "1A", true, "B Major (1B)", listOf("G#", "A#", "B", "C#", "D#", "E", "F#"), listOf("G#m - E - B - F#", "G#m - C#m - D#m - G#m")),
        KeyData("Eb Minor", "2A", true, "F# Major (2B)", listOf("Eb", "F", "Gb", "Ab", "Bb", "Cb", "Db"), listOf("Ebm - B - F# - C#", "Ebm - Abm - Bbm - Ebm")),
        KeyData("D# Minor", "2A", true, "F# Major (2B)", listOf("D#", "E#", "F#", "G#", "A#", "B", "C#"), listOf("D#m - B - F# - C#", "D#m - G#m - A#m - D#m")),
        KeyData("Bb Minor", "3A", true, "Db Major (3B)", listOf("Bb", "C", "Db", "Eb", "F", "Gb", "Ab"), listOf("Bbm - Gb - Db - Ab", "Bbm - Ebm - Fm - Bbm")),
        KeyData("A# Minor", "3A", true, "C# Major (3B)", listOf("A#", "B#", "C#", "D#", "E#", "F#", "G#"), listOf("A#m - F# - C# - G#", "A#m - D#m - E#m - A#m")),
        KeyData("F Minor", "4A", true, "Ab Major (4B)", listOf("F", "G", "Ab", "Bb", "C", "Db", "Eb"), listOf("Fm - Db - Ab - Eb", "Fm - Bbm - C - Fm")),
        KeyData("C Minor", "5A", true, "Eb Major (5B)", listOf("C", "D", "Eb", "F", "G", "Ab", "Bb"), listOf("Cm - Ab - Eb - Bb", "Cm - Fm - G - Cm")),
        KeyData("G Minor", "6A", true, "Bb Major (6B)", listOf("G", "A", "Bb", "C", "D", "Eb", "F"), listOf("Gm - Eb - Bb - F", "Gm - Cm - D - Gm")),
        KeyData("D Minor", "7A", true, "F Major (7B)", listOf("D", "E", "F", "G", "A", "Bb", "C"), listOf("Dm - Bb - F - C", "Dm - Gm - A - Dm")),
        KeyData("A Minor", "8A", true, "C Major (8B)", listOf("A", "B", "C", "D", "E", "F", "G"), listOf("Am - F - C - G", "Am - Dm - E - Am")),
        KeyData("E Minor", "9A", true, "G Major (9B)", listOf("E", "F#", "G", "A", "B", "C", "D"), listOf("Em - C - G - D", "Em - Am - B - Em")),
        KeyData("B Minor", "10A", true, "D Major (10B)", listOf("B", "C#", "D", "E", "F#", "G", "A"), listOf("Bm - G - D - A", "Bm - Em - F# - Bm")),
        KeyData("F# Minor", "11A", true, "A Major (11B)", listOf("F#", "G#", "A", "B", "C#", "D", "E"), listOf("F#m - D - A - E", "F#m - Bm - C# - F#m")),
        KeyData("C# Minor", "12A", true, "E Major (12B)", listOf("C#", "D#", "E", "F#", "G#", "A", "B"), listOf("C#m - A - E - B", "C#m - F#m - G# - C#m")),

        // Major Keys (Camelot B)
        KeyData("B Major", "1B", false, "G# Minor (1A)", listOf("B", "C#", "D#", "E", "F#", "G#", "A#"), listOf("B - F# - G#m - E", "B - E - G#m - F#")),
        KeyData("F# Major", "2B", false, "D# Minor (2A)", listOf("F#", "G#", "A#", "B", "C#", "D#", "E#"), listOf("F# - C# - D#m - B", "F# - B - D#m - C#")),
        KeyData("Gb Major", "2B", false, "Eb Minor (2A)", listOf("Gb", "Ab", "Bb", "Cb", "Db", "Eb", "F"), listOf("Gb - Db - Ebm - Cb", "Gb - Cb - Ebm - Db")),
        KeyData("Db Major", "3B", false, "Bb Minor (3A)", listOf("Db", "Eb", "F", "Gb", "Ab", "Bb", "C"), listOf("Db - Ab - Bbm - Gb", "Db - Gb - Bbm - Ab")),
        KeyData("C# Major", "3B", false, "A# Minor (3A)", listOf("C#", "D#", "E#", "F#", "G#", "A#", "B#"), listOf("C# - G# - A#m - F#", "C# - F# - A#m - G#")),
        KeyData("Ab Major", "4B", false, "F Minor (4A)", listOf("Ab", "Bb", "C", "Db", "Eb", "F", "G"), listOf("Ab - Eb - Fm - Db", "Ab - Db - Fm - Eb")),
        KeyData("Eb Major", "5B", false, "C Minor (5A)", listOf("Eb", "F", "G", "Ab", "Bb", "C", "D"), listOf("Eb - Bb - Cm - Ab", "Eb - Ab - Cm - Bb")),
        KeyData("Bb Major", "6B", false, "G Minor (6A)", listOf("Bb", "C", "D", "Eb", "F", "G", "A"), listOf("Bb - F - Gm - Eb", "Bb - Eb - Gm - F")),
        KeyData("F Major", "7B", false, "D Minor (7A)", listOf("F", "G", "A", "Bb", "C", "D", "E"), listOf("F - C - Dm - Bb", "F - Bb - Dm - C")),
        KeyData("C Major", "8B", false, "A Minor (8A)", listOf("C", "D", "E", "F", "G", "A", "B"), listOf("C - G - Am - F", "C - F - Am - G")),
        KeyData("G Major", "9B", false, "E Minor (9A)", listOf("G", "A", "B", "C", "D", "E", "F#"), listOf("G - D - Em - C", "G - C - Em - D")),
        KeyData("D Major", "10B", false, "B Minor (10A)", listOf("D", "E", "F#", "G", "A", "B", "C#"), listOf("D - A - Bm - G", "D - G - Bm - A")),
        KeyData("A Major", "11B", false, "F# Minor (11A)", listOf("A", "B", "C#", "D", "E", "F#", "G#"), listOf("A - E - F#m - D", "A - D - F#m - E")),
        KeyData("E Major", "12B", false, "C# Minor (12A)", listOf("E", "F#", "G#", "A", "B", "C#", "D#"), listOf("E - B - C#m - A", "E - A - C#m - B"))
    )

    fun findKeyData(keyName: String): KeyData {
        val clean = keyName.trim().replace("min", "Minor", ignoreCase = true)
            .replace("maj", "Major", ignoreCase = true)
            .replace("m", " Minor")
            .trim()
        
        return KEY_MAP.firstOrNull { it.standardKey.equals(clean, ignoreCase = true) }
            ?: KEY_MAP.firstOrNull { clean.startsWith(it.standardKey, ignoreCase = true) }
            ?: KEY_MAP.firstOrNull { it.camelotCode.equals(clean, ignoreCase = true) }
            ?: KEY_MAP[13] // Fallback F# Minor (11A)
    }

    fun getCamelotInfo(keyName: String): CamelotInfo {
        val kd = findKeyData(keyName)
        val num = kd.camelotCode.filter { it.isDigit() }.toIntOrNull() ?: 8
        val letter = kd.camelotCode.lastOrNull() ?: 'B'

        val prevNum = if (num == 1) 12 else num - 1
        val nextNum = if (num == 12) 1 else num + 1
        val oppositeLetter = if (letter == 'A') 'B' else 'A'

        val sameKeyOpposite = "$num$oppositeLetter"
        val boost = "$nextNum$letter"
        val drop = "$prevNum$letter"

        val compatible = listOf(
            "$num$letter (Current Key)",
            "$sameKeyOpposite (Relative)",
            "$boost (Energy +1)",
            "$drop (Energy -1)"
        )

        return CamelotInfo(
            code = kd.camelotCode,
            standardKey = kd.standardKey,
            relativeKey = kd.relativeKey,
            compatibleEnergyBoost = boost,
            compatibleEnergyDrop = drop,
            compatibleKeys = compatible
        )
    }

    fun getChordProgressions(keyName: String): List<String> {
        return findKeyData(keyName).commonProgressions
    }

    fun getScaleNotes(keyName: String): List<String> {
        return findKeyData(keyName).notes
    }
}
