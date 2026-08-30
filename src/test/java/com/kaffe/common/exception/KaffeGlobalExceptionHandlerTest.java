package com.kaffe.common.exception;

import com.kaffe.common.web.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class KaffeGlobalExceptionHandlerTest {

    private final KaffeGlobalExceptionHandler handler = new KaffeGlobalExceptionHandler();

    @Test
    void mapsProviderUnavailabilityWithoutLeakingAnInternalFailure() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleServiceUnavailable(
                new ServiceUnavailableException("La función todavía no está disponible"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void mapsRateGuardsToTooManyRequests() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleTooManyRequests(
                new TooManyRequestsException("Espera un momento antes de volver a intentar"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo("TOO_MANY_REQUESTS");
    }
}
