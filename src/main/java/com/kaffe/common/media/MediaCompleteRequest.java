package com.kaffe.common.media;

public record MediaCompleteRequest(
        Long sizeBytes,
        String checksumSha256
) {
}
