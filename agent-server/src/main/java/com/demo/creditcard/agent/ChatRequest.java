package com.demo.creditcard.agent;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(@NotBlank String message) {
}
