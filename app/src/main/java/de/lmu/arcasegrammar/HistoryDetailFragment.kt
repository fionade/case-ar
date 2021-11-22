package de.lmu.arcasegrammar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import de.lmu.arcasegrammar.databinding.FragmentHistoryDetailBinding
import de.lmu.arcasegrammar.viewmodel.HistoryViewModel

class HistoryDetailFragment : Fragment() {

    // View binding
    private var _binding: FragmentHistoryDetailBinding? = null
    private val binding get() = _binding!!

    private var sentenceId = 0L

    private val viewModel: HistoryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHistoryDetailBinding.inflate(inflater, container, false)

        viewModel.sentence.observe(viewLifecycleOwner, {
            // set the sentence and show the quiz
            if (it != null) {
                binding.detailFirstPart.text = it.firstPart
                binding.detailSolution.text = it.wordToChoose
                binding.detailSecondPart.text = it.secondPart
            }
        })

        arguments?.getLong("showSentence")?.let { it ->
            sentenceId = it

            if (sentenceId != -1L) {
                // display this sentence

                viewModel.getSentence(sentenceId)
            }
        }

        return binding.root
    }


}