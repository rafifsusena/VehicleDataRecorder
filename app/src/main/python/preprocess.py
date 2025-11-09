"""
Adaptive Image Preprocessing for Vehicle Engine Number Inspection
---------------------------------------------------------------
Author : Rafif Susena (dimodifikasi untuk Chaquopy oleh AI)
Description :
Fungsi ini melakukan preprocessing citra secara adaptif menggunakan OpenCV.
Fungsi ini dimodifikasi untuk menerima BYTE ARRAY dari Kotlin,
bukan file path.
"""

import cv2
import numpy as np
import io # Diperlukan untuk encoding kembali

def process_image(image_bytes):
    """
    Fungsi preprocessing adaptif untuk citra OCR kendaraan.
    
    Parameters:
    -----------
    image_bytes : bytes
        Byte array dari citra input (dari Kotlin).

    Returns:
    --------
    processed_bytes : bytes
        Byte array dari citra hasil preprocessing (siap OCR).
    """

    try:
        # -------------------------------------------------------------------------
        # 1️⃣ Decode byte array dari Kotlin menjadi gambar OpenCV
        # -------------------------------------------------------------------------
        nparr = np.frombuffer(image_bytes, np.uint8)
        img = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if img is None:
            print("[PY-ERROR] Gagal men-decode byte gambar.")
            return image_bytes # Kembalikan gambar asli jika gagal

        # -------------------------------------------------------------------------
        # 2️⃣ Konversi ke grayscale (Logika Asli Anda Dimulai)
        # -------------------------------------------------------------------------
        gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

        # -------------------------------------------------------------------------
        # 3️⃣ Analisis statistik intensitas citra untuk deteksi kondisi
        # -------------------------------------------------------------------------
        mean_intensity = np.mean(gray)
        std_intensity = np.std(gray)

        if mean_intensity < 80:
            condition = "dark"
        elif mean_intensity > 180:
            condition = "bright"
        elif std_intensity < 40:
            condition = "low_contrast"
        else:
            condition = "normal"

        print(f"[PY-INFO] Kondisi gambar terdeteksi: {condition} (Mean: {mean_intensity:.2f}, Std: {std_intensity:.2f})")

        # -------------------------------------------------------------------------
        # 4️⃣ Kompensasi pencahayaan (Illumination Correction)
        # -------------------------------------------------------------------------
        blur = cv2.GaussianBlur(gray, (0, 0), sigmaX=25, sigmaY=25)
        corrected = cv2.divide(gray, blur, scale=255)

        # -------------------------------------------------------------------------
        # 5️⃣ CLAHE (Contrast Limited Adaptive Histogram Equalization)
        # -------------------------------------------------------------------------
        if condition == "dark":
            clipLimit = 3.0
            tileGridSize = (8, 8)
        elif condition == "bright":
            clipLimit = 2.0
            tileGridSize = (6, 6)
        elif condition == "low_contrast":
            clipLimit = 4.0
            tileGridSize = (12, 12)
        else:  # normal
            clipLimit = 2.5
            tileGridSize = (8, 8)

        clahe = cv2.createCLAHE(clipLimit=clipLimit, tileGridSize=tileGridSize)
        enhanced = clahe.apply(corrected)

        # -------------------------------------------------------------------------
        # 6️⃣ Bilateral Filter untuk meredam noise tanpa menghapus tepi
        # -------------------------------------------------------------------------
        filtered = cv2.bilateralFilter(enhanced, d=9, sigmaColor=75, sigmaSpace=75)

        # -------------------------------------------------------------------------
        # 7️⃣ Adaptive Thresholding (Gaussian) dengan parameter adaptif
        # -------------------------------------------------------------------------
        if condition == "dark":
            blockSize = 11
            C = 2
        elif condition == "bright":
            blockSize = 15
            C = 5
        elif condition == "low_contrast":
            blockSize = 9
            C = 3
        else:
            blockSize = 13
            C = 4

        if blockSize % 2 == 0:
            blockSize += 1

        binary = cv2.adaptiveThreshold(
            filtered, 255,
            cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
            cv2.THRESH_BINARY,
            blockSize, C
        )

        # -------------------------------------------------------------------------
        # 8️⃣ Morphological Operation: Closing atau Opening
        # -------------------------------------------------------------------------
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3))
        if condition in ["dark", "low_contrast"]:
            morph = cv2.morphologyEx(binary, cv2.MORPH_CLOSE, kernel, iterations=1)
        else:
            morph = cv2.morphologyEx(binary, cv2.MORPH_OPEN, kernel, iterations=1)

        # -------------------------------------------------------------------------
        # 9️⃣ Encode gambar hasil (morph) kembali menjadi byte array (JPEG)
        # -------------------------------------------------------------------------
        is_success, buffer = cv2.imencode(".jpg", morph, [cv2.IMWRITE_JPEG_QUALITY, 95])

        if is_success:
            print("[PY-INFO] Preprocessing berhasil, mengembalikan bytes.")
            return buffer.tobytes()
        else:
            print("[PY-ERROR] Gagal men-encode gambar kembali ke bytes.")
            return image_bytes # Kembalikan gambar asli jika gagal encode

    except Exception as e:
        print(f"[PY-ERROR] Error di process_image: {e}")
        return image_bytes # Kembalikan gambar asli jika terjadi error

