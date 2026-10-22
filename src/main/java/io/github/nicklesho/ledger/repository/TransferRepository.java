package io.github.nicklesho.ledger.repository;

import io.github.nicklesho.ledger.domain.Transfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

    @Query("select t from Transfer t where t.fromAccountId = :accountId or t.toAccountId = :accountId")
    Page<Transfer> findByAccountId(UUID accountId, Pageable pageable);
}