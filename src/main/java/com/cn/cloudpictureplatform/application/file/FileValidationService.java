package com.cn.cloudpictureplatform.application.file;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class FileValidationService {

    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
        "image/jpeg", Set.of("jpg", "jpeg"),
        "image/png", Set.of("png"),
        "image/gif", Set.of("gif"),
        "image/webp", Set.of("webp"),
        "image/svg+xml", Set.of("svg")
    );

    private static final Map<String, byte[][]> MAGIC_BYTES = Map.of(
        "image/jpeg", new byte[][] {
            {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
        },
        "image/png", new byte[][] {
            {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        },
        "image/gif", new byte[][] {
            {0x47, 0x49, 0x46, 0x38, 0x37, 0x61},
            {0x47, 0x49, 0x46, 0x38, 0x39, 0x61}
        },
        "image/webp", new byte[][] {
            {0x52, 0x49, 0x46, 0x46}
        }
    );

    public void validateImage(MultipartFile file) {
        String declaredType = file.getContentType();

        if (declaredType == null || !ALLOWED_TYPES.containsKey(declaredType)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST,
                "unsupported file type: " + declaredType + ". Allowed: " + ALLOWED_TYPES.keySet());
        }

        if ("image/svg+xml".equals(declaredType)) {
            return;
        }

        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = is.read(header);
            if (read < 4) {
                throw new ApiException(ApiErrorCode.BAD_REQUEST, "file too small to validate");
            }
            if (!matchesMagicBytes(declaredType, header)) {
                throw new ApiException(ApiErrorCode.BAD_REQUEST,
                    "file content does not match declared type " + declaredType);
            }
        } catch (IOException e) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "failed to read file for validation");
        }
    }

    private boolean matchesMagicBytes(String mimeType, byte[] header) {
        byte[][] signatures = MAGIC_BYTES.get(mimeType);
        if (signatures == null) return true;

        for (byte[] sig : signatures) {
            if (matchesSignature(header, sig)) {
                if ("image/webp".equals(mimeType)) {
                    if (header.length >= 12
                        && header[8] == 'W' && header[9] == 'E'
                        && header[10] == 'B' && header[11] == 'P') {
                        return true;
                    }
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    private boolean matchesSignature(byte[] header, byte[] signature) {
        if (header.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) return false;
        }
        return true;
    }
}
