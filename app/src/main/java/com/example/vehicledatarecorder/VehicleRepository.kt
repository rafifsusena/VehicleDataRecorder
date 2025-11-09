package com.example.vehicledatarecorder

import android.content.Context
import kotlinx.coroutines.flow.Flow

class VehicleRepository(context: Context) {
    
    private val vehicleDao = AppDatabase.getDatabase(context).vehicleDao()
    
    fun getAllVehicles(): Flow<List<VehicleEntity>> = vehicleDao.getAllVehicles()
    
    suspend fun getVehicleById(id: Long): VehicleEntity? = vehicleDao.getVehicleById(id)
    
    fun searchVehicles(query: String): Flow<List<VehicleEntity>> = vehicleDao.searchVehicles(query)
    
    suspend fun insertVehicle(vehicle: VehicleEntity): Long = vehicleDao.insertVehicle(vehicle)
    
    suspend fun updateVehicle(vehicle: VehicleEntity) = vehicleDao.updateVehicle(vehicle)
    
    suspend fun deleteVehicle(vehicle: VehicleEntity) = vehicleDao.deleteVehicle(vehicle)
    
    suspend fun deleteVehicleById(id: Long) = vehicleDao.deleteVehicleById(id)

    suspend fun findByChassisOrEngine(searchText: String): VehicleEntity? {
        return vehicleDao.findByChassisOrEngine(searchText)
    }
}


