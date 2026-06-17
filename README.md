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
jwt.secret=kaffe-local-dev-secret-change-me
jwt.cookie-name=${JWT_COOKIE_NAME:KAFFE_AUTH}
```

Production must provide:

```properties
jwt.secret=${JWT_SECRET}
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

