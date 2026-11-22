package com.propio.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceCommentDto {

    @NotBlank(message = "Comment text cannot be blank")
    private String content;
}
