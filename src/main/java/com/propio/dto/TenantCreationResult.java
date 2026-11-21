package com.propio.dto;

import com.propio.model.Tenant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Result returned upon creating a new tenant, containing the generated login credentials.
 */
@Getter
@AllArgsConstructor
public class TenantCreationResult {
    private final Tenant tenant;
    private final String rawTemporaryPassword;
    private final String loginEmail;
}
