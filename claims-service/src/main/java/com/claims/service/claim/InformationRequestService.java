package com.claims.service.claim;

import com.claims.events.ClaimEventType;
import com.claims.service.common.ApiException;
import com.claims.service.identity.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff pause a claim to ask the claimant something; the claimant's answer resumes it. */
@Service
@Transactional
class InformationRequestService {

    record AskRequest(@NotBlank @Size(max = 2000) String question) {}

    record AnswerRequest(@NotBlank @Size(max = 4000) String responseText) {}

    private final ClaimAccess access;
    private final ClaimChanges changes;
    private final ClaimRecords.InformationRequests requests;

    InformationRequestService(ClaimAccess access, ClaimChanges changes, ClaimRecords.InformationRequests requests) {
        this.access = access;
        this.changes = changes;
        this.requests = requests;
    }

    void ask(CurrentUser staff, UUID claimId, AskRequest request) {
        Claim claim = access.assignedTo(staff, claimId);
        ClaimStatus from = claim.getStatus();
        Instant now = Instant.now();
        // The state machine guarantees at most one open request: INFO_REQUESTED cannot request again.
        claim.requestInformation(now);
        changes.record(claim, from, staff.id(), request.question(), ClaimEventType.INFORMATION_REQUESTED);
        requests.save(new InformationRequest(claimId, staff.id(), request.question(), now));
    }

    void answer(CurrentUser claimant, UUID claimId, UUID requestId, AnswerRequest request) {
        Claim claim = access.visibleTo(claimant, claimId);
        InformationRequest informationRequest = requests.findById(requestId)
                .filter(found -> found.claimId.equals(claimId))
                .orElseThrow(() -> ApiException.notFound("Information request"));
        Instant now = Instant.now();
        informationRequest.answer(request.responseText(), now);
        ClaimStatus from = claim.getStatus();
        claim.informationProvided(now);
        changes.record(claim, from, claimant.id(), null, ClaimEventType.INFORMATION_PROVIDED);
    }
}
