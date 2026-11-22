package com.propio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupportMessageDto {

    @NotBlank(message = "Your name is required")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Your email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Please select an inquiry category")
    private String category;

    @NotBlank(message = "Subject is required")
    @Size(max = 150)
    private String subject;

    @NotBlank(message = "Message details are required")
    @Size(min = 10, max = 2000, message = "Message must be between 10 and 2000 characters")
    private String message;
}
