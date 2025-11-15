package com.jody.freshfood.ui.home.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.jody.freshfood.R
import com.jody.freshfood.data.local.entity.ScanResultEntity
import com.jody.freshfood.databinding.ItemScanHistoryBinding

class ScanHistoryAdapter : ListAdapter<ScanResultEntity, ScanHistoryAdapter.ScanHistoryViewHolder>(ScanDiff()) {

    private var onItemClick: ((ScanResultEntity) -> Unit)? = null

    fun setOnItemClickListener(listener: (ScanResultEntity) -> Unit) {
        onItemClick = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanHistoryViewHolder {
        val binding = ItemScanHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScanHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScanHistoryViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    fun getItemAt(position: Int): ScanResultEntity = getItem(position)

    fun removeItem(position: Int): ScanResultEntity {
        val mutable = currentList.toMutableList()
        val removed = mutable.removeAt(position)
        submitList(mutable)
        return removed
    }

    fun insertItem(position: Int, item: ScanResultEntity) {
        val mutable = currentList.toMutableList()
        val pos = if (position < 0 || position > mutable.size) mutable.size else position
        mutable.add(pos, item)
        submitList(mutable)
    }

    inner class ScanHistoryViewHolder(private val binding: ItemScanHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) onItemClick?.invoke(getItem(pos))
            }
        }

        fun bind(item: ScanResultEntity) {
            binding.textFruitType.text = item.fruitType.replaceFirstChar { it.uppercaseChar() }
            binding.textFreshnessLabel.text = item.freshnessLabel
            binding.textConfidence.text = String.format(binding.root.context.getString(R.string.home_confidence_format), (item.confidence * 100).toInt())
            binding.textScanDate.text = formatRelative(item.scanDate)

            // Badge background
            val label = item.freshnessLabel.lowercase()
            val bg = when {
                label.contains("fresh") -> R.drawable.bg_badge_fresh
                label.contains("ripe") -> R.drawable.bg_badge_ripe
                label.contains("overripe") -> R.drawable.bg_badge_overripe
                label.contains("spoil") || label.contains("bad") -> R.drawable.bg_badge_spoiled
                else -> R.drawable.bg_badge_fresh
            }
            binding.textFreshnessLabel.setBackgroundResource(bg)

            // Load image with Coil
            binding.imageFood.load(item.imagePath) {
                crossfade(true)
                placeholder(R.mipmap.ic_launcher)
                error(R.mipmap.ic_launcher)
            }
        }

        private fun formatRelative(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diffMs = now - timestamp
            val days = (diffMs / (1000 * 60 * 60 * 24)).toInt()
            val ctx = binding.root.context
            return when {
                days <= 0 -> ctx.getString(R.string.home_today)
                days == 1 -> ctx.getString(R.string.home_yesterday)
                else -> ctx.getString(R.string.home_days_ago_format, days)
            }
        }
    }

    class ScanDiff : DiffUtil.ItemCallback<ScanResultEntity>() {
        override fun areItemsTheSame(oldItem: ScanResultEntity, newItem: ScanResultEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ScanResultEntity, newItem: ScanResultEntity): Boolean {
            return oldItem == newItem
        }
    }
}
