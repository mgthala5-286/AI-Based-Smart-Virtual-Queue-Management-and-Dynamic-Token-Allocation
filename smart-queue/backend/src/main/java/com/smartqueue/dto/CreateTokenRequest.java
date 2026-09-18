package com.smartqueue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTokenRequest {

    @NotBlank(message = "serviceId is required")
    private String serviceId;
}
