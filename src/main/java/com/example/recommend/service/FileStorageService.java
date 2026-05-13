package com.example.recommend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@Service
public class FileStorageService {
    private static final Set<String> ALLOWED_EXT = Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg");

    private final Path uploadRoot;

    public FileStorageService(@Value("${recommend.upload-dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public StoredFile storeItemImage(Long itemId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
        }
        String ext = fileExtension(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "only jpg/jpeg/png/webp/gif/svg are allowed");
        }
        String filename = "item-" + itemId + "-" + Instant.now().toEpochMilli() + ext;
        Path dir = uploadRoot.resolve("items");
        Path target = dir.resolve(filename).normalize();
        if (!target.startsWith(dir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid file path");
        }
        try {
            Files.createDirectories(dir);
            file.transferTo(target.toFile());
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "failed to store file");
        }
        return new StoredFile("/uploads/items/" + filename, target);
    }

    private String fileExtension(String filename) {
        if (filename == null || filename.isBlank() || !filename.contains(".")) {
            return "";
        }
        String ext = filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        return ext.length() > 10 ? "" : ext;
    }

    public record StoredFile(String url, Path path) {
    }
}
