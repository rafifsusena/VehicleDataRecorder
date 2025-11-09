package com.example.vehicledatarecorder

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Vehicle(
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
) : Parcelable {
    
    // Convert VehicleEntity to Vehicle
    companion object {
        fun fromEntity(entity: VehicleEntity): Vehicle {
            return Vehicle(
                id = entity.id,
                ownerName = entity.ownerName,
                chassisNumber = entity.chassisNumber,
                engineNumber = entity.engineNumber,
                ownerAddress = entity.ownerAddress,
                vehicleTestNumber = entity.vehicleTestNumber,
                registrationNumber = entity.registrationNumber,
                vehicleBrand = entity.vehicleBrand,
                vehicleType = entity.vehicleType,
                vehicleCategory = entity.vehicleCategory,
                manufactureYear = entity.manufactureYear,
                fuelType = entity.fuelType,
                cylinderCapacity = entity.cylinderCapacity,
                vehicleColor = entity.vehicleColor,
                createdAt = entity.createdAt
            )
        }
    }
    
    // Convert Vehicle to VehicleEntity
    fun toEntity(): VehicleEntity {
        return VehicleEntity(
            id = id,
            ownerName = ownerName,
            chassisNumber = chassisNumber,
            engineNumber = engineNumber,
            ownerAddress = ownerAddress,
            vehicleTestNumber = vehicleTestNumber,
            registrationNumber = registrationNumber,
            vehicleBrand = vehicleBrand,
            vehicleType = vehicleType,
            vehicleCategory = vehicleCategory,
            manufactureYear = manufactureYear,
            fuelType = fuelType,
            cylinderCapacity = cylinderCapacity,
            vehicleColor = vehicleColor,
            createdAt = createdAt
        )
    }
}
