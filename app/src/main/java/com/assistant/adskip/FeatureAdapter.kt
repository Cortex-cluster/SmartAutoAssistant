package com.assistant.adskip

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

class FeatureAdapter(
    private val features: List<FeatureItem>,
    private val onToggleChanged: (FeatureItem, Boolean) -> Unit,
    private val onInfoClicked: (FeatureItem) -> Unit
) : RecyclerView.Adapter<FeatureAdapter.FeatureViewHolder>() {

    inner class FeatureViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvCategory: TextView = itemView.findViewById(R.id.tvFeatureCategory)
        val tvTitle: TextView = itemView.findViewById(R.id.tvFeatureTitle)
        val tvSummary: TextView = itemView.findViewById(R.id.tvFeatureSummary)
        val switchFeature: MaterialSwitch = itemView.findViewById(R.id.switchFeature)
        val btnInfo: ImageView = itemView.findViewById(R.id.btnFeatureInfo)

        fun bind(item: FeatureItem) {
            tvCategory.text = item.category
            tvTitle.text = item.title
            tvSummary.text = item.summary

            val isCurrentlyEnabled = FeatureManager.isEnabled(itemView.context, item.id, item.defaultEnabled)
            switchFeature.setOnCheckedChangeListener(null)
            switchFeature.isChecked = isCurrentlyEnabled

            switchFeature.setOnCheckedChangeListener { _, isChecked ->
                FeatureManager.setEnabled(itemView.context, item.id, isChecked)
                onToggleChanged(item, isChecked)
            }

            btnInfo.setOnClickListener {
                onInfoClicked(item)
            }

            itemView.setOnClickListener {
                onInfoClicked(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeatureViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_feature, parent, false)
        return FeatureViewHolder(view)
    }

    override fun onBindViewHolder(holder: FeatureViewHolder, position: Int) {
        holder.bind(features[position])
    }

    override fun getItemCount(): Int = features.size
}
