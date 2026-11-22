package com.propio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestEmailDto {

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Please provide a valid email address")
    private String recipient;

    @NotBlank(message = "Subject line is required")
    private String subject;

    private String message;
}
