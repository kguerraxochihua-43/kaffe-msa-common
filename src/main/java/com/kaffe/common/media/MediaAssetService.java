package com.kaffe.common.media;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaffe.common.config.KaffeMediaProperties;
import com.kaffe.common.exception.BadRequestException;
import com.kaffe.common.exception.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class MediaAssetService {

    private static final Pattern SAFE_EXTENSION = Pattern.compile("^[a-z0-9]{2,8}$");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final KaffeMediaProperties properties;
    private final S3Presigner s3Presigner;
    private final S3Client s3Client;

    public MediaAssetService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            KaffeMediaProperties properties,
            S3Presigner s3Presigner,
            S3Client s3Client
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.s3Presigner = s3Presigner;
        this.s3Client = s3Client;
    }

    public PresignedMediaUpload createPresignedUpload(MediaUploadPolicy policy) {
        ensureEnabled();
        validatePolicy(policy);

        UUID assetId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(properties.getUploadTtl());
        String bucket = requireBucket();
        String objectKey = objectKey(policy, assetId);
        String publicUrl = publicUrl(bucket, objectKey);

        jdbcTemplate.update("""
                        insert into media.assets (
                            asset_id, tenant_id, location_id, owner_service, purpose, entity_type, entity_id,
                            bucket, object_key, public_url, original_filename, content_type, size_bytes,
                            checksum_sha256, status, created_by_user_id, upload_expires_at, metadata
                        )
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'upload_pending', ?, ?, ?::jsonb)
                        """,
                assetId,
                policy.tenantId(),
                policy.locationId(),
                policy.ownerService(),
                policy.purpose(),
                policy.entityType(),
                policy.entityId(),
                bucket,
                objectKey,
                publicUrl,
                trimToNull(policy.fileName()),
                normalizeContentType(policy.contentType()),
                policy.sizeBytes(),
                trimToNull(policy.checksumSha256()),
                policy.createdByUserId(),
                expiresAt,
                toJson(policy.metadata())
        );

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .contentType(normalizeContentType(policy.contentType()))
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.getUploadTtl())
                .putObjectRequest(putObjectRequest)
                .build();

        String uploadUrl = s3Presigner.presignPutObject(presignRequest).url().toString();
        return new PresignedMediaUpload(
                assetId,
                uploadUrl,
                "PUT",
                Map.of("Content-Type", normalizeContentType(policy.contentType())),
                publicUrl,
                expiresAt,
                properties.getMaxUploadBytes(),
                normalizeContentType(policy.contentType())
        );
    }

    public MediaAsset completeUpload(UUID assetId, Long tenantId, Long userId, MediaCompleteRequest request) {
        ensureEnabled();
        MediaAsset asset = findAsset(assetId, tenantId);
        if (!"upload_pending".equals(asset.status())) {
            throw new BadRequestException("Media asset is not pending upload");
        }
        if (asset.uploadExpiresAt() != null && asset.uploadExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Media upload request has expired");
        }
        if (asset.createdByUserId() != null && userId != null && !asset.createdByUserId().equals(userId)) {
            throw new BadRequestException("Media asset was created by a different user");
        }

        HeadObjectResponse head = headObject(asset);
        long actualSize = head.contentLength() == null ? asset.sizeBytes() : head.contentLength();
        if (actualSize > properties.getMaxUploadBytes()) {
            throw new BadRequestException("Uploaded file exceeds maximum allowed size");
        }
        if (request != null && request.sizeBytes() != null && request.sizeBytes() > 0 && request.sizeBytes() != actualSize) {
            throw new BadRequestException("Uploaded file size does not match the requested size");
        }
        String actualContentType = normalizeContentType(head.contentType());
        if (actualContentType != null && !actualContentType.equals(asset.contentType())) {
            throw new BadRequestException("Uploaded file content type does not match the requested type");
        }

        jdbcTemplate.update("""
                        update media.assets
                        set status = 'completed',
                            size_bytes = ?,
                            checksum_sha256 = coalesce(?, checksum_sha256),
                            completed_at = now(),
                            updated_at = now()
                        where asset_id = ?
                          and tenant_id = ?
                        """,
                actualSize,
                request == null ? null : trimToNull(request.checksumSha256()),
                assetId,
                tenantId
        );
        return findAsset(assetId, tenantId);
    }

    public MediaAsset findAsset(UUID assetId, Long tenantId) {
        try {
            return jdbcTemplate.queryForObject("""
                            select *
                            from media.assets
                            where asset_id = ?
                              and tenant_id = ?
                            """,
                    (rs, rowNum) -> mapAsset(rs),
                    assetId,
                    tenantId
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException("Media asset was not found");
        }
    }

    private HeadObjectResponse headObject(MediaAsset asset) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(asset.bucket())
                    .key(asset.objectKey())
                    .build());
        } catch (SdkException ex) {
            throw new BadRequestException("Uploaded object was not found in storage");
        }
    }

    private void validatePolicy(MediaUploadPolicy policy) {
        if (policy == null) {
            throw new BadRequestException("Media upload request is required");
        }
        if (policy.tenantId() == null) {
            throw new BadRequestException("Tenant id is required for media upload");
        }
        if (isBlank(policy.ownerService()) || isBlank(policy.purpose())) {
            throw new BadRequestException("Media owner service and purpose are required");
        }
        if (policy.sizeBytes() <= 0) {
            throw new BadRequestException("Media size must be greater than zero");
        }
        if (policy.sizeBytes() > properties.getMaxUploadBytes()) {
            throw new BadRequestException("Media size exceeds maximum allowed size");
        }
        String contentType = normalizeContentType(policy.contentType());
        if (!allowedContentTypes().contains(contentType)) {
            throw new BadRequestException("Media content type is not allowed");
        }
        if (isBlank(policy.keyPrefix())) {
            throw new BadRequestException("Media key prefix is required");
        }
    }

    private Set<String> allowedContentTypes() {
        java.util.LinkedHashSet<String> allowed = new java.util.LinkedHashSet<>();
        for (String contentType : properties.getAllowedContentTypes()) {
            String normalized = normalizeContentType(contentType);
            if (normalized != null) {
                allowed.add(normalized);
            }
        }
        if (properties.isAllowSvg()) {
            allowed.add("image/svg+xml");
        }
        return allowed;
    }

    private String objectKey(MediaUploadPolicy policy, UUID assetId) {
        String extension = extension(policy.fileName(), policy.contentType());
        return joinKey(properties.getS3().getKeyPrefix(), policy.keyPrefix(), assetId + "." + extension);
    }

    private String extension(String fileName, String contentType) {
        String extension = null;
        String cleaned = trimToNull(fileName);
        if (cleaned != null) {
            int dot = cleaned.lastIndexOf('.');
            if (dot >= 0 && dot < cleaned.length() - 1) {
                extension = cleaned.substring(dot + 1).toLowerCase(Locale.ROOT);
            }
        }
        if (extension == null || !SAFE_EXTENSION.matcher(extension).matches()) {
            extension = switch (normalizeContentType(contentType)) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                case "image/avif" -> "avif";
                case "image/svg+xml" -> "svg";
                default -> "bin";
            };
        }
        if ("jpeg".equals(extension)) {
            return "jpg";
        }
        return extension;
    }

    private String joinKey(String... parts) {
        StringBuilder key = new StringBuilder();
        for (String part : parts) {
            String value = trimSlashes(part);
            if (value == null) {
                continue;
            }
            if (!key.isEmpty()) {
                key.append('/');
            }
            key.append(value);
        }
        return key.toString();
    }

    private String publicUrl(String bucket, String objectKey) {
        String baseUrl = trimTrailingSlash(properties.getS3().getPublicBaseUrl());
        String encodedKey = encodeKey(objectKey);
        if (baseUrl != null) {
            return baseUrl + "/" + encodedKey;
        }
        return "https://" + bucket + ".s3.amazonaws.com/" + encodedKey;
    }

    private String encodeKey(String objectKey) {
        String[] parts = objectKey.split("/");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = URLEncoder.encode(parts[i], StandardCharsets.UTF_8).replace("+", "%20");
        }
        return String.join("/", parts);
    }

    private MediaAsset mapAsset(ResultSet rs) throws SQLException {
        return new MediaAsset(
                rs.getObject("asset_id", UUID.class),
                rs.getObject("tenant_id", Long.class),
                rs.getObject("location_id", Long.class),
                rs.getString("owner_service"),
                rs.getString("purpose"),
                rs.getString("entity_type"),
                rs.getObject("entity_id", Long.class),
                rs.getString("bucket"),
                rs.getString("object_key"),
                rs.getString("public_url"),
                rs.getString("original_filename"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("checksum_sha256"),
                rs.getString("status"),
                rs.getObject("created_by_user_id", Long.class),
                rs.getObject("upload_expires_at", OffsetDateTime.class),
                rs.getObject("completed_at", OffsetDateTime.class),
                fromJson(rs.getString("metadata"))
        );
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new BadRequestException("Media storage is disabled");
        }
    }

    private String requireBucket() {
        String bucket = trimToNull(properties.getS3().getBucket());
        if (bucket == null) {
            throw new BadRequestException("Media storage bucket is not configured");
        }
        return bucket;
    }

    private String normalizeContentType(String contentType) {
        String normalized = trimToNull(contentType);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private String toJson(Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? Map.of() : metadata);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("Invalid media metadata");
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimSlashes(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private String trimTrailingSlash(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.replaceAll("/+$", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
