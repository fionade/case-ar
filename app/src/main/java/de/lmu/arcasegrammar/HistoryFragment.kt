package de.lmu.arcasegrammar

import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import de.lmu.arcasegrammar.databinding.FragmentHistoryBinding
import de.lmu.arcasegrammar.model.QuizWrapper
import de.lmu.arcasegrammar.viewhelpers.HistoryAdapter
import de.lmu.arcasegrammar.viewmodel.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs


class HistoryFragment : Fragment(), HistoryAdapter.SentenceTouchListener {

    // View binding
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    // Room view model
    private val viewModel: HistoryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)

        val historyAdapter = HistoryAdapter(mutableListOf(), this)
        binding.historyList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = historyAdapter
        }


        // Loading initial data
        viewModel.getAllQuizzes()

        viewModel.quizList.observe(viewLifecycleOwner) {

            if (it.isNullOrEmpty()) {
                binding.emptyListWarning.visibility = View.VISIBLE
            } else {
                historyAdapter.setData(it.sortedWith(compareBy {item -> - item.timestamp }))
                historyAdapter.notifyDataSetChanged()
                binding.emptyListWarning.visibility = View.GONE
            }
        }

        val swipeToDeleteHandler = ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

                private val icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_baseline_delete_24)

                private val background = if (Build.VERSION.SDK_INT >= 23) {
                    ColorDrawable(resources.getColor(R.color.colorAccent, activity?.theme))
                } else {
                    ColorDrawable(resources.getColor(R.color.colorAccent))
                }

                override fun onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder): Boolean {
                    // do nothing
                    return false
                }

                override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
                    // remove from adapter
                    val position = viewHolder.adapterPosition
                    val sentenceToRemove = viewModel.quizList.value?.get(position)
                    if (sentenceToRemove != null) {
                        viewModel.deleteQuiz(sentenceToRemove)
                    }
                }

                // Colour background and bin icon shown while swiping
                override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: ViewHolder,
                    dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {

                    if (dX < 0) {
                        background.setBounds(viewHolder.itemView.right + dX.toInt(), viewHolder.itemView.top, viewHolder.itemView.right, viewHolder.itemView.bottom)
                        background.draw(c)

                        val iconMargin = (viewHolder.itemView.height - icon?.intrinsicHeight!!) / 2
                        if (abs(dX) > icon.intrinsicWidth + iconMargin) { // alternative: scale opacity
                            val iconTop = viewHolder.itemView.top + (viewHolder.itemView.height - icon.intrinsicHeight) / 2
                            val iconBottom = iconTop + icon.intrinsicHeight
                            val iconLeft: Int = viewHolder.itemView.right - iconMargin - icon.intrinsicWidth
                            val iconRight: Int = viewHolder.itemView.right - iconMargin
                            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            icon.draw(c)
                        }
                    }

                    super.onChildDraw(c, recyclerView,
                        viewHolder,
                        dX,
                        dY,
                        actionState,
                        isCurrentlyActive
                    )
                }
            })

        swipeToDeleteHandler.attachToRecyclerView(binding.historyList)

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onItemTouched(id: Long, quizType: QuizWrapper.QuizType) {
//        val bundle = bundleOf("quizId" to id, "quizType" to "Sentence")
        val action = HistoryFragmentDirections.actionNavigationHistoryToNavigationHistoryDetail()
        action.quizId = id
        action.quizType = quizType
        findNavController().navigate(action)
    }

}