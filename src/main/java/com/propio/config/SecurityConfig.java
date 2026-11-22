package com.propio.config;

import com.propio.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security Configuration for Propio.
 * Enforces role-based access control, BCrypt password hashing, session management,
 * custom styled login/registration, and remember-me authentication.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/login",
                    "/register",
                    "/error",
                    "/error/**",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/uploads/**",
                    "/webjars/**",
                    "/h2-console/**",
                    "/favicon.ico"
                ).permitAll()
                // Role-restricted endpoints
                .requestMatchers("/properties", "/properties/**").hasRole("ADMIN")
                .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/tenants/add", "/tenants/*/edit", "/tenants/*/delete").hasRole("ADMIN")
                .requestMatchers("/tenants").hasRole("ADMIN")
                .requestMatchers("/tenants/*").hasAnyRole("ADMIN", "TENANT")
                .requestMatchers("/leasing/new", "/leasing/*/terminate", "/leasing/*/renew").hasRole("ADMIN")
                .requestMatchers("/leasing", "/leasing/**").hasAnyRole("ADMIN", "TENANT")
                .requestMatchers("/tenant/leases").hasRole("TENANT")
                .requestMatchers("/payments/record", "/payments/run-engine").hasRole("ADMIN")
                .requestMatchers("/payments", "/payments/**").hasAnyRole("ADMIN", "TENANT")
                .requestMatchers("/tenant/payments").hasRole("TENANT")
                .requestMatchers("/documents", "/documents/**").hasAnyRole("ADMIN", "TENANT")
                .requestMatchers("/emails", "/emails/**").hasRole("ADMIN")
                .requestMatchers("/maintenance/new", "/maintenance/my-requests").hasAnyRole("TENANT", "ADMIN")
                .requestMatchers("/maintenance/*/status", "/maintenance/*/assign").hasRole("ADMIN")
                .requestMatchers("/maintenance").hasRole("ADMIN")
                .requestMatchers("/maintenance/**").hasAnyRole("ADMIN", "TENANT")
                // All other requests require an active authenticated session
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/error/403")
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .rememberMe(remember -> remember
                .key("propio-remember-me-secret-key-2024")
                .tokenValiditySeconds(86400 * 14) // 14 days
                .userDetailsService(userDetailsService)
                .rememberMeParameter("remember-me")
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll()
            )
            .csrf(csrf -> csrf
                // Disable CSRF for H2 console frame access in dev
                .ignoringRequestMatchers("/h2-console/**")
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.disable())
            );

        return http.build();
    }
}
