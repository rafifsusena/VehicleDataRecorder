package com.example.vehicledatarecorder

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
// Hapus import PopupMenu jika tidak digunakan
// import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
// --- IMPORT BARU UNTUK CRASH HANDLER ---
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess
// ---------------------------------------
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

// Import baru untuk pindah ke Main thread
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerViewVehicles: RecyclerView
    private lateinit var editTextSearch: TextInputEditText
    private lateinit var textViewEmptyState: TextView
    private lateinit var fabAddVehicle: FloatingActionButton // ID ini dipertahankan

    private lateinit var vehicleAdapter: VehicleAdapter
    private lateinit var vehicleRepository: VehicleRepository
    private val vehicleList = mutableListOf<Vehicle>()

    // --- PERUBAHAN 1: Modifikasi launcher untuk MENANGANI HASIL ---
    private val vehicleInputLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Cek apakah ini hasil dari Pindai-dan-Cari
            val ocrQuery = result.data?.getStringExtra(VehicleInputActivity.OCR_SEARCH_QUERY)
            if (!ocrQuery.isNullOrEmpty()) {
                Log.d("MainActivity", "Menerima kueri OCR: $ocrQuery")
                // Panggil fungsi pencarian baru
                performOcrSearch(ocrQuery)
            } else {
                Log.d("MainActivity", "Hasil OK dari Input, refresh data (jika perlu).")
                // Jika ini adalah hasil dari "Tambah/Edit Manual"
                // onResume() akan menangani refresh data.
            }
        } else {
            Log.d("MainActivity", "vehicleInputLauncher dibatalkan (resultCode: ${result.resultCode})")
        }
    }
    // --- AKHIR PERUBAHAN 1 ---

    private val vehicleDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // No need to handle result
    }

//    private fun initPython() {
//        // ... (kode python)
//    }
//
//    private fun getPythonMessage(): String {
//        // ... (kode python)
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        // --- TAMBAHKAN INI: Setup Global Exception Handler ---
        setupGlobalExceptionHandler()
        // ----------------------------------------------------

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            // 1. Kode padding Anda yang sudah ada
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)

            // 2. --- TAMBAHAN BARU ---
            // Cek apakah keyboard (IME) sedang terlihat
            val isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())

            // Jika keyboard TIDAK terlihat, hilangkan fokus dari view manapun
            if (!isKeyboardVisible) {
                currentFocus?.clearFocus() // Ini akan menghilangkan fokus dari editTextSearch
            }
            // ------------------------

            // 3. Kembalikan insets
            insets
        }

        initializeViews()
        setupRepository()
        setupRecyclerView()
        setupSearchFunctionality()
        setupFloatingActionButton()
        observeVehicles()
    }

    // --- FUNGSI BARU UNTUK MENANGANI CRASH (Tidak berubah) ---
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Tangkap error di sini
            Log.e("AppCrash", "Uncaught exception: ", throwable)

            // Dapatkan detail error (stack trace) sebagai String
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTraceString = sw.toString()

            // Buat pesan error yang akan ditampilkan
            // Batasi panjangnya agar muat di dialog
            val errorMessage = """
                Maaf, aplikasi mengalami error tak terduga.
                Mohon laporkan detail berikut ke pengembang:

                Error: ${throwable.javaClass.simpleName}
                Pesan: ${throwable.message ?: "Tidak ada pesan"}

                Detail (awal):
                ${stackTraceString.take(1000)}
                ${if (stackTraceString.length > 1000) "..." else ""}
            """.trimIndent()

            // Tampilkan dialog di UI thread (penting!)
            // Pastikan activity masih valid sebelum menampilkan dialog
            if (!isFinishing && !isDestroyed) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setTitle("Terjadi Error")
                        .setMessage(errorMessage)
                        .setPositiveButton("Tutup Aplikasi") { _, _ ->
                            // Tutup aplikasi setelah pengguna melihat error
                            finishAffinity() // Menutup semua activity
                            exitProcess(1) // Keluar paksa dari proses
                        }
                        .setCancelable(false) // Mencegah dialog ditutup tanpa sengaja
                        .show()
                }
            } else {
                Log.e("AppCrash", "Activity is finishing or destroyed, cannot show dialog.")
                // Jika activity sudah hancur, mungkin panggil default handler
                defaultHandler?.uncaughtException(thread, throwable)
            }

            // (Opsional) Jika Anda ingin default handler juga berjalan (misalnya untuk reporting crash bawaan)
            // defaultHandler?.uncaughtException(thread, throwable)
        }
    }
    // --- AKHIR FUNGSI BARU ---


    private fun initializeViews() {
        recyclerViewVehicles = findViewById(R.id.recyclerViewVehicles)
        editTextSearch = findViewById(R.id.editTextSearch)
        textViewEmptyState = findViewById(R.id.textViewEmptyState)
        fabAddVehicle = findViewById(R.id.fabAddVehicle)
    }

    private fun setupRepository() {
        vehicleRepository = VehicleRepository(this)
    }

    private fun setupRecyclerView() {
        vehicleAdapter = VehicleAdapter(vehicleList) { vehicle ->
            openVehicleDetail(vehicle)
        }
        recyclerViewVehicles.layoutManager = LinearLayoutManager(this)
        recyclerViewVehicles.adapter = vehicleAdapter
    }

    private fun setupSearchFunctionality() {
        editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                observeVehicles(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupFloatingActionButton() {
        fabAddVehicle.setOnClickListener {
            showAppMenuDialog()
        }
    }

    private fun openVehicleInput() {
        val intent = Intent(this, VehicleInputActivity::class.java)
        vehicleInputLauncher.launch(intent)
    }

    private fun openVehicleDetail(vehicle: Vehicle) {
        val intent = Intent(this, VehicleDetailActivity::class.java)
        // Kunci Anda adalah "vehicle" (Parcelable), bukan "vehicle_id" (Int)
        intent.putExtra("vehicle", vehicle)
        vehicleDetailLauncher.launch(intent)
    }

    private fun observeVehicles(query: String = "") {
        lifecycleScope.launch {
            val flow = if (query.isEmpty()) {
                vehicleRepository.getAllVehicles()
            } else {
                // Konsep yang ada: searchVehicles berdasarkan nama
                vehicleRepository.searchVehicles(query)
            }
            flow.collect { entities ->
                vehicleList.clear()
                vehicleList.addAll(entities.map { Vehicle.fromEntity(it) })
                vehicleAdapter.notifyDataSetChanged()
                updateEmptyState()
            }
        }
    }

    private fun updateEmptyState() {
        if (vehicleList.isEmpty()) {
            textViewEmptyState.visibility = View.VISIBLE
            recyclerViewVehicles.visibility = View.GONE
        } else {
            textViewEmptyState.visibility = View.GONE
            recyclerViewVehicles.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        observeVehicles(editTextSearch.text.toString())
    }

    // --- Fungsi Dialog (Tidak Berubah) ---
    private fun showAppMenuDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_menu, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_select_action)
            .setView(dialogView)
            .create()
        val enterManual = dialogView.findViewById<TextView>(R.id.text_enter_manual)
        val scanData = dialogView.findViewById<TextView>(R.id.text_scan_data)
        enterManual.setOnClickListener {
            dialog.dismiss()
            openVehicleInput() // Membuka mode formulir manual
        }
        scanData.setOnClickListener {
            dialog.dismiss()
            showScanTargetDialog() // Memulai alur pindai
        }
        dialog.show()
    }

    private fun showScanTargetDialog() {
        val items = arrayOf("Chassis Number", "Engine Number")
        AlertDialog.Builder(this)
            .setTitle("Select Scan Target")
            .setItems(items) { dialog, which ->
                val targetField = if (which == 0) "chassisNumber" else "engineNumber"
                showScanOptionsDialog(targetField)
            }
            .show()
    }

    private fun showScanOptionsDialog(targetField: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_scan_options, null)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_select_scan_source)
            .setView(dialogView)
            .create()
        val internalCamera = dialogView.findViewById<TextView>(R.id.text_internal_camera)
        val usbCamera = dialogView.findViewById<TextView>(R.id.text_usb_camera)
        val gallery = dialogView.findViewById<TextView>(R.id.text_gallery)
        internalCamera.setOnClickListener {
            dialog.dismiss()
            launchScanActivity(targetField, VehicleInputActivity.SOURCE_INTERNAL_CAM)
        }
        usbCamera.setOnClickListener {
            dialog.dismiss()
            launchScanActivity(targetField, VehicleInputActivity.SOURCE_USB_CAM)
        }
        gallery.setOnClickListener {
            dialog.dismiss()
            launchScanActivity(targetField, VehicleInputActivity.SOURCE_GALLERY)
        }
        dialog.show()
    }

    // Fungsi ini sudah benar, ia meluncurkan activity "tak terlihat"
    // dan hasilnya akan ditangkap oleh 'vehicleInputLauncher'
    private fun launchScanActivity(targetField: String, scanSource: String) {
        val intent = Intent(this, VehicleInputActivity::class.java)
        intent.putExtra(VehicleInputActivity.EXTRA_SCAN_TARGET, targetField)
        intent.putExtra(VehicleInputActivity.EXTRA_SCAN_SOURCE, scanSource)
        vehicleInputLauncher.launch(intent) // Launcher yang sama
    }

    // --- PERUBAHAN 2: FUNGSI BARU UNTUK MELAKUKAN PENCARIAN OCR ---
    /**
     * Melakukan pencarian database menggunakan teks hasil OCR.
     * Ini adalah "konsep yang sama" dengan pencarian Owner Name,
     * tetapi menggunakan kueri pencocokan penuh (exact-match).
     */
    private fun performOcrSearch(ocrQuery: String) {
        lifecycleScope.launch(Dispatchers.IO) { // Lakukan di IO thread
            Log.d("MainActivity", "Mencari di database untuk: $ocrQuery")
            // Panggil fungsi repository yang baru (single-shot suspend fun)
            val vehicleEntity = vehicleRepository.findByChassisOrEngine(ocrQuery)

            // Pindah ke Main thread untuk UI
            withContext(Dispatchers.Main) {
                if (vehicleEntity != null) {
                    // SUKSES: Data ditemukan
                    Log.d("MainActivity", "Data ditemukan: ID ${vehicleEntity.id}. Membuka detail...")

                    // "Konsep yang sama" -> Panggil fungsi navigasi Anda yang sudah ada
                    val vehicle = Vehicle.fromEntity(vehicleEntity)
                    openVehicleDetail(vehicle)

                } else {
                    // GAGAL: Data tidak ditemukan
                    Log.d("MainActivity", "Data tidak ditemukan untuk: $ocrQuery")
                    Toast.makeText(this@MainActivity, "Data tidak ditemukan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    // --- AKHIR PERUBAHAN 2 ---
}

