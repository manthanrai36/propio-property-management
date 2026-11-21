package com.propio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyFormDto {

    private Long id;

    @NotBlank(message = "Property name is required")
    @Size(min = 2, max = 150, message = "Name must be between 2 and 150 characters")
    private String name;

    @NotBlank(message = "Address is required")
    @Size(max = 255, message = "Address must not exceed 255 characters")
    private String address;

    @NotBlank(message = "City is required")
    @Size(max = 80, message = "City must not exceed 80 characters")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 50, message = "State must not exceed 50 characters")
    private String state;

    @NotBlank(message = "ZIP Code is required")
    @Size(max = 20, message = "ZIP Code must not exceed 20 characters")
    private String zipCode;

    @NotBlank(message = "Property type is required")
    @Size(max = 50, message = "Property type must not exceed 50 characters")
    private String propertyType;

    private String description;

    private String existingImageUrl;

    private MultipartFile imageFile;
}
