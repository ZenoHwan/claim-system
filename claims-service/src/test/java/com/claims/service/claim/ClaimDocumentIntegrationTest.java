package com.claims.service.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claims.service.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class ClaimDocumentIntegrationTest extends IntegrationTest {

    private static final byte[] PDF = "%PDF-1.7 police report".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};

    private ResultActions upload(String email, String claimId, String fileName, String declaredType, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/claims/" + claimId + "/documents")
                .file(new MockMultipartFile("file", fileName, declaredType, bytes))
                .header(HttpHeaders.AUTHORIZATION, bearer(email)));
    }

    private String uploaded(String claimId, String fileName, byte[] bytes) throws Exception {
        return body(upload(AISHA, claimId, fileName, "application/octet-stream", bytes).andExpect(status().isCreated()))
                .get("id").asString();
    }

    @Test
    void anUploadedPhotoAppearsOnTheClaimWithNameTypeSizeAndTime() throws Exception {
        String id = submitMotorClaim("100.00");

        upload(AISHA, id, "damage.jpg", "image/jpeg", JPEG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("damage.jpg"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.sizeBytes").value(JPEG.length))
                .andExpect(jsonPath("$.uploadedAt").isNotEmpty());

        getAs("/api/claims/" + id, bearer(AISHA))
                .andExpect(jsonPath("$.documents.length()").value(1))
                .andExpect(jsonPath("$.documents[0].fileName").value("damage.jpg"));
    }

    @Test
    void aDocumentSentWithAResponseIsLinkedToThatRequest() throws Exception {
        String[] ids = claimAwaitingInformation();

        mvc.perform(multipart("/api/claims/" + ids[0] + "/documents")
                        .file(new MockMultipartFile("file", "report.pdf", "application/pdf", PDF))
                        .param("informationRequestId", ids[1])
                        .header(HttpHeaders.AUTHORIZATION, bearer(AISHA)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.informationRequestId").value(ids[1]));
    }

    @Test
    void aRequestFromAnotherClaimCannotBeLinked() throws Exception {
        String otherRequest = claimAwaitingInformation()[1];
        String id = submitMotorClaim("100.00");

        mvc.perform(multipart("/api/claims/" + id + "/documents")
                        .file(new MockMultipartFile("file", "report.pdf", "application/pdf", PDF))
                        .param("informationRequestId", otherRequest)
                        .header(HttpHeaders.AUTHORIZATION, bearer(AISHA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aClosedClaimAcceptsNoMoreDocuments() throws Exception {
        String id = assessedClaim("100.00", "90.00");
        reject(OFFICER_1, id, "Not covered");

        upload(AISHA, id, "late.pdf", "application/pdf", PDF).andExpect(status().isConflict());
    }

    @Test
    void staffCannotUpload() throws Exception {
        String id = submitMotorClaim("100.00");

        upload(OFFICER_1, id, "note.pdf", "application/pdf", PDF).andExpect(status().isForbidden());
        upload(MANAGER, id, "note.pdf", "application/pdf", PDF).andExpect(status().isForbidden());
    }

    @Test
    void anotherClaimantCannotUploadToTheClaim() throws Exception {
        String id = submitMotorClaim("100.00");

        upload(DANIEL, id, "sneaky.pdf", "application/pdf", PDF).andExpect(status().isNotFound());
    }

    @Test
    void anUnsupportedTypeIsRefusedAndNothingIsStored() throws Exception {
        String id = submitMotorClaim("100.00");

        upload(AISHA, id, "notes.txt", "text/plain", "just text".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("file"));

        assertThat(count("claim_document", id)).isZero();
    }

    @Test
    void aFileOnlyClaimingToBeAPdfIsRefused() throws Exception {
        String id = submitMotorClaim("100.00");

        upload(AISHA, id, "invoice.pdf", "application/pdf", "MZ this is an executable".getBytes(StandardCharsets.ISO_8859_1))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theTypeComesFromTheContentNotFromWhatTheClientDeclares() throws Exception {
        String id = submitMotorClaim("100.00");

        upload(AISHA, id, "scan.bin", "text/plain", PNG)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/png"));
    }

    @Test
    void aFileOverTenMegabytesIsRefusedAndNothingIsStored() throws Exception {
        String id = submitMotorClaim("100.00");
        byte[] tooBig = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(PDF, 0, tooBig, 0, PDF.length);

        upload(AISHA, id, "huge.pdf", "application/pdf", tooBig).andExpect(status().is(413));

        assertThat(count("claim_document", id)).isZero();
    }

    @Test
    void theOwnerDownloadsTheFileWithItsOriginalNameAndType() throws Exception {
        String id = submitMotorClaim("100.00");
        String documentId = uploaded(id, "report.pdf", PDF);

        getAs("/api/claims/" + id + "/documents/" + documentId + "/content", bearer(AISHA))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("report.pdf")))
                .andExpect(content().bytes(PDF));
    }

    @Test
    void staffInTheClaimsMarketCanDownloadButOtherMarketsAndClaimantsCannot() throws Exception {
        String id = submitMotorClaim("100.00");
        String url = "/api/claims/" + id + "/documents/" + uploaded(id, "report.pdf", PDF) + "/content";

        getAs(url, bearer(OFFICER_2)).andExpect(status().isOk());
        getAs(url, bearer(OFFICER_SG)).andExpect(status().isNotFound());
        getAs(url, bearer(DANIEL)).andExpect(status().isNotFound());
    }

    @Test
    void aDocumentIsOnlyReachableThroughItsOwnClaim() throws Exception {
        String documentId = uploaded(submitMotorClaim("100.00"), "report.pdf", PDF);
        String anotherClaim = submitMotorClaim("100.00");

        getAs("/api/claims/" + anotherClaim + "/documents/" + documentId + "/content", bearer(AISHA))
                .andExpect(status().isNotFound());
    }

    @Test
    void aHostileFileNameCannotEscapeTheStorageDirectory() throws Exception {
        String id = submitMotorClaim("100.00");

        upload(AISHA, id, "../../../etc/passwd.pdf", "application/pdf", PDF)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("passwd.pdf"));

        String storageKey = jdbc.queryForObject(
                "select storage_key from claim_document where claim_id = ?::uuid", String.class, id);
        assertThat(storageKey).doesNotContain("passwd").doesNotContain("..");
        try (Stream<Path> stored = Files.walk(DOCUMENTS)) {
            assertThat(stored.filter(Files::isRegularFile))
                    .allSatisfy(file -> assertThat(file.toRealPath()).startsWith(DOCUMENTS.toRealPath()))
                    .anySatisfy(file -> assertThat(DOCUMENTS.relativize(file).toString()).isEqualTo(storageKey));
        }
    }
}
