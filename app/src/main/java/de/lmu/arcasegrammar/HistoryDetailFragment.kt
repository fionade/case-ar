package de.lmu.arcasegrammar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import de.lmu.arcasegrammar.databinding.FragmentHistoryDetailBinding
import de.lmu.arcasegrammar.sentencebuilder.Sentence
import de.lmu.arcasegrammar.viewmodel.HistoryViewModel

class HistoryDetailFragment : Fragment() {

    // View binding
    private var _binding: FragmentHistoryDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHistoryDetailBinding.inflate(inflater, container, false)

        viewModel.quiz.observe(viewLifecycleOwner) {
            // set the sentence and show the quiz
            if (it is Sentence) {
                binding.detailFirstPart.text = it.firstPart
                binding.detailSolution.text = it.wordToChoose
                binding.detailSecondPart.text = it.secondPart

                binding.detailSolution.visibility = View.INVISIBLE
                binding.showAnswerButton.visibility = View.VISIBLE
            }

            // TODO: else adapt the display
        }

        binding.showAnswerButton.setOnClickListener {
            if (binding.detailSolution.visibility == View.INVISIBLE) {
                binding.detailSolution.visibility = View.VISIBLE
                binding.showAnswerButton.visibility = View.INVISIBLE
            }
        }

        arguments?.let {
            val args = HistoryDetailFragmentArgs.fromBundle(it)
            viewModel.getQuiz(args.quizId, args.quizType)
        }

        return binding.root
    }


}