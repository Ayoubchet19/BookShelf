package fr.estiam.traillog.bookshelf.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import fr.estiam.traillog.R
import fr.estiam.traillog.bookshelf.model.ReadingSession
import fr.estiam.traillog.databinding.ItemSessionBinding
import java.text.DateFormat
import java.util.Date

/** Reading sessions of one book (date, pages, minutes) with edit and delete actions. */
class SessionAdapter(
    private val onEdit: (ReadingSession) -> Unit,
    private val onDelete: (ReadingSession) -> Unit
) : ListAdapter<ReadingSession, SessionAdapter.Holder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: ItemSessionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(session: ReadingSession) {
            val context = binding.root.context
            val date = formatSessionDate(session.startedAt)
            val details = context.getString(R.string.session_details, session.pagesRead, session.durationMinutes)
            binding.sessionDate.text = date
            binding.sessionDetails.text = details
            binding.root.contentDescription = "$date, $details"
            binding.editSessionButton.setOnClickListener { onEdit(session) }
            binding.deleteSessionButton.setOnClickListener { onDelete(session) }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ReadingSession>() {
            override fun areItemsTheSame(a: ReadingSession, b: ReadingSession) = a.id == b.id
            override fun areContentsTheSame(a: ReadingSession, b: ReadingSession) = a == b
        }

        fun formatSessionDate(startedAt: Long): String =
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(startedAt))
    }
}
