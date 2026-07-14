package com.kaffe.common.media;

import java.util.Map;

public record MediaUploadPolicy(
        String ownerService,
        Long tenantId,
        Long locationId,
        String purpose,
        String entityType,
        Long entityId,
        Long createdByUserId,
        String keyPrefix,
        String fileName,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        Map<String, Object> metadata
) {
}
