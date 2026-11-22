package com.propio.config;

import com.propio.service.LoginAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles failed authentication attempts, records brute-force tracking metrics,
 * and redirects locked-out users to /login?error=locked.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String email = request.getParameter("email");
        if (email != null && !email.isBlank()) {
            loginAttemptService.loginFailed(email);
        }

        if (email != null && loginAttemptService.isBlocked(email)) {
            int minutes = loginAttemptService.getRemainingLockoutMinutes(email);
            log.warn("Blocked login attempt for locked account '{}' (remaining: ~{} min)", email, minutes);
            setDefaultFailureUrl("/login?error=locked");
        } else {
            int attempts = email != null ? loginAttemptService.getAttempts(email) : 0;
            log.info("Invalid login for '{}'. Attempts recorded: {}/{}", email, attempts, LoginAttemptService.MAX_ATTEMPTS);
            setDefaultFailureUrl("/login?error=true");
        }

        super.onAuthenticationFailure(request, response, exception);
    }
}
