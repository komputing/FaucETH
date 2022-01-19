package org.komputing.fauceth.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import java.io.ByteArrayOutputStream
import java.util.*

internal fun createQR(content: String, imageSize: Int = 200) = ByteArrayOutputStream().apply {

    val matrix: BitMatrix = MultiFormatWriter().encode(
        content, BarcodeFormat.QR_CODE,
        imageSize, imageSize
    )
    MatrixToImageWriter.writeToStream(matrix, "png", this)

}.let { "data:image/png;base64," + Base64.getEncoder().encodeToString(it.toByteArray()) }