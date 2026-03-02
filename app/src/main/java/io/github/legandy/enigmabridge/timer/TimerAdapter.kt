package io.github.legandy.enigmabridge.timer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.databinding.ListItemTimerBinding
import io.github.legandy.enigmabridge.receiversettings.Timer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimerAdapter(
    private var timers: MutableList<Timer>,
    private val listener: OnTimerActionsListener // Listener for communicating clicks
) : RecyclerView.Adapter<TimerAdapter.TimerViewHolder>() {

    /**
     * An interface for the Activity to listen to item actions.
     */
    interface OnTimerActionsListener {
        fun onEditClicked(timer: Timer)
        fun onDeleteClicked(timer: Timer)
    }

    class TimerViewHolder(val binding: ListItemTimerBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimerViewHolder {
        val binding = ListItemTimerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TimerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimerViewHolder, position: Int) {
        val timer = timers[position]
        val context = holder.itemView.context
        val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val startDate = dateTimeFormat.format(Date(timer.beginTimestamp * 1000))
        val endTime = timeFormat.format(Date(timer.endTimestamp * 1000))

        holder.binding.textTimerTitle.text = timer.name
        holder.binding.textTimerChannel.text = timer.sName
        holder.binding.textTimerTime.text = context.getString(R.string.timer_time_range_detailed, startDate, endTime)

        // Set status icon and color based on timer state
        val iconRes: Int
        val colorRes: Int
        when (timer.state) {
            0, 1 -> { // Scheduled or Preparing
                iconRes = R.drawable.ic_outline_timer_24
                colorRes = R.color.status_scheduled
            }
            2 -> { // Recording
                iconRes = R.drawable.ic_outline_videocam_24
                colorRes = R.color.status_recording
            }
            3 -> { // Finished
                iconRes = R.drawable.ic_outline_check_circle_24
                colorRes = R.color.status_finished
            }
            else -> { // Error or Unknown
                iconRes = R.drawable.ic_outline_error_24
                colorRes = R.color.status_error
            }
        }
        holder.binding.statusIcon.setImageResource(iconRes)
        holder.binding.statusIcon.setColorFilter(ContextCompat.getColor(context, colorRes))

        // Handle timer type (Zap/Switch)
        if (timer.justPlay == 2) {
            holder.binding.textTimerType.text = context.getString(R.string.label_switch)
            holder.binding.textTimerType.visibility = View.VISIBLE
        } else {
            holder.binding.textTimerType.visibility = View.GONE
        }

        // Set up the click listener for the 3-dot menu
        holder.binding.buttonMenu.setOnClickListener { view ->
            val popup = PopupMenu(context, view)
            popup.inflate(R.menu.timer_item_menu) // Use the menu resource you provided
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit_timer -> {
                        listener.onEditClicked(timer)
                        true
                    }
                    R.id.action_delete_timer -> {
                        listener.onDeleteClicked(timer)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = timers.size

    class TimerDiffCallback(
        private val oldList: List<Timer>,
        private val newList: List<Timer>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = oldList.size
        override fun getNewListSize() = newList.size
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            // Unique identifier for a timer on Enigma2
            return oldItem.sRef == newItem.sRef &&
                    oldItem.beginTimestamp == newItem.beginTimestamp
        }
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    fun updateTimers(newTimers: List<Timer>) {
        val sortedNewTimers = newTimers.sortedByDescending { it.beginTimestamp }
        val diffCallback = TimerDiffCallback(this.timers, sortedNewTimers)
        val diffResult = DiffUtil.calculateDiff(diffCallback)

        this.timers.clear()
        this.timers.addAll(sortedNewTimers)
        diffResult.dispatchUpdatesTo(this)

    }
}