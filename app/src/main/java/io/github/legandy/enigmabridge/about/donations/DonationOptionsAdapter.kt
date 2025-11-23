package io.github.legandy.enigmabridge.about.donations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.legandy.enigmabridge.databinding.ListItemDonationOptionBinding

class DonationOptionsAdapter(
    private val options: List<DonationOption>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<DonationOptionsAdapter.DonationOptionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DonationOptionViewHolder {
        val binding = ListItemDonationOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return DonationOptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DonationOptionViewHolder, position: Int) {
        val option = options[position]
        holder.bind(option)
    }

    override fun getItemCount(): Int = options.size

    inner class DonationOptionViewHolder(private val binding: ListItemDonationOptionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(option: DonationOption) {
            binding.optionTitle.text = binding.root.context.getString(option.titleResId)
            binding.optionDescription.text = binding.root.context.getString(option.descriptionResId)
            binding.root.setOnClickListener { onItemClick(option.url) }
        }
    }
}
