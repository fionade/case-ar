package de.lmu.arcasegrammar.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.lmu.arcasegrammar.FirebaseLogger
import de.lmu.arcasegrammar.model.DetectedObject
import de.lmu.arcasegrammar.model.HistoryDatabase
import de.lmu.arcasegrammar.sentencebuilder.Sentence
import de.lmu.arcasegrammar.sentencebuilder.SentenceManager
import kotlinx.coroutines.launch

class DetectionViewModel(application: Application) : AndroidViewModel(application) {

    // Quiz setup
    private var sentenceManager: SentenceManager = SentenceManager(application)
    var sentence: MutableLiveData<Sentence?> = MutableLiveData(null)

    private val preparationList: MutableLiveData<ArrayList<DetectedObject>> = MutableLiveData(ArrayList())

    // history
    private val sentenceDao = HistoryDatabase.getDatabase(application).sentenceDao()

    // logging
    private val firebaseLogger = FirebaseLogger.getInstance()

    fun addObject(detectedObject: DetectedObject) {
        preparationList.value?.add(detectedObject)

        if (preparationList.value != null) {
            when(preparationList.value!!.size) {
                1 -> {
                    firebaseLogger.addLogMessage("label_tapped", "added first object ${detectedObject.name}")
                }
                2 -> {
                    firebaseLogger.addLogMessage("label_tapped", "added second object ${detectedObject.name}")
                    // construct sentence
                    sentence.value = sentenceManager.constructSentence(
                        preparationList.value!![0],
                        preparationList.value!![1]
                    )

                    addToHistory()
                }
                3 -> {
                    firebaseLogger.addLogMessage("label_tapped", "reset and selected first ${detectedObject.name}")
                    sentence.value = null
                    preparationList.value!!.removeAt(1)
                    preparationList.value!!.removeAt(0)
                }
            }
        }
    }

    fun deleteObject(title: String) {
        preparationList.value = preparationList.value?.filter {
            it.name != title
        } as ArrayList

        if (preparationList.value == null || (preparationList.value != null && preparationList.value!!.size < 2)) {
            sentence.value = null
        }
    }

    private fun addToHistory() {
        if (sentence.value != null) {
            viewModelScope.launch {
                sentenceDao.insertSentence(sentence.value!!)
            }
        }
    }

}