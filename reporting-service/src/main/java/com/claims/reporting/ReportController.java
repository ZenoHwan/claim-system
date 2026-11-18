package com.claims.reporting;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manager-only (enforced in SecurityConfig). Workload and performance are scoped to the caller's market. */
@RestController
@RequestMapping("/api/reports")
class ReportController {

    private final ReportQueries reports;

    ReportController(ReportQueries reports) {
        this.reports = reports;
    }

    @GetMapping("/workload")
    ReportQueries.Workload workload(@AuthenticationPrincipal Jwt token) {
        return reports.workload(token.getClaimAsString("market"));
    }

    @GetMapping("/performance")
    ReportQueries.Performance performance(@AuthenticationPrincipal Jwt token) {
        return reports.performance(token.getClaimAsString("market"));
    }

    @GetMapping("/exposure")
    ReportQueries.Exposure exposure() {
        return reports.exposure();
    }
}
