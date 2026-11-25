package com.joedayz.springai.m08;

import java.io.IOException;
import java.util.Set;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validación de archivos subidos. No confíes en el Content-Type que envía el cliente:
 * se comprueban los primeros bytes (firma del formato).
 */
final class Uploads {

    static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private Uploads() {
    }

    record Image(MimeType mimeType, Resource resource) { }

    static Image image(MultipartFile file) throws IOException {
        byte[] bytes = nonEmpty(file);
        String detected = detectImage(bytes);
        if (detected == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Solo PNG, JPEG, WEBP o GIF");
        }
        return new Image(MimeType.valueOf(detected), named(bytes, file.getOriginalFilename()));
    }

    /** El nombre de archivo importa: la API de transcripción deduce el formato por la extensión. */
    static Resource audio(MultipartFile file) throws IOException {
        byte[] bytes = nonEmpty(file);
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.matches(".+\\.(mp3|wav|m4a|webm|ogg|flac|mp4|mpeg|mpga)")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de audio no soportado");
        }
        return named(bytes, name);
    }

    private static byte[] nonEmpty(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Archivo vacío");
        }
        return file.getBytes();
    }

    static String detectImage(byte[] b) {
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        if (b.length > 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "image/gif";
        }
        return null;
    }

    private static Resource named(byte[] bytes, String filename) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
