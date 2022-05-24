package de.lmu.arcasegrammar.viewhelpers

import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout

abstract class QuizLayout(context: Context) : ConstraintLayout(context, null) {

    abstract fun setQuiz()
    abstract fun resetQuiz()

    interface QuizLayoutResponder {
        fun reset()
    }
}