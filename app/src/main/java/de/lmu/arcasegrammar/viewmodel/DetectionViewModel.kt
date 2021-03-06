package de.lmu.arcasegrammar.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import de.lmu.arcasegrammar.logging.FirebaseLogger
import de.lmu.arcasegrammar.model.DetectedObject
import de.lmu.arcasegrammar.model.HistoryDatabase
import de.lmu.arcasegrammar.model.QuizWrapper
import de.lmu.arcasegrammar.sentencebuilder.Sentence
import de.lmu.arcasegrammar.sentencebuilder.SentenceManager
import kotlinx.coroutines.launch

class DetectionViewModel(application: Application) : AndroidViewModel(application) {

    // Quiz setup
    private var sentenceManager: SentenceManager = SentenceManager(application)
    var quiz: MutableLiveData<QuizWrapper?> = MutableLiveData(null)

    val preparationList: MutableLiveData<ArrayList<DetectedObject>> = MutableLiveData(ArrayList())

    // history
    private val sentenceDao = HistoryDatabase.getDatabase(application).sentenceDao()

    // logging
    private val firebaseLogger = FirebaseLogger.getInstance()

    fun addObject(detectedObject: DetectedObject) {

        if (quiz.value != null) {
            reset()
        }

        preparationList.value?.add(detectedObject)
        // set the list as value so observers are notified
        preparationList.value = preparationList.value

        firebaseLogger.addLogMessage("label_tapped", "added object ${preparationList.value!!.size}: ${detectedObject.name}")
    }

    fun deleteObject(title: String) {
        preparationList.value = preparationList.value?.filter {
            it.name != title
        } as ArrayList

    }

    fun startQuiz() {

        if (preparationList.value != null) {
            when {
                preparationList.value!!.size == 1 -> {
                    // TODO: dynamically change question type here
                    quiz.value = sentenceManager.constructSingleSentence(preparationList.value!![0])

                }
                preparationList.value!!.size == 2 -> {
                    // construct sentence
                    quiz.value = sentenceManager.constructSentence(
                        preparationList.value!![0],
                        preparationList.value!![1]
                    )
                }
                preparationList.value!!.size > 2 -> {

                    preparationList.value?.shuffle()
                    quiz.value = sentenceManager.constructSentence(
                        preparationList.value!![0],
                        preparationList.value!![1]
                    )
                }
            }

            addToHistory()
        }
    }

    fun reset() {
        quiz.value = null
        preparationList.value = ArrayList()
    }

    private fun addToHistory() {
        if (quiz.value != null) {

            // TODO add other quiz types here
            viewModelScope.launch {
                if (quiz.value!! is Sentence) {
                    sentenceDao.insertSentence(quiz.value!! as Sentence)
                }
            }
        }
    }

}