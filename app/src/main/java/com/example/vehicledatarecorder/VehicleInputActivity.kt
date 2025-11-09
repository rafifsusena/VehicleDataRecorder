package com.example.vehicledatarecorder

import android.app.Activity
// Hapus import AlertDialog jika tidak digunakan lagi
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap         // <--- IMPORT DIPERLUKAN
import android.graphics.BitmapFactory // <--- IMPORT DIPERLUKAN
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment // Import Environment
import android.provider.MediaStore // Import MediaStore (opsional, tapi baik)
import android.util.Log
import android.view.LayoutInflater // Import LayoutInflater
import android.widget.Button // <<< IMPORT DITAMBAHKAN
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider // Import FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
// --- IMPORT BARU UNTUK PYTHON/CHAQUOPY ---
import com.chaquo.python.PyObject
import com.chaquo.python.Python
// -----------------------------------------
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File // Import File
import java.io.IOException
import java.text.SimpleDateFormat // Import SimpleDateFormat
import java.util.Date // Import Date
import java.util.Locale // Import Locale

class VehicleInputActivity : AppCompatActivity() {

    // --- Properti ---
    private lateinit var editTextOwnerName: TextInputEditText
    private lateinit var editTextChassisNumber: TextInputEditText
    private lateinit var editTextEngineNumber: TextInputEditText
    private lateinit var layoutChassisNumber: TextInputLayout
    private lateinit var layoutEngineNumber: TextInputLayout
    private lateinit var editTextOwnerAddress: TextInputEditText
    private lateinit var editTextVehicleTestNumber: TextInputEditText
    private lateinit var editTextRegistrationNumber: TextInputEditText
    private lateinit var editTextVehicleBrand: TextInputEditText
    private lateinit var editTextVehicleType: TextInputEditText
    private lateinit var editTextVehicleCategory: TextInputEditText
    private lateinit var editTextManufactureYear: TextInputEditText
    private lateinit var editTextFuelType: TextInputEditText
    private lateinit var editTextCylinderCapacity: TextInputEditText
    private lateinit var editTextVehicleColor: TextInputEditText
    private lateinit var buttonSave: MaterialButton
    private var isEditMode = false
    private var vehicleToEdit: Vehicle? = null
    private lateinit var vehicleRepository: VehicleRepository
    private var currentFieldTarget: String? = null
    private lateinit var usbCameraLauncher: ActivityResultLauncher<Intent>
    private var isScanFlowActive = false

    // --- URI SEMENTARA UNTUK KAMERA INTERNAL ---
    private var photoUriForCamera: Uri? = null

    // --- ActivityResultLauncher BARU untuk Kamera Internal ---
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
        Log.d(TAG, "Hasil dari TakePicture diterima. Sukses: $success")
        if (success) {
            photoUriForCamera?.let { uri ->
                Log.d(TAG, "URI dari kamera internal: $uri")
                launchCropperForUri(uri) // Lanjutkan ke Cropper
            } ?: run {
                Log.e(TAG, "TakePicture sukses tapi photoUriForCamera null!")
                Toast.makeText(this, "Gagal mendapatkan URI gambar.", Toast.LENGTH_SHORT).show()
                handleScanCancellation()
            }
        } else {
            Log.w(TAG, "Pengambilan gambar dibatalkan oleh pengguna.")
            Toast.makeText(this, "Pengambilan gambar dibatalkan.", Toast.LENGTH_SHORT).show()
            handleScanCancellation()
        }
    }

    // --- ActivityResultLauncher untuk Cropper (DIMODIFIKASI: Panggil fungsi baru) ---
    private val cropImageLauncher = registerForActivityResult(CropImageContract()) { result ->
        Log.d(TAG, "Hasil dari CROPPER diterima.")
        if (result.isSuccessful) {
            Log.d(TAG, "Cropping BERHASIL.")
            result.uriContent?.let { uri ->
                Log.d(TAG, "URI hasil crop: $uri")
                // --- MODIFIKASI: Panggil fungsi baru ---
                runPreProcessingAndOcr(uri)
            }
        } else {
            val exception = result.error
            Log.e(TAG, "Cropping GAGAL. Error: ${exception?.message}")
            Toast.makeText(this, "Pemotongan gambar dibatalkan/gagal.", Toast.LENGTH_SHORT).show()
            // --- PERBAIKAN: Panggil handle cancellation ---
            handleScanCancellation()
        }
    }

    // --- Companion Object (Tidak berubah) ---
    companion object {
        private const val TAG = "VehicleInputActivity"
        const val EXTRA_SCAN_SOURCE = "EXTRA_SCAN_SOURCE"
        const val EXTRA_SCAN_TARGET = "EXTRA_SCAN_TARGET" // Ini masih dipakai
        const val SOURCE_USB_CAM = "SOURCE_USB_CAM"
        const val SOURCE_INTERNAL_CAM = "SOURCE_INTERNAL_CAM"
        const val SOURCE_GALLERY = "SOURCE_GALLERY"

        // Kunci BARU untuk mengembalikan hasil OCR ke MainActivity
        const val OCR_SEARCH_QUERY = "OCR_SEARCH_QUERY"
    }

    // --- Fungsi onCreate (Tidak berubah) ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "VehicleInputActivity: onCreate")

        // Selalu setup launcher, dibutuhkan oleh kedua mode
        setupLaunchers()

        // Periksa apakah kita dalam Mode Pemindai (Scan Mode)
        if (savedInstanceState == null && intent.hasExtra(EXTRA_SCAN_SOURCE) && intent.hasExtra(EXTRA_SCAN_TARGET)) {
            // --- INI ADALAH MODE PEMINDAI ---
            Log.d(TAG, "Aktivitas dimulai dalam mode pindai (tanpa UI).")
            isScanFlowActive = true

            // JANGAN PANGGIL setContentView() ATAU FUNGSI UI LAINNYA

            val targetField = intent.getStringExtra(EXTRA_SCAN_TARGET)
            if (targetField == null || (targetField != "chassisNumber" && targetField != "engineNumber")) {
                Log.e(TAG, "EXTRA_SCAN_TARGET tidak valid! Membatalkan pindai.")
                Toast.makeText(this, "Scan target invalid.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            currentFieldTarget = targetField
            Log.d(TAG, "Target field diatur ke: $currentFieldTarget")

            // Langsung luncurkan pemindai
            launchSelectedScanner()

        } else if (savedInstanceState == null && (intent.hasExtra(EXTRA_SCAN_SOURCE) || intent.hasExtra(EXTRA_SCAN_TARGET))) {
            // Error jika salah satu extra hilang
            Log.e(TAG, "Mode pindai di-request tapi salah satu extra (target/source) hilang.")
            Toast.makeText(this, "Scan launch error.", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            // --- INI ADALAH MODE FORMULIR (NORMAL) ---
            Log.d(TAG, "Aktivitas dimulai dalam mode formulir (dengan UI).")
            isScanFlowActive = false // Pastikan false

            // Muat UI
            setContentView(R.layout.activity_vehicle_input)

            // Setup repository HANYA untuk mode formulir
            setupRepository() // <-- Panggil di sini

            ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }

            // Setup semua komponen UI
            initializeViews()
            setupToolbar()
            setupClickListeners()
            handleEditMode()
        }
    }

    // --- setupLaunchers (Tidak berubah) ---
    private fun setupLaunchers() {
        usbCameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imageUriString = result.data?.getStringExtra(UsbCameraActivity.EXTRA_IMAGE_URI)
                if (imageUriString != null) {
                    val imageUri = Uri.parse(imageUriString)
                    launchCropperForUri(imageUri) // Lanjutkan ke Cropper
                } else {
                    Log.e(TAG, "Hasil OK dari USB Cam, tapi URI null!")
                    handleScanCancellation() // Gagal dapat URI, batalkan
                }
            } else {
                Log.w(TAG, "Hasil dari UsbCameraActivity DIBATALKAN (resultCode: ${result.resultCode})")
                // --- PERBAIKAN: Panggil handle cancellation ---
                handleScanCancellation()
            }
        }
        // takePictureLauncher diinisialisasi di atas (sebagai properti kelas)
    }

    // --- Fungsi Cropper (Tidak berubah) ---
    private fun launchCropperForUri(uri: Uri) {
        val cropOptions = CropImageOptions(
            guidelines = CropImageView.Guidelines.ON,
            activityTitle = "Potong Gambar", // Ganti judul
            cropShape = CropImageView.CropShape.RECTANGLE,
            autoZoomEnabled = false,
            allowRotation = true,
            minCropResultWidth = 100,
            minCropResultHeight = 50,
            outputCompressFormat = Bitmap.CompressFormat.JPEG,
            outputCompressQuality = 90
        )
        val cropImageContractOptions = CropImageContractOptions(uri, cropOptions)
        Log.i(TAG, "Meluncurkan Cropper untuk URI: $uri")
        try {
            cropImageLauncher.launch(cropImageContractOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Error saat meluncurkan CropImage Activity dari URI", e)
            Toast.makeText(this, "Gagal membuka editor gambar: ${e.message}", Toast.LENGTH_LONG).show()
            handleScanCancellation() // Gagal, batalkan
        }
    }

    /**
     * Hanya digunakan untuk GALERI.
     */
    private fun launchCropperWithSource(isCamera: Boolean /* Harusnya selalu false */) {
        if (isCamera) {
            Log.e(TAG, "launchCropperWithSource dipanggil dengan isCamera=true!")
        }

        val cropOptions = CropImageOptions(
            guidelines = CropImageView.Guidelines.ON,
            activityTitle = "Pilih Gambar",
            cropShape = CropImageView.CropShape.RECTANGLE,
            autoZoomEnabled = false,
            allowRotation = true,
            minCropResultWidth = 100,
            minCropResultHeight = 50,
            outputCompressFormat = Bitmap.CompressFormat.JPEG,
            outputCompressQuality = 90,
            imageSourceIncludeCamera = false, // Hanya galeri
            imageSourceIncludeGallery = true
        )
        val cropImageContractOptions = CropImageContractOptions(null, cropOptions)
        Log.i(TAG, "Meluncurkan Cropper untuk memilih dari Galeri")
        try {
            cropImageLauncher.launch(cropImageContractOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Error saat meluncurkan CropImage Activity untuk Galeri", e)
            Toast.makeText(this, "Gagal membuka galeri: ${e.message}", Toast.LENGTH_LONG).show()
            handleScanCancellation() // Gagal, batalkan
        }
    }

    // --- FUNGSI BARU: Membuat URI File Sementara untuk Kamera (Tidak berubah) ---
    private fun createImageUri(): Uri? {
        return try {
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            // Gunakan cache dir eksternal
            val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

            if (storageDir == null) {
                Log.e(TAG, "Direktori penyimpanan eksternal tidak tersedia.")
                return null
            }

            if (!storageDir.exists()) storageDir.mkdirs()

            val imageFile = File.createTempFile(imageFileName, ".jpg", storageDir)
            Log.d(TAG, "File sementara dibuat: ${imageFile.absolutePath}")

            FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider", // PASTIKAN INI SESUAI MANIFEST
                imageFile
            )
        } catch (ex: IOException) {
            Log.e(TAG, "Error membuat file gambar sementara", ex)
            null
        } catch (ex: IllegalArgumentException) {
            Log.e(TAG, "Error mendapatkan URI dari FileProvider, cek authority", ex)
            null
        }
    }

    // --- initializeViews, setupRepository, setupToolbar, setupClickListeners (Tidak berubah, tapi dipanggil kondisional) ---
    private fun initializeViews() {
        editTextOwnerName = findViewById(R.id.editTextOwnerName)
        editTextChassisNumber = findViewById(R.id.editTextChassisNumber)
        editTextEngineNumber = findViewById(R.id.editTextEngineNumber)
        layoutChassisNumber = findViewById(R.id.layoutChassisNumber)
        layoutEngineNumber = findViewById(R.id.layoutEngineNumber)
        editTextOwnerAddress = findViewById(R.id.editTextOwnerAddress)
        editTextVehicleTestNumber = findViewById(R.id.editTextVehicleTestNumber)
        editTextRegistrationNumber = findViewById(R.id.editTextRegistrationNumber)
        editTextVehicleBrand = findViewById(R.id.editTextVehicleBrand)
        editTextVehicleType = findViewById(R.id.editTextVehicleType)
        editTextVehicleCategory = findViewById(R.id.editTextVehicleCategory)
        editTextManufactureYear = findViewById(R.id.editTextManufactureYear)
        editTextFuelType = findViewById(R.id.editTextFuelType)
        editTextCylinderCapacity = findViewById(R.id.editTextCylinderCapacity)
        editTextVehicleColor = findViewById(R.id.editTextVehicleColor)
        buttonSave = findViewById(R.id.buttonSave)
    }
    private fun setupRepository() { vehicleRepository = VehicleRepository(this) }
    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }
    private fun setupClickListeners() {
        buttonSave.setOnClickListener { if (validateInput()) { saveVehicle() } }
    }

    // --- isUsbCameraConnected (Tidak berubah) ---
    private fun isUsbCameraConnected(): Boolean {
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        for (device in usbManager.deviceList.values) {
            for (i in 0 until device.interfaceCount) {
                if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO) {
                    return true
                }
            }
        }
        return false
    }

    // --- launchSelectedScanner (Tidak berubah) ---
    private fun launchSelectedScanner() {
        when (intent.getStringExtra(EXTRA_SCAN_SOURCE)) {
            SOURCE_USB_CAM -> {
                Log.d(TAG, "Meluncurkan pemindai: USB Camera")
                if (isUsbCameraConnected()) {
                    val intent = Intent(this, UsbCameraActivity::class.java)
                    usbCameraLauncher.launch(intent)
                } else {
                    Toast.makeText(this, "Kamera USB tidak terdeteksi.", Toast.LENGTH_LONG).show()
                    handleScanCancellation() // Gagal, batalkan
                }
            }
            SOURCE_INTERNAL_CAM -> {
                Log.d(TAG, "Meluncurkan pemindai: Internal Camera (via TakePicture contract)")
                // 1. Buat URI tujuan
                val createdUri = createImageUri() // Simpan ke variabel lokal dulu
                photoUriForCamera = createdUri // Tetapkan ke properti kelas

                // --- PERBAIKAN SMART CAST ---
                // 2. Salin ke variabel lokal SETELAH pengecekan null
                val currentPhotoUri = photoUriForCamera
                if (currentPhotoUri != null) {
                    try {
                        // 3. Gunakan variabel lokal (currentPhotoUri)
                        takePictureLauncher.launch(currentPhotoUri)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error meluncurkan TakePicture contract", e)
                        Toast.makeText(this, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_LONG).show()
                        handleScanCancellation() // Gagal, batalkan
                    }
                } else {
                    // Jika gagal buat URI (createdUri atau photoUriForCamera null)
                    Toast.makeText(this, "Gagal menyiapkan penyimpanan kamera.", Toast.LENGTH_SHORT).show()
                    handleScanCancellation() // Gagal, batalkan
                }
                // --- AKHIR PERBAIKAN ---
            }
            SOURCE_GALLERY -> {
                Log.d(TAG, "Meluncurkan pemindai: Gallery (via Cropper)")
                launchCropperWithSource(isCamera = false) // Hanya galeri
            }
            else -> {
                Log.e(TAG, "Sumber pindai tidak diketahui. Membatalkan.")
                Toast.makeText(this, "Sumber pindai tidak valid.", Toast.LENGTH_SHORT).show()
                handleScanCancellation() // Sumber tidak valid, batalkan
            }
        }
    }

    // --- FUNGSI BARU: Menangani pembatalan alur pindai (Tidak berubah) ---
    private fun handleScanCancellation() {
        if (isScanFlowActive) {
            Log.d(TAG, "Alur pindai dibatalkan atau gagal. Menutup activity.")
            isScanFlowActive = false // Reset flag
            // Hapus URI sementara jika ada
            photoUriForCamera?.let { uri ->
                try {
                    // Hapus content URI
                    contentResolver.delete(uri, null, null)
                    Log.d(TAG, "File sementara kamera dihapus: $uri")
                } catch (e: Exception) {
                    Log.w(TAG, "Gagal menghapus file sementara kamera: $uri", e)
                }
            }
            photoUriForCamera = null
            setResult(Activity.RESULT_CANCELED) // Pastikan mengirim RESULT_CANCELED
            finish() // Kembali ke MainActivity
        } else {
            Log.d(TAG, "handleScanCancellation dipanggil tapi scan flow tidak aktif.")
        }
    }


    // --- FUNGSI OCR DIGANTI DENGAN 'runPreProcessingAndOcr' ---
    /**
     * Menggabungkan Pre-processing Python dengan ML Kit OCR.
     */
    private fun runPreProcessingAndOcr(uri: Uri) {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Pre-processing gambar...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch(Dispatchers.IO) { // Lakukan di IO thread
            try {
                // 1. Baca byte gambar dari URI
                val imageBytes = contentResolver.openInputStream(uri)?.use {
                    it.readBytes()
                }

                if (imageBytes == null) {
                    throw IOException("Gagal membaca byte dari URI.")
                }

                Log.d(TAG, "Byte gambar asli dibaca: ${imageBytes.size} bytes")

                // 2. Panggil Python (OpenCV) untuk pre-processing
                val py = Python.getInstance()
                // Panggil 'preprocess.py' dan fungsi 'process_image'
                val pyModule = py.getModule("preprocess")

                // --- PERBAIKAN: Menggunakan .toJava(ByteArray::class.java) ---
                val processedBytes = pyModule.callAttr("process_image", imageBytes)
                    .toJava(ByteArray::class.java)

                Log.d(TAG, "Byte gambar diproses: ${processedBytes.size} bytes")

                // 3. Decode byte yang sudah diproses menjadi Bitmap
                val processedBitmap: Bitmap? = BitmapFactory.decodeByteArray(processedBytes, 0, processedBytes.size)

                if (processedBitmap == null) {
                    throw IOException("Gagal men-decode bitmap yang sudah diproses (processedBitmap is null).")
                }

                // 4. Buat InputImage dari Bitmap yang sudah diproses (sekarang non-null)
                val image = InputImage.fromBitmap(processedBitmap, 0)

                // Pindah ke Main thread untuk update UI (ProgressDialog)
                withContext(Dispatchers.Main) {
                    progressDialog.setMessage("Menjalankan OCR...")
                }

                // 5. Jalankan ML Kit OCR (masih di IO thread)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        // Sukses, pindah ke Main thread
                        runOnUiThread {
                            progressDialog.dismiss()
                            val resultText = visionText.text
                            Log.d(TAG, "Hasil OCR: $resultText")
                            val cleanedText = resultText.replace(Regex("[\\s\\n]+"), " ").trim()

                            if (cleanedText.isNotEmpty()) {
                                showOcrResultDialog(cleanedText, uri) // Panggil dialog
                            } else {
                                Toast.makeText(this@VehicleInputActivity, "Tidak ada teks yang terdeteksi.", Toast.LENGTH_SHORT).show()
                                isScanFlowActive = false
                                cleanupTemporaryUri(uri)
                                if (isScanFlowActive) {
                                    setResult(Activity.RESULT_CANCELED)
                                    finish()
                                }
                            }
                        }
                    }
                    .addOnFailureListener { e ->
                        // Gagal, pindah ke Main thread
                        runOnUiThread {
                            progressDialog.dismiss()
                            Log.e(TAG, "Pengenalan teks gagal", e)
                            Toast.makeText(this@VehicleInputActivity, "Pengenalan teks gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                            isScanFlowActive = false
                            cleanupTemporaryUri(uri)
                            if (isScanFlowActive) {
                                setResult(Activity.RESULT_CANCELED)
                                finish()
                            }
                        }
                    }

            } catch (e: Exception) {
                // Tangani error dari (1) baca bytes, (2) panggil python, (3) decode bitmap
                e.printStackTrace()
                runOnUiThread {
                    progressDialog.dismiss()
                    Log.e(TAG, "Gagal memproses gambar Python/OpenCV", e)
                    Toast.makeText(this@VehicleInputActivity, "Gagal memproses gambar: ${e.message}", Toast.LENGTH_SHORT).show()
                    cleanupTemporaryUri(uri)
                    if (isScanFlowActive) {
                        handleScanCancellation()
                    }
                }
            }
        }
    }
    // --- AKHIR FUNGSI BARU ---


    // --- FUNGSI MENAMPILKAN DIALOG (Tidak berubah) ---
    private fun showOcrResultDialog(ocrText: String, originalUri: Uri) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_ocr_result, null)
        val etOcrResult = dialogView.findViewById<EditText>(R.id.etOcrResult)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveOcr)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelOcr)

        etOcrResult.setText(ocrText)
        etOcrResult.requestFocus()
        etOcrResult.setSelection(ocrText.length)

        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Aksi Tombol Simpan
        btnSave.setOnClickListener {
            val correctedText = etOcrResult.text.toString().trim()

            // --- PERUBAHAN: Kembalikan hasil ke MainActivity ---
            Log.d(TAG, "Mengembalikan hasil OCR: $correctedText")
            val resultIntent = Intent()
            resultIntent.putExtra(OCR_SEARCH_QUERY, correctedText)
            setResult(Activity.RESULT_OK, resultIntent)
            dialog.dismiss() // Tutup dialog (akan mentrigger onDismissListener)
            // --- AKHIR PERUBAHAN ---
        }

        // Aksi Tombol Batal
        btnCancel.setOnClickListener {
            setResult(Activity.RESULT_CANCELED) // Tetapkan batal
            dialog.dismiss() // Tutup dialog (akan mentrigger onDismissListener)
        }

        // PENTING: Cleanup setelah dialog ditutup (apapun alasannya)
        dialog.setOnDismissListener {
            Log.d(TAG, "Dialog OCR ditutup. Membersihkan URI dan mereset flag.")
            isScanFlowActive = false // Reset flag scan

            // Cleanup URI dari cropper
            cleanupTemporaryUri(originalUri)
            // Cleanup juga URI asli dari kamera (jika ada)
            cleanupTemporaryUri(photoUriForCamera)

            // Selalu tutup activity setelah dialog ditutup
            finish()
        }

        dialog.show()
    }


    // --- FUNGSI CLEANUP URI (Diperbarui) ---
    private fun cleanupTemporaryUri(uri: Uri?) {
        // Fungsi ini sekarang lebih umum
        if (uri == null) return

        // Hanya hapus jika itu adalah URI dari cache kita (dari internal cam atau cropper)
        // Ini adalah pemeriksaan sederhana; mungkin perlu disempurnakan
        if (uri.toString().contains(applicationContext.packageName)) {
            Log.d(TAG, "Membersihkan URI sementara: $uri")
            try {
                // Coba hapus content URI
                contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "Gagal menghapus URI sementara: $uri", e)
            }
        }

        // Jika URI yang dihapus adalah photoUriForCamera, null-kan
        if (uri == photoUriForCamera) {
            photoUriForCamera = null
        }
    }


    // --- Sisa fungsi (handleEditMode, populateFieldsForEdit, validateInput, clearErrors, saveVehicle) tidak berubah ---
    // Fungsi-fungsi ini HANYA akan terpanggil jika kita dalam Mode Formulir
    private fun handleEditMode() {
        if (intent.hasExtra(EXTRA_SCAN_SOURCE)) {
            Log.d(TAG, "Mode pindai aktif, membatalkan handleEditMode.")
            supportActionBar?.title = "Tambah Kendaraan"
            buttonSave.text = "Simpan Kendaraan"
            return
        }
        isEditMode = intent.getBooleanExtra("isEdit", false)
        vehicleToEdit = intent.getParcelableExtra<Vehicle>("vehicle")
        if (isEditMode && vehicleToEdit != null) {
            populateFieldsForEdit()
            supportActionBar?.title = "Edit Kendaraan"
            buttonSave.text = "Update Kendaraan"
        } else {
            supportActionBar?.title = "Tambah Kendaraan"
            buttonSave.text = "Simpan Kendaraan"
        }
    }
    private fun populateFieldsForEdit() {
        vehicleToEdit?.let { vehicle ->
            editTextOwnerName.setText(vehicle.ownerName)
            editTextChassisNumber.setText(vehicle.chassisNumber)
            editTextEngineNumber.setText(vehicle.engineNumber)
            editTextOwnerAddress.setText(vehicle.ownerAddress)
            editTextVehicleTestNumber.setText(vehicle.vehicleTestNumber)
            editTextRegistrationNumber.setText(vehicle.registrationNumber)
            editTextVehicleBrand.setText(vehicle.vehicleBrand)
            editTextVehicleType.setText(vehicle.vehicleType)
            editTextVehicleCategory.setText(vehicle.vehicleCategory)
            editTextManufactureYear.setText(vehicle.manufactureYear.toString())
            editTextFuelType.setText(vehicle.fuelType)
            editTextCylinderCapacity.setText(vehicle.cylinderCapacity)
            editTextVehicleColor.setText(vehicle.vehicleColor)
        }
    }
    private fun validateInput(): Boolean {
        clearErrors()
        if (editTextOwnerName.text.toString().trim().isEmpty()) { editTextOwnerName.error = "Nama pemilik wajib diisi"; return false; }
        if (editTextOwnerAddress.text.toString().trim().isEmpty()) { editTextOwnerAddress.error = "Alamat pemilik wajib diisi"; return false; }
        if (editTextVehicleTestNumber.text.toString().trim().isEmpty()) { editTextVehicleTestNumber.error = "Nomor uji wajib diisi"; return false; }
        if (editTextRegistrationNumber.text.toString().trim().isEmpty()) { editTextRegistrationNumber.error = "Nomor registrasi wajib diisi"; return false; }
        if (editTextVehicleBrand.text.toString().trim().isEmpty()) { editTextVehicleBrand.error = "Merk wajib diisi"; return false; }
        if (editTextVehicleType.text.toString().trim().isEmpty()) { editTextVehicleType.error = "Tipe wajib diisi"; return false; }
        if (editTextVehicleCategory.text.toString().trim().isEmpty()) { editTextVehicleCategory.error = "Kategori wajib diisi"; return false; }
        if (editTextManufactureYear.text.toString().trim().isEmpty()) { editTextManufactureYear.error = "Tahun wajib diisi"; return false; }
        if (editTextFuelType.text.toString().trim().isEmpty()) { editTextFuelType.error = "Bahan bakar wajib diisi"; return false; }
        if (editTextCylinderCapacity.text.toString().trim().isEmpty()) { editTextCylinderCapacity.error = "Kapasitas silinder wajib diisi"; return false; }
        if (editTextVehicleColor.text.toString().trim().isEmpty()) { editTextVehicleColor.error = "Warna wajib diisi"; return false; }
        return true
    }
    private fun clearErrors() {
        editTextOwnerName.error = null; editTextChassisNumber.error = null; editTextEngineNumber.error = null; editTextOwnerAddress.error = null; editTextVehicleTestNumber.error = null; editTextRegistrationNumber.error = null; editTextVehicleBrand.error = null; editTextVehicleType.error = null; editTextVehicleCategory.error = null; editTextManufactureYear.error = null; editTextFuelType.error = null; editTextCylinderCapacity.error = null; editTextVehicleColor.error = null
    }
    private fun saveVehicle() {
        val year = editTextManufactureYear.text.toString().trim().toIntOrNull() ?: 0 // Handle jika bukan angka
        val vehicle = if (isEditMode && vehicleToEdit != null) {
            vehicleToEdit!!.copy(
                ownerName = editTextOwnerName.text.toString().trim(), chassisNumber = editTextChassisNumber.text.toString().trim(), engineNumber = editTextEngineNumber.text.toString().trim(), ownerAddress = editTextOwnerAddress.text.toString().trim(), vehicleTestNumber = editTextVehicleTestNumber.text.toString().trim(), registrationNumber = editTextRegistrationNumber.text.toString().trim(), vehicleBrand = editTextVehicleBrand.text.toString().trim(), vehicleType = editTextVehicleType.text.toString().trim(), vehicleCategory = editTextVehicleCategory.text.toString().trim(), manufactureYear = year, fuelType = editTextFuelType.text.toString().trim(), cylinderCapacity = editTextCylinderCapacity.text.toString().trim(), vehicleColor = editTextVehicleColor.text.toString().trim()
            )
        } else {
            Vehicle(
                ownerName = editTextOwnerName.text.toString().trim(), chassisNumber = editTextChassisNumber.text.toString().trim(), engineNumber = editTextEngineNumber.text.toString().trim(), ownerAddress = editTextOwnerAddress.text.toString().trim(), vehicleTestNumber = editTextVehicleTestNumber.text.toString().trim(), registrationNumber = editTextRegistrationNumber.text.toString().trim(), vehicleBrand = editTextVehicleBrand.text.toString().trim(), vehicleType = editTextVehicleType.text.toString().trim(), vehicleCategory = editTextVehicleCategory.text.toString().trim(), manufactureYear = year, fuelType = editTextFuelType.text.toString().trim(), cylinderCapacity = editTextCylinderCapacity.text.toString().trim(), vehicleColor = editTextVehicleColor.text.toString().trim()
            )
        }
        buttonSave.isEnabled = false; buttonSave.text = if (isEditMode) "Memperbarui..." else "Menyimpan..."
        lifecycleScope.launch {
            try {
                if (isEditMode) { vehicleRepository.updateVehicle(vehicle.toEntity()) } else { vehicleRepository.insertVehicle(vehicle.toEntity()) }
                val message = if (isEditMode) "Kendaraan berhasil diperbarui!" else "Kendaraan berhasil disimpan!"
                Toast.makeText(this@VehicleInputActivity, message, Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK) // Set result OK
                finish()
            } catch (e: Exception) {
                buttonSave.isEnabled = true
                // --- PERBAIKAN TYPO DARI PERMINTAAN SEBELUMNYA ---
                buttonSave.text = if (isEditMode) "Update Kendaraan" else "Simpan Kendaraan"
                Snackbar.make(buttonSave, "Error menyimpan: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}

