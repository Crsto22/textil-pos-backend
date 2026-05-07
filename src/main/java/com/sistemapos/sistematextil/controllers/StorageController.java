package com.sistemapos.sistematextil.controllers;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistemapos.sistematextil.services.S3StorageService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final S3StorageService storageService;

    @GetMapping
    public ResponseEntity<?> getAsset(@RequestParam("ref") String ref) {
        try {
            S3StorageService.StoredAsset asset = storageService.readManagedAsset(ref);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .contentType(MediaType.parseMediaType(asset.contentType()))
                    .body(asset.bytes());
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? "No se pudo obtener el archivo" : e.getMessage();
            return ResponseEntity.badRequest().body(Map.of("message", message));
        }
    }
}
