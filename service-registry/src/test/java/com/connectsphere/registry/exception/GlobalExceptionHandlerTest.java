package com.connectsphere.registry.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private IllegalArgumentException illegalArgumentException;

    @Mock
    private RuntimeException runtimeException;

    @Test
    void handleIllegalArgumentException_returns400() {
        when(illegalArgumentException.getMessage()).thenReturn("Invalid service metadata");

        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgumentException(illegalArgumentException);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Invalid service metadata", response.getBody().get("message"));
    }

    @Test
    void handleGenericException_returns500() {
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(runtimeException);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Unexpected registry error", response.getBody().get("message"));
    }
}
