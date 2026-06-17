# Kaffe MSA Common

Shared infrastructure classes for Kaffe Spring Boot microservices.

## Current Scope

- JWT token generation and validation with one shared signing strategy.
- Bearer token resolution from `Authorization: Bearer <token>` and `KAFFE_AUTH` cookie.
- Reusable JWT authentication filter.
- Shared `ApiResponse`.
- Base exceptions.

## Local JWT Rule

All local MSAs must use the same secret:

```properties
kaffe.jwt.secret=kaffe-local-dev-secret-change-me
kaffe.jwt.cookie-name=${JWT_COOKIE_NAME:KAFFE_AUTH}
```

Production must provide:

```properties
kaffe.jwt.secret=${JWT_SECRET}
```

## Database Rule

Services consume the shared datasource auto-configuration through:

```properties
kaffe.datasource.url=${DATABASE_URL}
kaffe.datasource.username=${DB_USER}
kaffe.datasource.password=${DB_PASSWORD}
```

The auth schema standard is:

```properties
kaffe.auth.schema=${KAFFE_AUTH_SCHEMA:kaffe_auth}
```

## Maven Usage

Install locally:

```bash
mvn install
```

Then consume from an MSA:

```xml
<dependency>
    <groupId>com.kaffe</groupId>
    <artifactId>kaffe-msa-common</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```
