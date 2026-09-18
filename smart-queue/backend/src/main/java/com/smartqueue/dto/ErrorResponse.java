package com.smartqueue.dto;

import java.util.Map;

public record ErrorResponse(String message, Map<String, String> errors) {
}
