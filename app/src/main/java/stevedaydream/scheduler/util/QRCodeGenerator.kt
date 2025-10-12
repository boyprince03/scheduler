package stevedaydream.scheduler.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * QR Code 生成工具
 */
object QRCodeGenerator {

    /**
     * 生成 QR Code Bitmap
     * @param content QR Code 內容
     * @param size 圖片大小 (px)
     * @param foregroundColor 前景色 (預設黑色)
     * @param backgroundColor 背景色 (預設白色)
     */
    fun generateQRCode(
        content: String,
        size: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap? {
        return try {
            val hints = hashMapOf<EncodeHintType, Any>().apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H)
                put(EncodeHintType.MARGIN, 1)
            }

            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) foregroundColor else backgroundColor
                }
            }

            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 生成邀請碼 QR Code
     * @param inviteCode 邀請碼
     * @param appScheme Deep Link Scheme (例如: "scheduler://join/org?code=")
     */
    fun generateInviteQRCode(
        inviteCode: String,
        appScheme: String = "scheduler://join/org?code=",
        size: Int = 512
    ): Bitmap? {
        val content = "$appScheme$inviteCode"
        return generateQRCode(content, size)
    }
}