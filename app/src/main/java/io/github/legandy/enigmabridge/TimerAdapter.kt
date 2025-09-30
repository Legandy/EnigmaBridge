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
        fun onTimerDeleteClicked(timer: Timer)
        fun onTimerEditClicked(timer: Timer)
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

        val beginDate = Date(timer.beginTimestamp * 1000)
        val endDate = Date(timer.endTimestamp * 1000)

        holder.binding.textTimerChannel.text = timer.sName
        holder.binding.textTimerTitle.text = timer.name
        holder.binding.textTimerTime.text = if (dateFormat.format(beginDate) == dateFormat.format(endDate)) {
            context.getString(R.string.timer_time_range_single_day, timeFormat.format(beginDate), timeFormat.format(endDate))
        } else {
            context.getString(R.string.timer_time_range_multi_day, dateFormat.format(beginDate), timeFormat.format(beginDate), dateFormat.format(endDate), timeFormat.format(endDate))
        }

        // Set status icon and background color
        val (iconRes, colorRes) = when (timer.state) {
            0 -> Pair(R.drawable.ic_timer, R.color.status_scheduled)  // Scheduled
            1 -> Pair(R.drawable.ic_recording, R.color.status_recording) // Preparing
            2 -> Pair(R.drawable.ic_recording, R.color.status_recording) // Recording
            3 -> Pair(R.drawable.ic_check_circle, R.color.status_finished)   // Finished
            4 -> Pair(R.drawable.ic_error, R.color.status_error)       // Error
            else -> Pair(R.drawable.ic_help, R.color.status_unknown)   // Unknown
        }
        holder.binding.statusIcon.setImageResource(iconRes)
        holder.binding.statusIcon.background.setTint(ContextCompat.getColor(context, colorRes))

        // Set Timer Type
        if (timer.justplay == 1) {
            holder.binding.statusIcon.setImageResource(R.drawable.ic_zap)
            holder.binding.textTimerType.text = context.getString(R.string.timer_type_zap)
        } else {
            holder.binding.textTimerType.text = context.getString(R.string.timer_type_record)
        }

        // Handle 3-dot menu clicks
        holder.binding.buttonMenu.setOnClickListener { view ->
            showPopupMenu(view, timer)
        }
    }

    override fun getItemCount(): Int = timers.size

    fun updateTimers(newTimers: List<Timer>) {
        this.timers.clear()
        this.timers.addAll(newTimers)
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
                R.id.action_delete_timer -> {
                    listener?.onTimerDeleteClicked(timer)
                    true
                }
                R.id.action_edit_timer -> {
                    listener?.onTimerEditClicked(timer)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}

