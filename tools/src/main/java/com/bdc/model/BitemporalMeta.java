package com.bdc.model;

import java.time.Instant;

/**
 * Bitemporal metadata for tracking when calendar artifacts were generated.
 *
 * Bitemporality refers to tracking two time dimensions:
 * - Transaction Time: When we recorded/knew this information
 * - Valid Time: When this information is true in the real world
 *
 * This enables:
 * - Reproducibility: "What did we think the calendar looked like on date X?"
 * - Auditing: "When did we first know about holiday Y?"
 * - Corrections: Add new data without losing history
 */
public record BitemporalMeta(
    /**
     * When this artifact was recorded/generated.
     */
    Instant transactionTime,

    /**
     * Git SHA or version of source YAML files.
     */
    String sourceVersion,

    /**
     * Version of the bdc tool that generated this artifact.
     */
    String toolVersion,

    /**
     * User or system that generated this artifact.
     */
    String generatedBy
) {
    /**
     * Creates metadata for the current transaction.
     */
    public static BitemporalMeta now(String sourceVersion, String toolVersion, String generatedBy) {
        return new BitemporalMeta(Instant.now(), sourceVersion, toolVersion, generatedBy);
    }

    /**
     * Creates metadata for the current transaction with default values.
     */
    public static BitemporalMeta now() {
        return now(
            System.getenv().getOrDefault("BDC_SOURCE_VERSION", "unknown"),
            getToolVersion(),
            System.getProperty("user.name", "unknown")
        );
    }

    /**
     * Gets the current tool version from build info or defaults to "dev".
     */
    private static String getToolVersion() {
        // In a real build, this would be injected by the build system
        String version = BitemporalMeta.class.getPackage().getImplementationVersion();
        return version != null ? version : "1.0.0-SNAPSHOT";
    }
}
