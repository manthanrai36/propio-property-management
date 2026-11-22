package com.propio.dto;

import com.propio.model.EmailStatus;
import com.propio.model.EmailType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailFilterDto {

    private String search;
    private EmailType type;
    private EmailStatus status;

    @Builder.Default
    private int page = 0;

    @Builder.Default
    private int size = 15;
}
