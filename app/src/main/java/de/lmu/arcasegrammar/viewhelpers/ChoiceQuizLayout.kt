package de.lmu.arcasegrammar.viewhelpers

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateInterpolator
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.chip.Chip
import de.lmu.arcasegrammar.R
import de.lmu.arcasegrammar.databinding.ChoiceQuizBinding
import de.lmu.arcasegrammar.logging.FirebaseLogger
import de.lmu.arcasegrammar.sentencebuilder.Sentence

class ChoiceQuizLayout(context: Context, private val sentence: Sentence, private val listener: QuizLayoutResponder) : QuizLayout(context) {

    // View binding
    private var _binding: ChoiceQuizBinding? = null
    private val binding get() = _binding!!

    // Quiz setup
    private var optionList: Array<Chip>

    // Logger
    private var firebaseLogger: FirebaseLogger = FirebaseLogger.getInstance()

    init {
        _binding = ChoiceQuizBinding.inflate(LayoutInflater.from(context), this)

        optionList = arrayOf(binding.option1, binding.option2, binding.option3)
        optionList.forEach { it ->
            it.setOnClickListener {onOptionSelected(it) }
        }

        setQuiz()
    }

    override fun setQuiz() {

        binding.part1.text = sentence.firstPart
        binding.part2.text = sentence.secondPart
        binding.attribution.text = sentence.attribution ?: ""

        binding.option1.text = sentence.distractors[0]
        binding.option2.text = sentence.distractors[1]
        binding.option3.text = sentence.distractors[2]

        firebaseLogger.addLogMessage("show_quiz", sentence.stringify())

        binding.root.visibility = View.VISIBLE
    }

    override fun resetQuiz() {
//        binding.root.visibility = View.GONE
//        bottomSheet.state = BottomSheetBehavior.STATE_HIDDEN

        // reset the radio group so no item is preselected on subsequent quizzes
        binding.options.clearCheck()

        binding.tickmark.alpha = 0f
        binding.incorrectmark.alpha = 0f

        optionList.forEach {
            it.setChipBackgroundColorResource(R.color.colorOptionBackground)
            it.setTextColor(
                AppCompatResources.getColorStateList(
                    context,
                    R.color.chip_states
                )
            )
            it.isCheckable = true
        }
    }

    private fun onOptionSelected(view: View) {
        val chip = view as Chip

        if(view.text == sentence.wordToChoose) {
            // correct solution found

            firebaseLogger.addLogMessage("answer_selected", "correct: ${view.text}")

            chip.setChipBackgroundColorResource(R.color.colorAnswerCorrect)

            optionList.forEach {
                it.isCheckable = false
            }

            binding.incorrectmark.animate().alpha(0f).setDuration(100)
                .setInterpolator(AccelerateInterpolator()).start()
            binding.tickmark.animate().alpha(1f).setDuration(800)
                .setInterpolator(AccelerateInterpolator()).start()

            binding.root.postDelayed({
                listener.reset()
            }, 3000) // hide quiz 3 seconds after a correct answer
        }
        else if (binding.tickmark.alpha < 0.1) {

            chip.setChipBackgroundColorResource(R.color.colorAnswerIncorrect)

            binding.incorrectmark.animate().alpha(1f).setDuration(800)
                .setInterpolator(AccelerateInterpolator()).start()

            // cannot be selected twice
            chip.isCheckable = false

            firebaseLogger.addLogMessage("answer_selected", "wrong: ${view.text}")
        }
    }
}