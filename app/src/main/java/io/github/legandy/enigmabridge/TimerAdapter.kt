package io.github.legandy.enigmabridge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.github.legandy.enigmabridge.databinding.ListItemTimerBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimerAdapter(
    private var timers: MutableList<Timer>
) : RecyclerView.Adapter<TimerAdapter.TimerViewHolder>() {

    interface OnTimerInteractionListener {
        fun onTimerEditClicked(timer: Timer)
        fun onTimerDeleteClicked(timer: Timer)
    }
    var listener: OnTimerInteractionListener? = null


    inner class TimerViewHolder(val binding: ListItemTimerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimerViewHolder {
        val binding = ListItemTimerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TimerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimerViewHolder, position: Int) {
        val timer = timers[position]
        val context = holder.itemView.context
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val startDate = dateFormat.format(Date(timer.beginTimestamp * 1000))
        val startTime = timeFormat.format(Date(timer.beginTimestamp * 1000))
        val endTime = timeFormat.format(Date(timer.endTimestamp * 1000))

        holder.binding.textTimerTitle.text = timer.name
        holder.binding.textTimerChannel.text = timer.sName
        holder.binding.textTimerDate.text = startDate
        holder.binding.textTimerTime.text = context.getString(R.string.timer_time_range, startTime, endTime)

        val statusColor = when (timer.state) {
            0 -> R.color.status_scheduled
            1 -> R.color.status_preparing
            2 -> R.color.status_recording
            3 -> R.color.status_finished
            4 -> R.color.status_error
            else -> R.color.status_unknown
        }
        holder.binding.statusIndicator.background.setTint(ContextCompat.getColor(context, statusColor))


        // Handle 3-dot menu clicks
        holder.binding.buttonMenu.setOnClickListener { view ->
            showPopupMenu(view, timer)
        }
    }

    override fun getItemCount(): Int = timers.size

    fun updateTimers(newTimers: List<Timer>) {
        this.timers.clear()
        this.timers.addAll(newTimers)
        this.timers.sortByDescending { it.beginTimestamp }
        notifyDataSetChanged()
    }

    fun removeItem(timer: Timer) {
        val position = timers.indexOf(timer)
        if (position > -1) {
            timers.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    private fun showPopupMenu(view: View, timer: Timer) {
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.timer_item_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit_timer -> {
                    listener?.onTimerEditClicked(timer)
                    true
                }
                R.id.action_delete_timer -> {
                    listener?.onTimerDeleteClicked(timer)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}

