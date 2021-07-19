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

import android.content.Context
import de.lmu.arcasegrammar.model.DetectedObject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import kotlin.math.abs
import kotlin.random.Random

class SentenceManager {

    companion object {
        const val NUMBER_OF_DISTRACTORS = 2
        val SOLUTION_OPTIONS = arrayOf("der", "die", "das", "dem", "den")
    }

    private val objects = mutableMapOf<String, Word>()
    // test: objects.isInitalized

    constructor(context: Context) {
        // loading data from assets
        // only do this once to avoid file system access

        val objectString: String
        try {
            objectString = context.assets.open("data/objects.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(objectString)
            val sentenceObject = jsonObject.getJSONArray("objects")
            for (i in 0 until sentenceObject.length()) {
                val word = Json.decodeFromString<Word>(sentenceObject[i].toString())
                this.objects[word.nominative.noun] = word
            }
        }
        catch (ioException: IOException) {
            ioException.printStackTrace()
        }
        catch (jsonException: JSONException) {
            jsonException.printStackTrace()
        }

    }

    fun constructSentence(firstItem: DetectedObject, secondItem: DetectedObject) : Sentence {

        // compute position of objects to get preposition
        val xDiff = firstItem.location.x - secondItem.location.x
        val xDescription = if (xDiff > 0) "rechts von" else "links von"

        val yDiff = firstItem.location.y - firstItem.location.y
        var yDescription = if (yDiff > 0) "vor" else "hinter"

        val position = if (abs(xDiff) > 0.8 * abs(yDiff)) xDescription else yDescription

        val firstWord = objects.getValue(firstItem.name)
        val secondWord = objects.getValue(secondItem.name)

        // randomly select an accusative or dative sentence
        return if (Random.nextFloat() > 0.5) {
            // return sentence
            createAccusativeSentence(firstWord, secondWord, position)
        } else {
            createDativeSentence(firstWord, secondWord, position)
        }

    }


    fun createAccusativeSentence(firstWord: Word, secondWord: Word, position: String) : Sentence {

        // Accusative sentence:
        // Personal pronoun or name + verb + accusative object 1 + preposition + accusative object 2
        val preposition = if (Random.nextFloat() >= 0.5) "neben" else position

        val verb = if (firstWord.verb == "liegt") "lege" else "stelle"
        val firstPart = "Ich %s %s %s %s".format(verb, firstWord.accusative.article, firstWord.accusative.noun, preposition)
        val wordToChoose = secondWord.accusative.article
        val secondPart = secondWord.accusative.noun

        return Sentence(firstPart, wordToChoose, secondPart, generateDistractors(wordToChoose, false))
    }

    fun createDativeSentence(firstWord: Word, secondWord: Word, position: String) : Sentence {

        // Dative sentence:
        // Nominative object 1 + verb + preposition + dative object 2
        val preposition = if (Random.nextFloat() >= 0.5) "neben" else position

        val firstPart = "%s %s %s %s".format(firstWord.nominative.article.capitalize(), firstWord.nominative.noun, firstWord.verb, preposition)
        val wordToChoose = secondWord.dative.article
        val secondPart = secondWord.dative.noun

        return if (firstPart.endsWith("von") && wordToChoose == "dem") {
            Sentence(firstPart.replace("von", ""), "vom", secondPart, generateDistractors("vom", true))
        } else {
            Sentence(firstPart, wordToChoose, secondPart, generateDistractors(wordToChoose, false))
        }
    }

    private fun generateDistractors(wordToChoose: String, contraction: Boolean) : ArrayList<String> {

        val distractors = ArrayList<String>(NUMBER_OF_DISTRACTORS + 1)
        distractors.add(wordToChoose)

        while (distractors.size < NUMBER_OF_DISTRACTORS + 1) {
            val index = Random.nextInt(SOLUTION_OPTIONS.size)
            val nextWord = if (!contraction) SOLUTION_OPTIONS[index] else "von ${SOLUTION_OPTIONS[index]}"
            if (!distractors.contains(nextWord)) {
                distractors.add(nextWord)
            }
        }

        distractors.shuffle()

        return distractors

    }
}