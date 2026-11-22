package com.propio.service;

import com.propio.controller.ActuatorHealthController;
import com.propio.model.Role;
import com.propio.model.User;
import com.propio.repository.TenantRepository;
import com.propio.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.LockedException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests verifying Phase 10 Production Readiness features:
 * - Brute-force rate limiting and account lockout (LoginAttemptService)
 * - Password complexity enforcement (UserService)
 * - Actuator health endpoint status and details (ActuatorHealthController)
 * - Security integration with LockedException in UserDetailsService
 */
class Phase10ProductionReadinessTest {

    private LoginAttemptService loginAttemptService;
    private UserService userService;
    private CustomUserDetailsService userDetailsService;
    private UserRepository mockUserRepo;
    private TenantRepository mockTenantRepo;
    private DataSource mockDataSource;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService();
        loginAttemptService.reset();

        mockUserRepo = mock(UserRepository.class);
        mockTenantRepo = mock(TenantRepository.class);
        mockDataSource = mock(DataSource.class);

        userDetailsService = new CustomUserDetailsService(mockUserRepo, mockTenantRepo, loginAttemptService);
    }

    @Test
    @DisplayName("LoginAttemptService locks account after 5 failed attempts")
    void testAccountLockoutAfterFiveFailures() {
        String testEmail = "testuser@example.com";

        for (int i = 1; i <= 4; i++) {
            loginAttemptService.loginFailed(testEmail);
            assertThat(loginAttemptService.isBlocked(testEmail)).isFalse();
            assertThat(loginAttemptService.getAttempts(testEmail)).isEqualTo(i);
        }

        // 5th failed attempt triggers lockout
        loginAttemptService.loginFailed(testEmail);
        assertThat(loginAttemptService.isBlocked(testEmail)).isTrue();
        assertThat(loginAttemptService.getRemainingLockoutMinutes(testEmail)).isGreaterThan(0);
        assertThat(loginAttemptService.getRemainingLockoutMinutes(testEmail)).isLessThanOrEqualTo(15);
    }

    @Test
    @DisplayName("LoginAttemptService resets attempts upon successful authentication")
    void testSuccessfulLoginResetsAttempts() {
        String testEmail = "reset@example.com";

        loginAttemptService.loginFailed(testEmail);
        loginAttemptService.loginFailed(testEmail);
        assertThat(loginAttemptService.getAttempts(testEmail)).isEqualTo(2);

        loginAttemptService.loginSucceeded(testEmail);
        assertThat(loginAttemptService.getAttempts(testEmail)).isZero();
        assertThat(loginAttemptService.isBlocked(testEmail)).isFalse();
    }

    @Test
    @DisplayName("CustomUserDetailsService throws LockedException when account is blocked")
    void testUserDetailsServiceThrowsLockedExceptionWhenBlocked() {
        String lockedUser = "locked@example.com";

        for (int i = 0; i < 5; i++) {
            loginAttemptService.loginFailed(lockedUser);
        }
        assertThat(loginAttemptService.isBlocked(lockedUser)).isTrue();

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(lockedUser))
                .isInstanceOf(LockedException.class)
                .hasMessageContaining("temporarily locked");
    }

    @Test
    @DisplayName("UserService validates strong password policy correctly")
    void testPasswordStrengthValidation() {
        // Weak passwords must throw IllegalArgumentException
        assertThatThrownBy(() -> UserService.validatePasswordStrength("short"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> UserService.validatePasswordStrength("alllowercase123"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> UserService.validatePasswordStrength("ALLUPPERCASE123"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> UserService.validatePasswordStrength("NoDigitsHere!"))
                .isInstanceOf(IllegalArgumentException.class);

        // Strong password must pass cleanly
        UserService.validatePasswordStrength("ValidPassword2026!");
        UserService.validatePasswordStrength("SecureP@ss1");
    }

    @Test
    @DisplayName("ActuatorHealthController returns 200 OK and UP status when DB is healthy")
    void testActuatorHealthUp() throws Exception {
        Connection mockConn = mock(Connection.class);
        DatabaseMetaData mockMeta = mock(DatabaseMetaData.class);

        when(mockDataSource.getConnection()).thenReturn(mockConn);
        when(mockConn.isValid(anyInt())).thenReturn(true);
        when(mockConn.getMetaData()).thenReturn(mockMeta);
        when(mockMeta.getDatabaseProductName()).thenReturn("MySQL");

        ActuatorHealthController controller = new ActuatorHealthController(mockDataSource);
        ResponseEntity<Map<String, Object>> response = controller.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("UP");

        @SuppressWarnings("unchecked")
        Map<String, Object> components = (Map<String, Object>) response.getBody().get("components");
        assertThat(components).containsKey("db");
        assertThat(components).containsKey("diskSpace");
    }
}
