package io.github.nicklesho.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transfer")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "from_account", nullable = false, updatable = false)
    private UUID fromAccountId;

    @Column(name = "to_account", nullable = false, updatable = false)
    private UUID toAccountId;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private TransferStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transfer() {
        // required by JPA
    }

    private Transfer(String idempotencyKey, UUID fromAccountId, UUID toAccountId,
                     BigDecimal amount, TransferStatus status) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        this.fromAccountId = Objects.requireNonNull(fromAccountId, "fromAccountId");
        this.toAccountId = Objects.requireNonNull(toAccountId, "toAccountId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.status = Objects.requireNonNull(status, "status");
        // Postgres stores microseconds; truncate so the in-memory value matches what's persisted.
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public static Transfer completed(String idempotencyKey, UUID fromAccountId,
                                     UUID toAccountId, BigDecimal amount) {
        return new Transfer(idempotencyKey, fromAccountId, toAccountId, amount, TransferStatus.COMPLETED);
    }

    public static Transfer rejected(String idempotencyKey, UUID fromAccountId,
                                    UUID toAccountId, BigDecimal amount) {
        return new Transfer(idempotencyKey, fromAccountId, toAccountId, amount, TransferStatus.REJECTED);
    }

    /** True if this transfer was created from the same request parameters. */
    public boolean matchesRequest(UUID fromAccountId, UUID toAccountId, BigDecimal amount) {
        return this.fromAccountId.equals(fromAccountId)
                && this.toAccountId.equals(toAccountId)
                && this.amount.compareTo(amount) == 0;
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}