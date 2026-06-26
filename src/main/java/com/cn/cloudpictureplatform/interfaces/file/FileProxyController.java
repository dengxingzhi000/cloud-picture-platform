package com.cn.cloudpictureplatform.interfaces.file;

import com.cn.cloudpictureplatform.application.file.FileProxyService;
import com.cn.cloudpictureplatform.common.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileProxyController {

    private final FileProxyService fileProxyService;

    @GetMapping("/**")
    public void serveFile(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        String fullPath = request.getRequestURI();
        String prefix = "/api/files/";
        if (!fullPath.startsWith(prefix)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String storageKey = fullPath.substring(prefix.length());

        AppUserPrincipal requester = getCurrentUser();
        String resolvedKey = fileProxyService.resolveAccessibleStorageKey(storageKey, requester);

        try (InputStream is = fileProxyService.getFileStream(resolvedKey)) {
            String contentType = detectContentType(storageKey);
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "inline");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Cache-Control", "private, max-age=3600");

            OutputStream os = response.getOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
        }
    }

    private AppUserPrincipal getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal) {
            return (AppUserPrincipal) authentication.getPrincipal();
        }
        return null;
    }

    private String detectContentType(String storageKey) {
        String lower = storageKey.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
