package com.example.data.local

import androidx.room.TypeConverter
import com.example.data.model.SectionType
import com.example.data.model.SongSection
import org.json.JSONArray
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromSectionsList(sections: List<SongSection>?): String {
        if (sections == null) return "[]"
        val array = JSONArray()
        for (sec in sections) {
            val obj = JSONObject()
            obj.put("name", sec.name)
            obj.put("type", sec.type.name)
            obj.put("startMs", sec.startMs)
            obj.put("endMs", sec.endMs)
            obj.put("energy", sec.energy.toDouble())
            obj.put("description", sec.description)
            obj.put("chordProgression", sec.chordProgression)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toSectionsList(data: String?): List<SongSection> {
        if (data.isNullOrBlank()) return emptyList()
        val list = mutableListOf<SongSection>()
        try {
            val array = JSONArray(data)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val typeName = obj.optString("type", SectionType.VERSE.name)
                val type = try { SectionType.valueOf(typeName) } catch (_: Exception) { SectionType.VERSE }
                list.add(
                    SongSection(
                        name = obj.optString("name", "Section ${i + 1}"),
                        type = type,
                        startMs = obj.optLong("startMs", 0L),
                        endMs = obj.optLong("endMs", 0L),
                        energy = obj.optDouble("energy", 0.5).toFloat(),
                        description = obj.optString("description", ""),
                        chordProgression = obj.optString("chordProgression", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    @TypeConverter
    fun fromFloatList(floats: List<Float>?): String {
        if (floats == null) return ""
        return floats.joinToString(",")
    }

    @TypeConverter
    fun toFloatList(data: String?): List<Float> {
        if (data.isNullOrBlank()) return emptyList()
        return try {
            data.split(",").mapNotNull { it.trim().toFloatOrNull() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromStringList(strings: List<String>?): String {
        if (strings == null) return ""
        return strings.joinToString("|||")
    }

    @TypeConverter
    fun toStringList(data: String?): List<String> {
        if (data.isNullOrBlank()) return emptyList()
        return data.split("|||").filter { it.isNotBlank() }
    }
}
