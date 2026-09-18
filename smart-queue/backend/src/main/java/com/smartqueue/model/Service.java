package com.smartqueue.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Service {

    private String id;
    private String name;
    private String description;
    private int averageServiceTime;
    private boolean active;
}
