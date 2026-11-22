package com.propio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class UpdateProfileDto {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Size(max = 30, message = "Phone number cannot exceed 30 characters")
    private String phone;

    private MultipartFile avatar;

    private String themePreference;

    private boolean notifyRentDue;
    private boolean notifyPaymentReceived;
    private boolean notifyMaintenance;
    private boolean notifyLeaseExpiry;
}
