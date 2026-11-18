package com.claims.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claims.service.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthIntegrationTest extends IntegrationTest {

    @Test
    void claimantSignsInAtTheClaimantDoor() throws Exception {
        login(AISHA, PASSWORD, "CLAIMANT")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("CLAIMANT"))
                .andExpect(jsonPath("$.market").value("MY"))
                .andExpect(jsonPath("$.name").value("Aisha Rahman"));
    }

    @Test
    void claimantIsRefusedAtTheStaffDoor() throws Exception {
        login(AISHA, PASSWORD, "STAFF")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void staffAreRefusedAtTheClaimantDoor() throws Exception {
        login(OFFICER_1, PASSWORD, "CLAIMANT").andExpect(status().isUnauthorized());
        login(MANAGER, PASSWORD, "CLAIMANT").andExpect(status().isUnauthorized());
    }

    @Test
    void officerAndManagerSignInAtTheStaffDoor() throws Exception {
        login(OFFICER_1, PASSWORD, "STAFF").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("OFFICER"));
        login(MANAGER, PASSWORD, "STAFF").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void wrongPasswordAndUnknownAccountLookTheSame() throws Exception {
        String wrongPassword = login(AISHA, "not-it", "CLAIMANT")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownAccount = login("nobody@claims.test", "not-it", "CLAIMANT")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownAccount);
    }

    @Test
    void passwordsAreStoredHashed() {
        assertThat(jdbc.queryForList("select password_hash from app_user", String.class))
                .isNotEmpty()
                .allSatisfy(hash -> assertThat(hash).startsWith("$2").doesNotContain(PASSWORD));
    }

    @Test
    void seededClaimantHasAMotorAndAPropertyPolicy() throws Exception {
        mvc.perform(get("/api/policies").header(HttpHeaders.AUTHORIZATION, bearer(AISHA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.lineOfBusiness=='MOTOR')]").exists())
                .andExpect(jsonPath("$[?(@.lineOfBusiness=='PROPERTY')]").exists());
    }

    @Test
    void protectedEndpointNeedsAToken() throws Exception {
        mvc.perform(get("/api/policies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/staff/claims/unassigned")).andExpect(status().isUnauthorized());
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = bearer(AISHA);
        String tampered = token.substring(0, token.length() - 3) + (token.endsWith("AAA") ? "BBB" : "AAA");

        mvc.perform(get("/api/policies").header(HttpHeaders.AUTHORIZATION, tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void claimantCannotCallStaffOperations() throws Exception {
        mvc.perform(get("/api/staff/claims/unassigned").header(HttpHeaders.AUTHORIZATION, bearer(AISHA)))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotCallClaimantOnlyOperations() throws Exception {
        mvc.perform(get("/api/policies").header(HttpHeaders.AUTHORIZATION, bearer(OFFICER_1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationFailureListsEachInvalidField() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='door')]").exists());
    }
}
