package com.demo.creditcard.mcp.supervisor;

import jakarta.validation.constraints.NotBlank;

public record ReviewRequest(@NotBlank String supervisorId) {
}
