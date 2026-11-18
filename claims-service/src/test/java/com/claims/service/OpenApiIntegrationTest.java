package com.claims.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** The contract the frontend generates its types from. Public, and complete. */
class OpenApiIntegrationTest extends IntegrationTest {

    @Test
    void theApiDocumentIsPublicAndListsEveryOperation() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/policies'].get").exists())
                .andExpect(jsonPath("$.paths['/api/claims'].post").exists())
                .andExpect(jsonPath("$.paths['/api/claims'].get").exists())
                .andExpect(jsonPath("$.paths['/api/claims/{claimId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/claims/{claimId}/information-requests/{requestId}/response'].post").exists())
                .andExpect(jsonPath("$.paths['/api/claims/{claimId}/documents'].post").exists())
                .andExpect(jsonPath("$.paths['/api/claims/{claimId}/documents/{documentId}/content'].get").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/unassigned'].get").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/mine'].get").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{claimId}/assign'].post").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{claimId}/information-requests'].post").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{claimId}/assessment'].post").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{claimId}/decision'].post").exists())
                .andExpect(jsonPath("$.paths['/api/staff/claims/{claimId}/payment'].post").exists());
    }
}
