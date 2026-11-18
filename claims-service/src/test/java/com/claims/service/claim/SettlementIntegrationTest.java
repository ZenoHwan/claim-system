package com.claims.service.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claims.service.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class SettlementIntegrationTest extends IntegrationTest {

    @Test
    void approvalRaisesARequestedPaymentForTheApprovedAmountPayableToTheClaimant() throws Exception {
        String id = approvedClaim("7200.00");

        getAs("/api/claims/" + id, bearer(OFFICER_1))
                .andExpect(jsonPath("$.payment.status").value("REQUESTED"))
                .andExpect(jsonPath("$.payment.amount").value(7200.00))
                .andExpect(jsonPath("$.payment.currency").value("MYR"))
                .andExpect(jsonPath("$.payment.payeeName").value("Aisha Rahman"))
                .andExpect(jsonPath("$.payment.paidAt").doesNotExist());
    }

    @Test
    void rejectionRaisesNoPayment() throws Exception {
        String id = assessedClaim("100.00", "90.00");
        reject(OFFICER_1, id, "Not covered");

        getAs("/api/claims/" + id, bearer(OFFICER_1)).andExpect(jsonPath("$.payment").doesNotExist());
        assertThat(count("payment", id)).isZero();
    }

    @Test
    void theDatabaseItselfRefusesASecondPaymentForAClaim() throws Exception {
        String id = approvedClaim("7200.00");

        assertThatThrownBy(() -> jdbc.update(
                        "insert into payment (id, claim_id, payee_name, amount, currency, status, requested_at) "
                                + "values (gen_random_uuid(), ?::uuid, 'Someone Else', 1.00, 'MYR', 'REQUESTED', now())", id))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(count("payment", id)).isEqualTo(1);
    }

    @Test
    void recordingThePaymentSettlesTheClaimAndReleasesTheReserve() throws Exception {
        String id = approvedClaim("7200.00");

        recordPayment(OFFICER_1, id, "TT-2026-0001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.currentReserve").value(0))
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.method").value("BANK_TRANSFER"))
                .andExpect(jsonPath("$.payment.reference").value("TT-2026-0001"))
                .andExpect(jsonPath("$.payment.paidAt").isNotEmpty());

        assertThat(jdbc.queryForList(
                        "select event_type from outbox_event where aggregate_id = ?::uuid order by created_at", String.class, id))
                .endsWith("CLAIM_APPROVED", "CLAIM_SETTLED");
        assertThat(jdbc.queryForList(
                        "select reason from reserve where claim_id = ?::uuid order by created_at", String.class, id))
                .containsExactly("INITIAL", "ASSESSMENT", "RELEASED");
    }

    @Test
    void aMissingReferenceIsRefusedAndTheClaimStaysApproved() throws Exception {
        String id = approvedClaim("7200.00");

        postJson("/api/staff/claims/" + id + "/payment", bearer(OFFICER_1), Map.of("method", "CHEQUE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("reference"));

        getAs("/api/claims/" + id, bearer(OFFICER_1)).andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void recordingTwiceIsAConflict() throws Exception {
        String id = approvedClaim("7200.00");
        recordPayment(OFFICER_1, id, "TT-2026-0002");

        recordPayment(OFFICER_1, id, "TT-2026-0003").andExpect(status().isConflict());
    }

    @Test
    void aPaymentCannotBeRecordedBeforeApproval() throws Exception {
        String id = assessedClaim("100.00", "90.00");

        recordPayment(OFFICER_1, id, "TT-2026-0004").andExpect(status().isConflict());
    }

    @Test
    void onlyTheAssignedOfficerRecordsThePayment() throws Exception {
        String id = approvedClaim("7200.00");

        recordPayment(OFFICER_2, id, "TT-2026-0005").andExpect(status().isForbidden());
    }

    @Test
    void claimantSeesAmountStatusAndPaidDateButNotThePaymentReference() throws Exception {
        String id = approvedClaim("7200.00");
        recordPayment(OFFICER_1, id, "TT-2026-0006");

        getAs("/api/claims/" + id, bearer(AISHA))
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.decision.approvedAmount").value(7200.00))
                .andExpect(jsonPath("$.payment.status").value("PAID"))
                .andExpect(jsonPath("$.payment.paidAt").isNotEmpty())
                .andExpect(jsonPath("$.payment.reference").doesNotExist());
    }
}
