package com.example.vehicledatarecorder

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class VehicleDetailActivity : AppCompatActivity() {
    
    private lateinit var vehicle: Vehicle
    private lateinit var textViewOwnerName: TextView
    private lateinit var textViewOwnerAddress: TextView
    private lateinit var textViewChassisNumber: TextView
    private lateinit var textViewEngineNumber: TextView
    private lateinit var textViewVehicleTestNumber: TextView
    private lateinit var textViewRegistrationNumber: TextView
    private lateinit var textViewVehicleBrand: TextView
    private lateinit var textViewVehicleType: TextView
    private lateinit var textViewVehicleCategory: TextView
    private lateinit var textViewManufactureYear: TextView
    private lateinit var textViewFuelType: TextView
    private lateinit var textViewCylinderCapacity: TextView
    private lateinit var textViewVehicleColor: TextView
    private lateinit var vehicleRepository: VehicleRepository
    
    private val editVehicleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val updatedVehicle = result.data?.getParcelableExtra<Vehicle>("vehicle")
            if (updatedVehicle != null) {
                // Save to database on background thread
                lifecycleScope.launch {
                    try {
                        vehicleRepository.updateVehicle(updatedVehicle.toEntity())
                        
                        // Update local vehicle and UI
                        vehicle = updatedVehicle
                        populateVehicleData()
                        
                        // Show success message
                        Snackbar.make(findViewById(android.R.id.content), "Vehicle updated successfully!", Snackbar.LENGTH_SHORT).show()
                        
                    } catch (e: Exception) {
                        // Show error message
                        Snackbar.make(findViewById(android.R.id.content), "Error updating vehicle: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_vehicle_detail)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        // Get vehicle from intent
        vehicle = intent.getParcelableExtra<Vehicle>("vehicle") ?: run {
            Toast.makeText(this, "Vehicle data not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        initializeViews()
        setupRepository()
        setupToolbar()
        populateVehicleData()
    }
    
    private fun initializeViews() {
        textViewOwnerName = findViewById(R.id.textViewOwnerName)
        textViewOwnerAddress = findViewById(R.id.textViewOwnerAddress)
        textViewChassisNumber = findViewById(R.id.textViewChassisNumber)
        textViewEngineNumber = findViewById(R.id.textViewEngineNumber)
        textViewVehicleTestNumber = findViewById(R.id.textViewVehicleTestNumber)
        textViewRegistrationNumber = findViewById(R.id.textViewRegistrationNumber)
        textViewVehicleBrand = findViewById(R.id.textViewVehicleBrand)
        textViewVehicleType = findViewById(R.id.textViewVehicleType)
        textViewVehicleCategory = findViewById(R.id.textViewVehicleCategory)
        textViewManufactureYear = findViewById(R.id.textViewManufactureYear)
        textViewFuelType = findViewById(R.id.textViewFuelType)
        textViewCylinderCapacity = findViewById(R.id.textViewCylinderCapacity)
        textViewVehicleColor = findViewById(R.id.textViewVehicleColor)
    }
    
    private fun setupRepository() {
        vehicleRepository = VehicleRepository(this)
    }
    
    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun populateVehicleData() {
        textViewOwnerName.text = vehicle.ownerName
        textViewOwnerAddress.text = vehicle.ownerAddress
        textViewChassisNumber.text = vehicle.chassisNumber
        textViewEngineNumber.text = vehicle.engineNumber
        textViewVehicleTestNumber.text = vehicle.vehicleTestNumber
        textViewRegistrationNumber.text = vehicle.registrationNumber
        textViewVehicleBrand.text = vehicle.vehicleBrand
        textViewVehicleType.text = vehicle.vehicleType
        textViewVehicleCategory.text = vehicle.vehicleCategory
        textViewManufactureYear.text = vehicle.manufactureYear.toString()
        textViewFuelType.text = vehicle.fuelType
        textViewCylinderCapacity.text = vehicle.cylinderCapacity
        textViewVehicleColor.text = vehicle.vehicleColor
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_vehicle_detail, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_edit -> {
                editVehicle()
                true
            }
            R.id.action_delete -> {
                deleteVehicle()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun editVehicle() {
        val intent = Intent(this, VehicleInputActivity::class.java)
        intent.putExtra("vehicle", vehicle)
        intent.putExtra("isEdit", true)
        editVehicleLauncher.launch(intent)
    }
    
    private fun deleteVehicle() {
        AlertDialog.Builder(this)
            .setTitle("Delete Vehicle")
            .setMessage("Are you sure you want to delete this vehicle? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                // Delete from database on background thread
                lifecycleScope.launch {
                    try {
                        vehicleRepository.deleteVehicle(vehicle.toEntity())
                        
                        // Show success message
                        Snackbar.make(findViewById(android.R.id.content), "Vehicle deleted successfully", Snackbar.LENGTH_SHORT).show()
                        
                        // Finish activity after a short delay to show the snackbar
                        lifecycleScope.launch {
                            kotlinx.coroutines.delay(1000)
                            finish()
                        }
                        
                    } catch (e: Exception) {
                        // Show error message
                        Snackbar.make(findViewById(android.R.id.content), "Error deleting vehicle: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
