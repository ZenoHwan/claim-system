package com.claims.service.claim;

import com.claims.service.common.ApiException;
import com.claims.service.identity.CurrentUser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Evidence files on a claim. Uploading is a trust boundary: nothing the client declares is believed. */
@Service
@Transactional
class DocumentService {

    static final long MAX_BYTES = 10 * 1024 * 1024;

    record Download(ClaimDocument document, Resource content) {}

    private final ClaimAccess access;
    private final ClaimRecords.Documents documents;
    private final ClaimRecords.InformationRequests informationRequests;
    private final DocumentStore store;

    DocumentService(
            ClaimAccess access, ClaimRecords.Documents documents,
            ClaimRecords.InformationRequests informationRequests, DocumentStore store) {
        this.access = access;
        this.documents = documents;
        this.informationRequests = informationRequests;
        this.store = store;
    }

    ClaimDocument upload(CurrentUser claimant, UUID claimId, UUID informationRequestId, MultipartFile file) {
        Claim claim = access.visibleTo(claimant, claimId);
        if (!claim.getStatus().isOpen()) {
            throw ApiException.conflict("Documents cannot be added to a claim that is " + claim.getStatus());
        }
        if (informationRequestId != null) {
            informationRequests.findById(informationRequestId)
                    .filter(request -> request.claimId.equals(claimId))
                    .orElseThrow(() -> ApiException.notFound("Information request"));
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.tooLarge("A document can be at most 10 MB");
        }

        UUID documentId = UUID.randomUUID();
        try (InputStream content = file.getInputStream()) {
            String contentType = sniff(content.readNBytes(8));
            String storageKey;
            try (InputStream whole = file.getInputStream()) {
                storageKey = store.save(claimId, documentId, whole);
            }
            return documents.save(new ClaimDocument(
                    documentId, claimId, informationRequestId, claimant.id(), displayName(file.getOriginalFilename()),
                    contentType, file.getSize(), storageKey, Instant.now()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The type is read from the file's leading bytes; the declared content type and extension are ignored. */
    private static String sniff(byte[] head) {
        if (startsWith(head, '%', 'P', 'D', 'F', '-')) {
            return "application/pdf";
        }
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        throw ApiException.invalid("file", "Only PDF, JPEG and PNG files are accepted");
    }

    private static boolean startsWith(byte[] head, int... signature) {
        if (head.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((head[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }

    /** Shown back to users and used in the download header only, never as a path. */
    private static String displayName(String original) {
        String name = original == null ? "" : Path.of(original.replace('\\', '/')).getFileName().toString();
        name = name.replaceAll("[\\p{Cntrl}\"]", "").strip();
        if (name.isEmpty()) {
            return "document";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    @Transactional(readOnly = true)
    Download download(CurrentUser user, UUID claimId, UUID documentId) {
        access.visibleTo(user, claimId);
        ClaimDocument document = documents.findById(documentId)
                .filter(found -> found.claimId.equals(claimId))
                .orElseThrow(() -> ApiException.notFound("Document"));
        return new Download(document, store.load(document.storageKey));
    }
}
