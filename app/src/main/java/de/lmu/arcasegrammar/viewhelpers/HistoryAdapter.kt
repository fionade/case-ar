package de.lmu.arcasegrammar.viewhelpers

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.lmu.arcasegrammar.R
import de.lmu.arcasegrammar.model.QuizWrapper
import de.lmu.arcasegrammar.sentencebuilder.Sentence

class HistoryAdapter(private var sentenceList: List<QuizWrapper>, private val listener: SentenceTouchListener) : RecyclerView.Adapter<HistoryAdapter.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return MyViewHolder(inflater, parent)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.quizTitle.text = sentenceList[position].stringifyWithPlaceholder()

        holder.itemView.setOnClickListener {
            listener.onItemTouched(sentenceList[position].id, sentenceList[position].getQuizType())
        }
    }

    override fun getItemCount(): Int {
        return sentenceList.size
    }

    fun setData(sentenceList: List<QuizWrapper>) {
        this.sentenceList = sentenceList
    }


    inner class MyViewHolder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.history_list_item, parent, false)) {

        var quizTitle: TextView = itemView.findViewById(R.id.history_quiz_title)
    }

    interface SentenceTouchListener {
        fun onItemTouched(id: Long, quizType: QuizWrapper.QuizType)
    }
}