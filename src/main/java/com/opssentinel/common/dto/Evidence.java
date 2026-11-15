package com.opssentinel.common.dto;

/** A single piece of retrieved evidence backing a diagnosis. */
public record Evidence(
        String source,
        String snippet,
        double similarityScore
) {}
