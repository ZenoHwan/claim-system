package com.claims.service.claim;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Where uploaded bytes live. Keys are built only from server-generated UUIDs, so nothing a user
 * sends can influence the path.
 */
// ponytail: local folder, single instance only. Swap this class for an S3 bucket to run more than one.
@Component
class DocumentStore {

    private final Path root;

    DocumentStore(@Value("${claims.documents.dir}") Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    /** @return the storage key to keep in the database */
    String save(UUID claimId, UUID documentId, InputStream content) {
        String key = claimId + "/" + documentId;
        try {
            Path target = root.resolve(key);
            Files.createDirectories(target.getParent());
            Files.copy(content, target);
            return key;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Resource load(String storageKey) {
        return new FileSystemResource(root.resolve(storageKey));
    }
}
