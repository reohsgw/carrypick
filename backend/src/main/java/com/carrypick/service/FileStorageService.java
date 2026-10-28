package com.carrypick.service;

import com.carrypick.common.ApiException;
import com.carrypick.config.AppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/** Saves uploaded images to a local folder (good enough for the demo). Returns a "/uploads/..." URL. */
@Service
public class FileStorageService {
    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp",
            "image/gif", ".gif",
            "application/pdf", ".pdf");

    private final Path root;

    public FileStorageService(AppProperties props) throws IOException {
        this.root = Path.of(props.uploadDir()).toAbsolutePath();
        Files.createDirectories(root);
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("FILE_REQUIRED", "Please attach a file");
        }
        String ext = ALLOWED.get(file.getContentType());
        if (ext == null) {
            throw ApiException.badRequest("FILE_TYPE", "Only JPG, PNG, WEBP, GIF or PDF files are allowed");
        }
        String name = UUID.randomUUID() + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, root.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save file", e);
        }
        return "/uploads/" + name;
    }
}
