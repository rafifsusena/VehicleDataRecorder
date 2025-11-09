package com.example.vehicledatarecorder

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    
    @Query("SELECT * FROM vehicles ORDER BY createdAt DESC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>
    
    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getVehicleById(id: Long): VehicleEntity?
    
    @Query("SELECT * FROM vehicles WHERE ownerName LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchVehicles(query: String): Flow<List<VehicleEntity>>
    
    @Insert
    suspend fun insertVehicle(vehicle: VehicleEntity): Long
    
    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)
    
    @Delete
    suspend fun deleteVehicle(vehicle: VehicleEntity)
    
    @Query("DELETE FROM vehicles WHERE id = :id")
    suspend fun deleteVehicleById(id: Long)

    // --- FUNGSI BARU DITAMBAHKAN ---
    @Query("SELECT * FROM vehicles WHERE chassisNumber = :searchText OR engineNumber = :searchText LIMIT 1")
    suspend fun findByChassisOrEngine(searchText: String): VehicleEntity?
}


