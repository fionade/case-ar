package de.lmu.arcasegrammar

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.lmu.arcasegrammar.sentencebuilder.Sentence

class HistoryAdapter(private var sentenceList: List<Sentence>) : RecyclerView.Adapter<HistoryAdapter.MyViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return MyViewHolder(inflater, parent)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        holder.quizTitle.text = sentenceList[position].stringifyWithPlaceholder()
    }

    override fun getItemCount(): Int {
        return sentenceList.size
    }

    fun setData(sentenceList: List<Sentence>) {
        this.sentenceList = sentenceList
    }


    inner class MyViewHolder(inflater: LayoutInflater, parent: ViewGroup) :
        RecyclerView.ViewHolder(inflater.inflate(R.layout.history_list_item, parent, false)) {

        var quizTitle: TextView = itemView.findViewById(R.id.history_quiz_title)
    }
}