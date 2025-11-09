package com.example.vehicledatarecorder

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class VehicleAdapter(
    private val vehicles: List<Vehicle>,
    private val onItemClick: (Vehicle) -> Unit
) : RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder>() {

    class VehicleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ownerNameTextView: TextView = itemView.findViewById(R.id.textViewOwnerName)
        val warningsTextView: TextView = itemView.findViewById(R.id.textViewWarnings)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VehicleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_vehicle, parent, false)
        return VehicleViewHolder(view)
    }

    override fun onBindViewHolder(holder: VehicleViewHolder, position: Int) {
        val vehicle = vehicles[position]
        holder.ownerNameTextView.text = vehicle.ownerName
        val warnings = mutableListOf<String>()
        if (vehicle.chassisNumber.isBlank()) warnings.add("Chassis number not provided")
        if (vehicle.engineNumber.isBlank()) warnings.add("Engine number not provided")
        if (warnings.isEmpty()) {
            holder.warningsTextView.visibility = View.GONE
            holder.warningsTextView.text = ""
        } else {
            holder.warningsTextView.visibility = View.VISIBLE
            holder.warningsTextView.text = warnings.joinToString(separator = ", ")
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(vehicle)
        }
    }

    override fun getItemCount(): Int = vehicles.size
}


