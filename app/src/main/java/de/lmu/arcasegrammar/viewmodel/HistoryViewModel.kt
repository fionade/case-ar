package de.lmu.arcasegrammar.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import de.lmu.arcasegrammar.model.HistoryDatabase
import de.lmu.arcasegrammar.sentencebuilder.Sentence
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    // history
    private val sentenceDao = HistoryDatabase.getDatabase(application).sentenceDao()

    // current sentence
    var sentence: MutableLiveData<Sentence?> = MutableLiveData(null)

    fun getSentence(id: Long) {
        viewModelScope.launch {
            sentence.value = sentenceDao.getSentence(id)
        }
    }
}