package com.example.order.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value Object representing a monetary amount with currency.
 */
public final class Money {

    private static final String DEFAULT_CURRENCY = "TWD";
    private static final int SCALE = 2;

    private final BigDecimal amount;
    private final String currency;

    private Money(BigDecimal amount, String currency) {
        this.amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
        this.currency = Objects.requireNonNull(currency, "Currency cannot be null");
    }

    /**
     * Creates Money with the specified amount and default currency (TWD).
     *
     * @param amount the monetary amount
     * @return new Money instance
     * @throws IllegalArgumentException if amount is negative
     */
    public static Money of(BigDecimal amount) {
        return of(amount, DEFAULT_CURRENCY);
    }

    /**
     * Creates Money with the specified amount and currency.
     *
     * @param amount   the monetary amount
     * @param currency the currency code
     * @return new Money instance
     * @throws IllegalArgumentException if amount is negative
     */
    public static Money of(BigDecimal amount, String currency) {
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative: " + amount);
        }
        return new Money(amount, currency);
    }

    /**
     * Creates Money with zero amount and default currency.
     *
     * @return new Money instance with zero amount
     */
    public static Money zero() {
        return new Money(BigDecimal.ZERO, DEFAULT_CURRENCY);
    }

    /**
     * Adds another Money to this one.
     *
     * @param other the Money to add
     * @return new Money with the sum
     * @throws IllegalArgumentException if currencies don't match
     */
    public Money add(Money other) {
        Objects.requireNonNull(other, "Cannot add null Money");
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot add Money with different currencies: " + this.currency + " vs " + other.currency);
        }
        return new Money(this.amount.add(other.amount), this.currency);
    }

    /**
     * Multiplies this Money by a quantity.
     *
     * @param quantity the multiplier
     * @return new Money with the product
     * @throws IllegalArgumentException if quantity is negative
     */
    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative: " + quantity);
        }
        return new Money(this.amount.multiply(BigDecimal.valueOf(quantity)), this.currency);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return amount.compareTo(money.amount) == 0 && Objects.equals(currency, money.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currency);
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency;
    }
}
