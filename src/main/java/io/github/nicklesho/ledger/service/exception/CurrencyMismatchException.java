package io.github.nicklesho.ledger.service.exception;

public class CurrencyMismatchException extends LedgerException {

    public CurrencyMismatchException(String fromCurrency, String toCurrency) {
        super("Cannot transfer between accounts with different currencies: "
                + fromCurrency + " and " + toCurrency);
    }
}