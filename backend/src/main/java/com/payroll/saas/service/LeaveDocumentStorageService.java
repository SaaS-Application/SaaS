package com.payroll.saas.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class LeaveDocumentStorageService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private final Path storageRoot;

    public LeaveDocumentStorageService(
            @Value("${app.leave-document-dir:./private/leave-documents}") String storageDirectory) {
        this.storageRoot = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Supporting document must be between 1 byte and 5 MB");
        }

        byte[] content = file.getBytes();
        String extension = validatedExtension(file.getContentType(), content);
        Files.createDirectories(storageRoot);
        String key = UUID.randomUUID() + extension;
        Path target = resolve(key);
        Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return key;
    }

    public byte[] read(String key) throws IOException {
        return Files.readAllBytes(resolve(key));
    }

    public String contentType(String key) {
        if (key.endsWith(".pdf")) return "application/pdf";
        if (key.endsWith(".png")) return "image/png";
        if (key.endsWith(".jpg")) return "image/jpeg";
        throw new IllegalArgumentException("Unsupported stored document type");
    }

    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        if (key == null || !key.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(pdf|png|jpg)")) {
            throw new IllegalArgumentException("Invalid stored document key");
        }
        Path target = storageRoot.resolve(key).normalize();
        if (!target.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid stored document key");
        }
        return target;
    }

    private String validatedExtension(String contentType, byte[] bytes) {
        if ("application/pdf".equalsIgnoreCase(contentType)
                && bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P'
                && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') {
            return ".pdf";
        }
        if ("image/png".equalsIgnoreCase(contentType)
                && bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10) {
            return ".png";
        }
        if ("image/jpeg".equalsIgnoreCase(contentType)
                && bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return ".jpg";
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Only valid PDF, PNG, or JPEG documents are accepted");
    }
}
