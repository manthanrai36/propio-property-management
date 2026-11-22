package com.propio.config;

import com.propio.service.LoginAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles successful authentication: resets failed login attempts in LoginAttemptService,
 * and routes user to /dashboard or their originally requested target URL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final LoginAttemptService loginAttemptService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String email = authentication.getName();
        loginAttemptService.loginSucceeded(email);
        log.info("User '{}' successfully signed in. Cleared failed attempts.", email);

        setDefaultTargetUrl("/dashboard");
        setAlwaysUseDefaultTargetUrl(false);
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
