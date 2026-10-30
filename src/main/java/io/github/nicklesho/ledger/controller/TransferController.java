package io.github.nicklesho.ledger.controller;

import io.github.nicklesho.ledger.controller.dto.TransferRequest;
import io.github.nicklesho.ledger.controller.dto.TransferResponse;
import io.github.nicklesho.ledger.domain.Transfer;
import io.github.nicklesho.ledger.domain.TransferStatus;
import io.github.nicklesho.ledger.service.TransferResult;
import io.github.nicklesho.ledger.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<Object> createTransfer(
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        TransferResult result = transferService.transfer(request.toCommand(idempotencyKey));
        Transfer transfer = result.transfer();

        // A replay returns exactly what the original request returned, plus a marker header.
        ResponseEntity.BodyBuilder response = transfer.getStatus() == TransferStatus.COMPLETED
                ? ResponseEntity.status(HttpStatus.CREATED)
                : ResponseEntity.status(ApiProblems.UNPROCESSABLE);
        if (result.replayed()) {
            response.header(REPLAYED_HEADER, "true");
        }

        Object body = transfer.getStatus() == TransferStatus.COMPLETED
                ? TransferResponse.from(transfer)
                : insufficientFunds(transfer);
        return response.body(body);
    }

    private static ProblemDetail insufficientFunds(Transfer transfer) {
        ProblemDetail problem = ApiProblems.create(ApiProblems.UNPROCESSABLE, "insufficient-funds",
                "Insufficient funds",
                "Account " + transfer.getFromAccountId() + " has insufficient funds for this transfer.");
        problem.setProperty("transfer", TransferResponse.from(transfer));
        return problem;
    }
}