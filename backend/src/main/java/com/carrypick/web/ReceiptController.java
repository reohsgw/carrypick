package com.carrypick.web;

import com.carrypick.service.ReceiptScanService;
import com.carrypick.web.dto.Dtos.ReceiptScan;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** T7: AI receipt reader. Logged-in users only (see SecurityConfig: anyRequest().authenticated()). */
@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {
    private final ReceiptScanService scanner;

    public ReceiptController(ReceiptScanService scanner) {
        this.scanner = scanner;
    }

    @PostMapping(value = "/scan", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReceiptScan scan(@RequestPart("image") MultipartFile image) {
        return scanner.scan(image);
    }
}
