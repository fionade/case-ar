package de.lmu.arcasegrammar

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import de.lmu.arcasegrammar.sentencebuilder.Case
import de.lmu.arcasegrammar.sentencebuilder.SentenceManager
import de.lmu.arcasegrammar.sentencebuilder.Word

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
//    @Test
//    fun useAppContext() {
//        // Context of the app under test.
//        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
//        assertEquals("de.lmu.arcasegrammar", appContext.packageName)
//    }
//
//    @Test
//    fun wordsLoaded() {
//        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
//        val sentenceManager = SentenceManager(appContext)
//
//        assertEquals(sentenceManager.objects.size, 80)
//    }
//
//    @Test
//    fun createSentences() {
//        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
//        val sentenceManager = SentenceManager(appContext)
//        val word1 = Word(Case("der", "Hund"), Case("dem", "Hund"), Case("den", "Hund"), "steht")
//        val word2 = Word(Case("die", "Katze"), Case("der", "Katze"), Case("die", "Katze"), "steht")
//
//        assertEquals(sentenceManager.createAccusativeSentence(word1, word2).stringify(), "Ich stelle den Hund neben die Katze")
//        assertEquals(sentenceManager.createDativeSentence(word1, word2).stringify(), "Der Hund steht neben der Katze")
//    }
//
//    @Test
//    fun getSentencesForSelectedObjects() {
//        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
//        val sentenceManager = SentenceManager(appContext)
//        assertEquals(sentenceManager.objects.getValue("Hund").nominative.noun, "Hund")
//    }
}
