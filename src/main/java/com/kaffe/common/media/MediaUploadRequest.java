package com.kaffe.common.media;

import java.util.Map;

public record MediaUploadRequest(
        String purpose,
        String fileName,
        String contentType,
        Long sizeBytes,
        String checksumSha256,
        Long locationId,
        Long entityId,
        Map<String, Object> metadata
) {
}
