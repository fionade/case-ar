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
import androidx.preference.PreferenceManager
import com.opencsv.CSVReader
import de.lmu.arcasegrammar.model.DetectedObject
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.random.Random

class SentenceManager(private val context: Context) {

    companion object {
        const val NUMBER_OF_DISTRACTORS = 2
        val SOLUTION_OPTIONS = arrayOf("der", "die", "das", "dem", "den")
    }

    private val objects = mutableMapOf<String, Word>()
    // test: objects.isInitalized

    private val json = Json { ignoreUnknownKeys = true }

    init {
        val objectString: String
        try {
            // loading data from assets
            // only do this once to avoid file system access
            objectString = context.assets.open("data/open_images_objects.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(objectString)
            val sentenceObject = jsonObject.getJSONArray("objects")
            for (i in 0 until sentenceObject.length()) {
                val word = json.decodeFromString<Word>(sentenceObject[i].toString())
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

    fun constructSingleSentence(item: DetectedObject): Sentence? {

        val priority = arrayListOf(UseOptions.USE_POSITION)
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        val useNovels = sharedPreferences.getBoolean("novels", true)
        if (useNovels) {
            priority.add(UseOptions.USE_NOVELS)
        }
        val useQuotes = sharedPreferences.getBoolean("quotes", true)
        if (useQuotes) {
            priority.add(UseOptions.USE_QUOTES)
        }

        priority.shuffle()

        for(option in priority) {
            when(option) {
                UseOptions.USE_NOVELS -> {
                    try {
                        // loading data from assets
                        val title = getAssetFileName(item.name)
                        val objectString = context.assets.open("data/literature/$title.txt").bufferedReader().use { it.readText() }
                        val lines = objectString.split("\n")

                        // choose random line
                        val sentence = lines[Random.nextInt(lines.size)]

                        return generateQuestion(sentence)
                    }
                    catch (ioException: IOException) {
                        ioException.printStackTrace()
                        continue
                    }
                }
                UseOptions.USE_QUOTES -> {
                    try {
                        // loading data from assets
                        val title = getAssetFileName(item.name)
                        val objectReader = context.assets.open("data/quotes/$title.csv").bufferedReader()

                        val reader = CSVReader(objectReader)
                        val entries = reader.readAll()

                        // choose random line
                        val quote = entries[Random.nextInt(entries.size)]

                        if (quote.size == 2) {
                            val sentence = generateQuestion(quote[0])
                            sentence?.let { it.attribution = "${quote[1]}\nin Wikiquote, Die freie Zitatsammlung." }
                            return sentence
                        }

                    }
                    catch (ioException: IOException) {
                        ioException.printStackTrace()
                        continue
                    }
                }
                else -> {
                    // fallback for objects without text resources
                    if(objects.containsKey(item.name) && objects.containsKey(item.name)) {
                        val word = objects.getValue(item.name)
                        val distractors = generateDistractors(word.nominative.article)
                        return Sentence("Das ist", word.nominative.article, word.nominative.noun, distractors, null)
                    }
                }
            }
        }

        return null
    }

    private fun generateQuestion(sentence: String): Sentence? {

        SOLUTION_OPTIONS.shuffle()

        for (article in SOLUTION_OPTIONS) {

            // does the sentence contain any of the articles?
            if (sentence.contains(" $article ") ||
                sentence.contains("${article.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.getDefault()
                    ) else it.toString()
                }} ")) { // articles within a word are not accepted.
                val index = sentence.indexOf(article)

                // ignore cases where an article is found within a word, e.g. "Bruder"
                if (index == 0 || !sentence[index-1].isLetter()) {
                    val distractors = generateDistractors(article)
                    return Sentence(sentence.substring(0, index), article, sentence.substring(index + article.length), distractors, null)
                }
            }
        }
        return null
    }

    fun constructSentence(firstItem: DetectedObject, secondItem: DetectedObject) : Sentence? {

        // compute position of objects to get preposition
        val xDiff = firstItem.location.x - secondItem.location.x
        val xDescription = if (xDiff > 0) "rechts von" else "links von"

        val yDiff = firstItem.location.y - firstItem.location.y
        val yDescription = if (yDiff > 0) "vor" else "hinter"

        val position = if (abs(xDiff) > 0.8 * abs(yDiff)) xDescription else yDescription

        if(objects.containsKey(firstItem.name) && objects.containsKey(firstItem.name)) {
            val firstWord = objects.getValue(firstItem.name)
            val secondWord = objects.getValue(secondItem.name)

            // randomly select an accusative or dative sentence
            return if (Random.nextFloat() > 0.5) {
                // return sentence
                createAccusativeSentence(firstWord, secondWord, position.replace("von", "neben"))
            } else {
                createDativeSentence(firstWord, secondWord, position)
            }
        }

        return null
    }


    private fun createAccusativeSentence(firstWord: Word, secondWord: Word, position: String) : Sentence {

        // Accusative sentence:
        // Personal pronoun or name + verb + accusative object 1 + preposition + accusative object 2
        val preposition = if (Random.nextFloat() >= 0.5) "neben" else position

        val verb = if (firstWord.verb == "liegt") "lege" else "stelle"
        val firstPart = "Ich %s %s %s %s".format(verb, firstWord.accusative.article, firstWord.accusative.noun, preposition)
        val wordToChoose = secondWord.accusative.article
        val secondPart = secondWord.accusative.noun

        return Sentence(firstPart, wordToChoose, secondPart, generateDistractors(wordToChoose), null)
    }

    private fun createDativeSentence(firstWord: Word, secondWord: Word, position: String) : Sentence {

        // Dative sentence:
        // Nominative object 1 + verb + preposition + dative object 2
        val preposition = if (Random.nextFloat() >= 0.5) "neben" else position

        val firstPart = "%s %s %s %s".format(firstWord.nominative.article.capitalize(), firstWord.nominative.noun, firstWord.verb, preposition)
        val wordToChoose = secondWord.dative.article
        val secondPart = secondWord.dative.noun

        var distractors = generateDistractors(wordToChoose)
        return if (firstPart.endsWith("von") && wordToChoose == "dem") {
            distractors = distractors.map {
                // add "von" to all distractor options
                if (it == "dem") "vom" else "von $it"
            } as ArrayList<String>
            // then remove "von" from the first part of the sentence
            Sentence(firstPart.replace("von", ""), "vom", secondPart, distractors, null)
        } else {
            Sentence(firstPart, wordToChoose, secondPart, distractors, null)
        }
    }

    private fun generateDistractors(wordToChoose: String) : ArrayList<String> {

        val distractors = ArrayList<String>(NUMBER_OF_DISTRACTORS + 1)
        distractors.add(wordToChoose)

        while (distractors.size < NUMBER_OF_DISTRACTORS + 1) {
            val index = Random.nextInt(SOLUTION_OPTIONS.size)
            val nextWord = SOLUTION_OPTIONS[index]
            if (!distractors.contains(nextWord)) {
                distractors.add(nextWord)
            }
        }

        distractors.shuffle()

        return distractors

    }

    private fun getAssetFileName(name: String): String {
        return name
            .lowercase()
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
    }

    private enum class UseOptions {
        USE_POSITION,
        USE_NOVELS,
        USE_QUOTES
    }
}