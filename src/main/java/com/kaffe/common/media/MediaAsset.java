package com.kaffe.common.media;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record MediaAsset(
        UUID assetId,
        Long tenantId,
        Long locationId,
        String ownerService,
        String purpose,
        String entityType,
        Long entityId,
        String bucket,
        String objectKey,
        String publicUrl,
        String originalFilename,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String status,
        Long createdByUserId,
        OffsetDateTime uploadExpiresAt,
        OffsetDateTime completedAt,
        Map<String, Object> metadata
) {
}
