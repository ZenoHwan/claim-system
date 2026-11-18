package com.claims.service.claim;

import com.claims.service.identity.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/claims/{claimId}/documents")
class DocumentController {

    private final DocumentService service;
    private final ClaimViews views;

    DocumentController(DocumentService service, ClaimViews views) {
        this.service = service;
        this.views = views;
    }

    /** Claimants only (POST under /api/claims is restricted in SecurityConfig). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ClaimViews.DocumentView upload(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) UUID informationRequestId) {
        return views.document(service.upload(CurrentUser.from(jwt), claimId, informationRequestId, file));
    }

    @GetMapping("/{documentId}/content")
    ResponseEntity<Resource> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID claimId, @PathVariable UUID documentId) {
        DocumentService.Download download = service.download(CurrentUser.from(jwt), claimId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.document().contentType))
                // "attachment" so a browser never renders an uploaded file inside our origin
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.document().fileName, StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(download.content());
    }
}
