package com.localdelivery.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.localdelivery.exception.ApiExceptionHandler.ApiError;
import com.localdelivery.repository.UserRepository;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import com.localdelivery.security.AccountPrincipal;
import com.localdelivery.security.SessionVersionFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration
public class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() {
        return Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean UserDetailsService userDetailsService(UserRepository users, @Value("${app.accounts.require-verification:true}") boolean requireVerification) {
        return email -> {
            var user = users.findByEmailIgnoreCase(email)
                    .filter(item -> item.getPasswordHash() != null)
                    .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
            // The principal's name is the immutable database ID, never a client-supplied ID.
            return new AccountPrincipal(user, requireVerification);
        };
    }

    @Bean AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwords) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        // Reveal verification status only after the supplied password has been checked.
        provider.setPostAuthenticationChecks(details -> {
            if (details instanceof AccountPrincipal account && !account.verifiedForLogin())
                throw new org.springframework.security.authentication.DisabledException("Email verification required.");
        });
        return new ProviderManager(provider);
    }

    @Bean SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean HttpSessionCsrfTokenRepository csrfRepository() { return new HttpSessionCsrfTokenRepository(); }

    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(HttpSessionCsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper json,
            SecurityContextRepository contexts, HttpSessionCsrfTokenRepository csrf, JdbcTemplate jdbc) throws Exception {
        return http
                .csrf(config -> config.csrfTokenRepository(csrf))
                .securityContext(config -> config.securityContextRepository(contexts))
                .addFilterAfter(new SessionVersionFilter(jdbc, json), SecurityContextHolderFilter.class)
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/api/health/ready", "/api/auth/csrf", "/api/auth/register", "/api/auth/login",
                                "/api/auth/forgot-password", "/api/auth/reset-password",
                                "/api/auth/resend-verification", "/api/auth/verify-email",
                                "/docs", "/swagger-ui/**", "/api/openapi.json", "/api/openapi.json/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                ApiError.write(json, response, HttpStatus.UNAUTHORIZED, "Please log in to continue."))
                        .accessDeniedHandler((request, response, error) ->
                                ApiError.write(json, response, HttpStatus.FORBIDDEN, "Access denied or session token expired. Refresh and try again.")))
                .logout(logout -> logout.logoutUrl("/api/auth/logout").deleteCookies("DELIVERY_SESSION")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
                .build();
    }
}
