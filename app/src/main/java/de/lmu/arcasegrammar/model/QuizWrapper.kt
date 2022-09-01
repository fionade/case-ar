package de.lmu.arcasegrammar.model

import androidx.lifecycle.LiveData
import androidx.room.*
import de.lmu.arcasegrammar.sentencebuilder.Sentence

abstract class QuizWrapper(@PrimaryKey(autoGenerate = true) var id: Long = 0,
                           @ColumnInfo(name = "timestamp", defaultValue = "-1" ) var timestamp: Long = System.currentTimeMillis()) {

    abstract fun stringify(): String
    abstract fun stringifyWithPlaceholder(): String
    abstract fun getQuizType(): QuizType

    enum class QuizType {
        Sentence
    }

}