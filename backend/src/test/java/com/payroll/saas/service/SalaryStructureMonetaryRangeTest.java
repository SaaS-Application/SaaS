package com.payroll.saas.service;

import com.payroll.saas.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalaryStructureMonetaryRangeTest {

    @Test
    void acceptsPositiveAndNegativeDecimalFourteenScaleTwoBoundaries() {
        assertThat(SalaryStructureService.validateMonetaryAmount(
                new BigDecimal("999999999999.99"), "Gross earnings"))
                .isEqualByComparingTo("999999999999.99");
        assertThat(SalaryStructureService.validateMonetaryAmount(
                new BigDecimal("-999999999999.99"), "Net salary"))
                .isEqualByComparingTo("-999999999999.99");
    }

    @Test
    void rejectsPositiveAmountBeyondDecimalFourteenScaleTwoBoundary() {
        assertThatThrownBy(() -> SalaryStructureService.validateMonetaryAmount(
                new BigDecimal("1000000000000.00"), "Gross earnings"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Gross earnings")
                .hasMessageContaining("999999999999.99");
    }

    @Test
    void rejectsNegativeNetSalaryBeyondDecimalFourteenScaleTwoBoundary() {
        assertThatThrownBy(() -> SalaryStructureService.validateMonetaryAmount(
                new BigDecimal("-1000000000000.00"), "Net salary"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Net salary")
                .hasMessageContaining("-999999999999.99");
    }

    @Test
    void rejectsCurrencyPrecisionThatWouldRequireRounding() {
        assertThatThrownBy(() -> SalaryStructureService.validateMonetaryAmount(
                new BigDecimal("1.001"), "Net salary"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("no more than two decimal places");
    }
}
