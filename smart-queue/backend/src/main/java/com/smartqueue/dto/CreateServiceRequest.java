package com.smartqueue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateServiceRequest {

    @NotBlank(message = "name is required")
    @Size(min = 2, message = "name must contain at least 2 characters")
    private String name;

    private String description;

    @Positive(message = "averageServiceTime must be greater than 0")
    private int averageServiceTime;
}
