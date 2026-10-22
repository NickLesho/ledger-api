package io.github.nicklesho.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String owner;

    // Postgres reports CHAR(3) as "bpchar"; without this, ddl-auto=validate fails.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    private Long version;

    protected Account() {
        // required by JPA
    }

    public Account(String owner, String currency, BigDecimal openingBalance) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.currency = Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(openingBalance, "openingBalance");
        if (openingBalance.signum() < 0) {
            throw new IllegalArgumentException("Opening balance must not be negative");
        }
        this.balance = openingBalance;
    }

    public boolean hasSufficientFunds(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (!hasSufficientFunds(amount)) {
            throw new IllegalStateException("Insufficient funds in account " + id);
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    public UUID getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Long getVersion() {
        return version;
    }
}