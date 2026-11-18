package com.claims.service.claim;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "motor_claim_detail")
class MotorClaimDetail {

    @Id
    UUID claimId;

    String vehicleRegistration;
    String driverName;
    @Embedded
    Address accidentLocation;
    boolean thirdPartyInvolved;

    protected MotorClaimDetail() {}

    MotorClaimDetail(UUID claimId, SubmitClaimRequest.Motor motor) {
        this.claimId = claimId;
        this.vehicleRegistration = motor.vehicleRegistration();
        this.driverName = motor.driverName();
        this.accidentLocation = motor.accidentLocation();
        this.thirdPartyInvolved = motor.thirdPartyInvolved();
    }
}
