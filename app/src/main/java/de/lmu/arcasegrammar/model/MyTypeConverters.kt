package de.lmu.arcasegrammar.model

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MyTypeConverters {

    @TypeConverter
    fun fromStringArrayList(list: ArrayList<String>): String {
        val gson = Gson()
        return gson.toJson(list)
    }

    @TypeConverter
    fun fromString(jsonString: String) : ArrayList<String> {
        val gson = Gson()
        return gson.fromJson(jsonString, object : TypeToken<ArrayList<String>>(){}.type)
    }
}