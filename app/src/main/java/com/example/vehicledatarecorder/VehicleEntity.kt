package com.example.vehicledatarecorder

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val ownerName: String,
    val chassisNumber: String,
    val engineNumber: String,
    val ownerAddress: String,
    val vehicleTestNumber: String,
    val registrationNumber: String,
    val vehicleBrand: String,
    val vehicleType: String,
    val vehicleCategory: String,
    val manufactureYear: Int,
    val fuelType: String,
    val cylinderCapacity: String,
    val vehicleColor: String,
    val createdAt: Long = System.currentTimeMillis()
)
