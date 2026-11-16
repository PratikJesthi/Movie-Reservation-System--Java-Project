package com.moviebooking.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TicketQrService {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public String verifyUrl(String token) {
        return baseUrl + "/verify/" + token;
    }

    public byte[] qrPng(String token) {
        try {
            String url = verifyUrl(token);
            BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("QR generation failed: " + ex.getMessage(), ex);
        }
    }
}
