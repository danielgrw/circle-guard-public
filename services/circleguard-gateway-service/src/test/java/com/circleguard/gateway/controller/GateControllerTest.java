package com.circleguard.gateway.controller;

import com.circleguard.gateway.service.QrValidationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = GateController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
public class GateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QrValidationService validationService;

    @Test
    void shouldReturnValidationResult() throws Exception {
        String token = "mock-token";
        QrValidationService.ValidationResult mockResult =
            new QrValidationService.ValidationResult(true, "GREEN", "Welcome");

        Mockito.when(validationService.validateToken(token)).thenReturn(mockResult);

        mockMvc.perform(post("/api/v1/gate/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"mock-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.status").value("GREEN"));
    }

    @Test
    void shouldReturnInvalidWhenTokenMissing() throws Exception {
        Mockito.when(validationService.validateToken(null))
                .thenReturn(new QrValidationService.ValidationResult(false, "RED", "Invalid or Expired Token"));

        mockMvc.perform(post("/api/v1/gate/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.status").value("RED"));
    }
}
