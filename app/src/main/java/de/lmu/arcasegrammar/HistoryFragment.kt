package de.lmu.arcasegrammar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import de.lmu.arcasegrammar.databinding.FragmentHistoryBinding
import de.lmu.arcasegrammar.model.HistoryDatabase
import de.lmu.arcasegrammar.sentencebuilder.Sentence
import de.lmu.arcasegrammar.sentencebuilder.SentenceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    // View binding
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    // Room database connection
    private lateinit var sentenceDao: SentenceDao

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)

        sentenceDao = HistoryDatabase.getDatabase(requireContext()).sentenceDao()

//        lifecycleScope.launch(Dispatchers.IO) {
//            sentenceDao.insertSentence(Sentence("Das Auto steht neben", "dem", "Haus", arrayListOf("der", "das", "die")))
//            sentenceDao.insertSentence(Sentence("Ich lege die Banane neben", "die", "Tasse", arrayListOf("das", "dem", "der")))
//
//        }

        lifecycleScope.launch(Dispatchers.IO) {
            val sentenceList = sentenceDao.getAllSentences()
            val historyAdapter = HistoryAdapter(sentenceList)

            lifecycleScope.launch(Dispatchers.Main) {
                binding.historyList.apply {
                    layoutManager = LinearLayoutManager(requireContext())
                    adapter = historyAdapter
                }
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}