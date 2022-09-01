package de.lmu.arcasegrammar.viewmodel

import android.app.Application
import androidx.lifecycle.*
import de.lmu.arcasegrammar.model.HistoryDatabase
import de.lmu.arcasegrammar.model.QuizWrapper
import de.lmu.arcasegrammar.sentencebuilder.Sentence
import de.lmu.arcasegrammar.sentencebuilder.SentenceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    // history
    private val sentenceDao = HistoryDatabase.getDatabase(application).sentenceDao()
    // TODO add other Daos here

    // current quiz
    var quiz: MutableLiveData<QuizWrapper> = MutableLiveData(null)
    var quizList: MutableLiveData<List<QuizWrapper>> = MutableLiveData(null)

    fun getQuiz(id: Long, quizType: QuizWrapper.QuizType) {

        when(quizType) {
            QuizWrapper.QuizType.Sentence -> {
                viewModelScope.launch(Dispatchers.IO) {
                    quiz.postValue(sentenceDao.getSentence(id))
                }
                // TODO add other quiz types here
            }
        }
    }

    fun getAllQuizzes() {
        // get quizzes for all types!!
        viewModelScope.launch(Dispatchers.IO) {
            quizList.postValue(sentenceDao.getAllSentences())
            // TODO append other quiz types
        }
    }

    fun deleteQuiz(quiz: QuizWrapper) {

        when(quiz.getQuizType()) {
            QuizWrapper.QuizType.Sentence -> {
                viewModelScope.launch {
                    sentenceDao.deleteSentence(quiz as Sentence)
                }
            }
            // TODO add other quiz types here
            else -> {}
        }
    }


}