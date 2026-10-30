package io.github.nicklesho.ledger.controller;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;

/** Builds RFC 9457 problem responses with stable, machine-readable type URIs. */
final class ApiProblems {

    /** 422 Unprocessable Content (RFC 9110): well-formed request that breaks a business rule. */
    static final HttpStatusCode UNPROCESSABLE = HttpStatusCode.valueOf(422);

    private static final String TYPE_BASE = "https://nicklesho.github.io/ledger-api/problems/";

    private ApiProblems() {
    }

    static ProblemDetail create(HttpStatusCode status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + type));
        problem.setTitle(title);
        return problem;
    }
}