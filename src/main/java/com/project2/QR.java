package com.project2;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

public class QR {

    /**
     * Genera una matriz de 64x64 donde cada elemento representa un LED.
     * 1 = LED encendido (blanco), 0 = LED apagado (negro)
     */
    public static int[][] generateQR(String text, int size) throws Exception {
        QRCodeWriter qrWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrWriter.encode(text, BarcodeFormat.QR_CODE, size, size);

        int[][] matrix = new int[size][size];
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                matrix[x][y] = bitMatrix.get(x, y) ? 0 : 1; // LED encendido = 1
            }
        }
        return matrix;
    }
}