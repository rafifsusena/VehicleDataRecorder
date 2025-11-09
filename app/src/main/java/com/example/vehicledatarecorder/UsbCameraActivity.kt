package com.example.vehicledatarecorder

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.SurfaceTexture // Import SurfaceTexture
import android.net.Uri
import android.hardware.usb.UsbDevice
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.serenegiant.usb.CameraDialog
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import com.serenegiant.usb.Size // Import Size
import com.serenegiant.usbcameracommon.UVCCameraHandler
import com.serenegiant.widget.UVCCameraTextureView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class UsbCameraActivity : AppCompatActivity(), CameraDialog.CameraDialogParent {

    @Volatile
    private var isCameraHandlerActive = false
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var usbMonitor: USBMonitor? = null
    private var cameraHandler: UVCCameraHandler? = null
    private var cameraView: UVCCameraTextureView? = null
    private lateinit var captureButton: ImageButton
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val RESULT_CAMERA_DISCONNECTED = 99
        private const val TAG = "DEBUGCAMERA"
        // --- Ukuran hardcode dihapus ---
        // private const val PREVIEW_WIDTH = 640
        // private const val PREVIEW_HEIGHT = 480
        private const val PREVIEW_MODE = 0 // 0 for YUV (sesuai contoh Java), coba ganti ke 1 (MJPEG) jika masih gagal
        private const val MAX_IMAGE_WIDTH = 1024f // Ukuran target setelah scaling
        private const val USE_SURFACE_ENCODER = false
        // Ukuran fallback jika query gagal
        private const val FALLBACK_WIDTH = 640
        private const val FALLBACK_HEIGHT = 480
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usb_camera)

        captureButton = findViewById(R.id.capture_button)
        captureButton.setOnClickListener { captureImage() }
        captureButton.isEnabled = false

        // Aspect ratio akan di-set di onConnect
        cameraView = findViewById<UVCCameraTextureView>(R.id.camera_view)

        usbMonitor = USBMonitor(this, onDeviceConnectListener)

        // Handler TIDAK dibuat di sini
    }

    private val onDeviceConnectListener = object : USBMonitor.OnDeviceConnectListener {
        override fun onAttach(device: UsbDevice?) {
            Log.d(TAG, "onAttach")
            usbMonitor?.requestPermission(device)
        }

        override fun onConnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?, createNew: Boolean) {
            Log.d(TAG, "onConnect")
            cameraExecutor.execute {

                if (ctrlBlock == null) {
                    Log.e(TAG, "UsbControlBlock null, membatalkan koneksi.")
                    runOnUiThread { Toast.makeText(this@UsbCameraActivity, "Error: Gagal terhubung.", Toast.LENGTH_LONG).show() }
                    return@execute
                }

                var highestResSize: Size? = null
                val tempCamera = UVCCamera()

                // 1. Coba dapatkan ukuran tertinggi yang didukung
                try {
                    tempCamera.open(ctrlBlock)
                    val supportedSizes = tempCamera.supportedSizeList

                    if (!supportedSizes.isNullOrEmpty()) {
                        // Cari ukuran dengan luas area terbesar
                        highestResSize = supportedSizes.maxByOrNull { it.width * it.height }
                        if (highestResSize != null) {
                            Log.i(TAG, "Resolusi tertinggi ditemukan: ${highestResSize.width}x${highestResSize.height}")
                        } else {
                            Log.w(TAG, "Gagal mencari resolusi tertinggi dari daftar.")
                        }
                    } else {
                        Log.w(TAG, "Daftar ukuran yang didukung kosong atau null.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal saat query daftar ukuran kamera. Akan menggunakan fallback.", e)
                } finally {
                    tempCamera.destroy()
                }

                // 2. Tentukan ukuran final (tertinggi atau fallback)
                val finalWidth = highestResSize?.width ?: FALLBACK_WIDTH
                val finalHeight = highestResSize?.height ?: FALLBACK_HEIGHT
                val usingFallback = (highestResSize == null)
                if (usingFallback) {
                    Log.w(TAG, "Menggunakan resolusi fallback: ${FALLBACK_WIDTH}x${FALLBACK_HEIGHT}")
                }

                // 3. Set aspect ratio di UI thread
                runOnUiThread {
                    cameraView?.setAspectRatio(finalWidth.toDouble() / finalHeight)
                }

                // 4. Coba inisialisasi handler dan preview (dengan jaring pengaman)
                try {
                    // Buat handler dengan ukuran final
                    cameraView?.let { view ->
                        cameraHandler = UVCCameraHandler.createHandler(
                            this@UsbCameraActivity,
                            view,
                            if (USE_SURFACE_ENCODER) 0 else 1,
                            finalWidth,
                            finalHeight,
                            PREVIEW_MODE
                        )
                    }

                    // Buka handler
                    cameraHandler?.open(ctrlBlock)

                    // Start preview
                    startPreview()
                    isCameraHandlerActive = true
                    runOnUiThread { captureButton.isEnabled = true }

                } catch (e: Exception) {
                    // Ini menangkap error 'setPreviewSize' jika ukuran (tertinggi ATAU fallback) gagal
                    Log.e(TAG, "FATAL: Gagal memulai kamera dengan ${finalWidth}x${finalHeight} mode $PREVIEW_MODE.", e)
                    runOnUiThread {
                        val errorMsg = if (usingFallback) {
                            "[Error] Kamera tidak mendukung fallback ${finalWidth}x${finalHeight} mode $PREVIEW_MODE."
                        } else {
                            "[Error] Kamera tidak mendukung resolusi tertinggi (${finalWidth}x${finalHeight}) mode $PREVIEW_MODE."
                        }
                        Toast.makeText(this@UsbCameraActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                    releaseCamera()
                }
            }
        }


        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            Log.d(TAG, "onDisconnect")
            releaseCamera()
        }

        override fun onDettach(device: UsbDevice?) {
            Log.d(TAG, "onDettach: Kamera dicabut.")
            releaseCamera()
            mainHandler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this@UsbCameraActivity, "Kamera terputus.", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_CAMERA_DISCONNECTED)
                    finish()
                }
            }, 500)
        }

        override fun onCancel(device: UsbDevice?) {
            Log.d(TAG, "onCancel")
            /* No action needed */
        }
    }

    private fun startPreview() {
        val st: SurfaceTexture? = cameraView?.surfaceTexture
        if (cameraHandler != null && st != null) {
            try {
                cameraHandler?.startPreview(Surface(st))
                Log.d(TAG, "startPreview dipanggil")
            } catch (e: Exception) {
                Log.e(TAG, "Error saat memanggil cameraHandler.startPreview()", e)
                runOnUiThread {
                    Toast.makeText(this@UsbCameraActivity, "[Error] Gagal memulai preview: ${e.message}", Toast.LENGTH_LONG).show()
                }
                releaseCamera()
            }
        } else {
            Log.w(TAG, "cameraHandler atau SurfaceTexture null saat startPreview")
        }
    }


    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")
        usbMonitor?.register()
        cameraView?.onResume()
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        usbMonitor?.unregister()
        releaseCamera()
        cameraView?.onPause()
        super.onStop()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        cameraHandler?.release()
        cameraHandler = null
        if (!cameraExecutor.isShutdown) {
            cameraExecutor.shutdown()
        }
        usbMonitor?.destroy()
        usbMonitor = null
        super.onDestroy()
    }

    private fun releaseCamera() {
        if (isCameraHandlerActive) {
            isCameraHandlerActive = false
            Log.d(TAG, "Memulai releaseCamera di background thread...")
            cameraExecutor.execute {
                try {
                    cameraHandler?.stopPreview()
                    cameraHandler?.close()
                    Log.d(TAG, "stopPreview() dan close() berhasil dipanggil.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error saat melepaskan kamera (mungkin sudah dilepas)", e)
                } finally {
                    runOnUiThread {
                        captureButton.isEnabled = false
                        Log.d(TAG, "Tombol capture dinonaktifkan.")
                    }
                }
            }
        } else {
            Log.d(TAG,"releaseCamera dipanggil tapi handler tidak aktif atau sudah dilepas.")
        }
    }

    private fun captureImage() {
        if (cameraHandler?.isOpened == true && isCameraHandlerActive) {
            captureButton.isEnabled = false
            cameraExecutor.execute {
                try {
                    val originalBitmap = cameraView?.captureStillImage()
                    if (originalBitmap != null) {
                        releaseCamera()
                        val scaledBitmap = scaleBitmap(originalBitmap)
                        val imageUri = saveBitmapToCacheAndGetUri(scaledBitmap)
                        if (!scaledBitmap.isRecycled) {
                            scaledBitmap.recycle()
                        }
                        runOnUiThread {
                            if (imageUri != null) {
                                val resultIntent = Intent().apply {
                                    putExtra(EXTRA_IMAGE_URI, imageUri.toString())
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                setResult(Activity.RESULT_OK, resultIntent)
                                finish()
                            } else {
                                Toast.makeText(this@UsbCameraActivity, "Gagal menyimpan gambar.", Toast.LENGTH_SHORT).show()
                                captureButton.isEnabled = true
                            }
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@UsbCameraActivity, "Gagal mengambil gambar (bitmap null).", Toast.LENGTH_SHORT).show()
                            captureButton.isEnabled = true
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@UsbCameraActivity, "Error saat capture: ${e.message}", Toast.LENGTH_LONG).show()
                        captureButton.isEnabled = true
                    }
                }
            }
        } else {
            Log.w(TAG, "Capture dipanggil tapi handler tidak dibuka atau tidak aktif.")
            runOnUiThread { captureButton.isEnabled = false }
        }
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        if (originalWidth <= MAX_IMAGE_WIDTH) return bitmap
        val scaleFactor = MAX_IMAGE_WIDTH / originalWidth
        val newHeight = (originalHeight * scaleFactor).roundToInt()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, MAX_IMAGE_WIDTH.toInt(), newHeight, true)
        if (scaledBitmap != bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return scaledBitmap
    }

    private fun saveBitmapToCacheAndGetUri(bitmap: Bitmap): Uri? {
        val cachePath = File(cacheDir, "images")
        return try {
            cachePath.mkdirs()
            val file = File(cachePath, "capture_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            }
            FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
        } catch (e: IOException) {
            Log.e(TAG, "Gagal menyimpan bitmap ke cache", e)
            null
        }
    }

    override fun getUSBMonitor(): USBMonitor? = usbMonitor

    override fun onDialogResult(canceled: Boolean) {
        if (canceled) {
            Toast.makeText(this, "Izin ditolak.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}