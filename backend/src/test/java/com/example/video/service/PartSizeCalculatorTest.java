package com.example.video.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PartSizeCalculatorTest {

    private final PartSizeCalculator calculator = new PartSizeCalculator(8L * 1024 * 1024, 5L * 1024 * 1024, 10_000);

    @Test
    void usesDefaultForSmallFile() {
        long partSize = calculator.partSizeFor(20L * 1024 * 1024);
        assertThat(partSize).isEqualTo(8L * 1024 * 1024);
        assertThat(calculator.totalParts(20L * 1024 * 1024, partSize)).isEqualTo(3);
    }

    @Test
    void growsPartSizeToRespectMaxParts() {
        long total = 100L * 1024 * 1024 * 1024;
        long partSize = calculator.partSizeFor(total);
        assertThat(calculator.totalParts(total, partSize)).isLessThanOrEqualTo(10_000);
        assertThat(partSize % (1024 * 1024)).isZero();
    }

    @Test
    void neverBelowMinimumPartSize() {
        assertThat(calculator.partSizeFor(1)).isGreaterThanOrEqualTo(5L * 1024 * 1024);
    }

    @Test
    void singlePartForEmptyFile() {
        assertThat(calculator.totalParts(0, 8L * 1024 * 1024)).isEqualTo(1);
    }
}
