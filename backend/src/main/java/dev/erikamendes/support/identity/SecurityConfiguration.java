package dev.erikamendes.support.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import java.util.UUID;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .httpBasic(basic -> basic.authenticationEntryPoint((request, response, error) ->
                        response.sendError(401)))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(@Value("${app.auth.username}") String username,
                                           @Value("${app.auth.password}") String password,
                                           @Value("${app.auth.secondary-username}") String secondaryUsername,
                                           @Value("${app.auth.secondary-password}") String secondaryPassword,
                                           PasswordEncoder encoder) {
        if (username.isBlank() || password.isBlank() || secondaryUsername.isBlank()
                || secondaryPassword.isBlank() || username.equals(secondaryUsername)) {
            throw new IllegalArgumentException("Configure two distinct non-empty demo accounts");
        }
        return new InMemoryUserDetailsManager(
                new SupportUser(username, encoder.encode(password),
                        UUID.fromString("11111111-1111-1111-1111-111111111111"), "Aurora Demo"),
                new SupportUser(secondaryUsername, encoder.encode(secondaryPassword),
                        UUID.fromString("22222222-2222-2222-2222-222222222222"), "Horizonte Demo"));
    }
}
