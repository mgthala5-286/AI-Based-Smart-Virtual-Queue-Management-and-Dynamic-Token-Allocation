package com.smartqueue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCounterRequest {

    @Positive(message = "counterNumber must be greater than 0")
    private int counterNumber;

    @NotBlank(message = "serviceId is required")
    private String serviceId;
}
