package com.kaffe.common.media;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record PresignedMediaUpload(
        UUID assetId,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        String publicUrl,
        OffsetDateTime expiresAt,
        long maxUploadBytes,
        String contentType
) {
}
