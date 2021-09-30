/*
 * Copyright 2021 Fiona Draxler, Audrey Labrie. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.lmu.arcasegrammar.sentencebuilder

import androidx.room.*

@Entity
data class Sentence(val firstPart: String,
                    val wordToChoose: String,
                    val secondPart: String,
                    val distractors: ArrayList<String>,
                    @PrimaryKey(autoGenerate = true) var id: Long = -1) {

    fun stringify(): String {
        return "%s %s %s.".format(firstPart, wordToChoose, secondPart)
    }

    fun stringifyWithPlaceholder(): String {
        return "%s ... %s.".format(firstPart, secondPart)
    }
}

@Dao
interface SentenceDao {

    @Query("SELECT * FROM sentence ORDER BY id DESC")
    suspend fun getAllSentences(): List<Sentence>

    @Insert
    suspend fun insertSentence(sentence: Sentence)

}