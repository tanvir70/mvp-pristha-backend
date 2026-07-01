package com.prishtha.mvp.identity.internal.config;

import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.AUTH_BASE_PATH;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.LOGIN;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.LOGOUT;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.MFA_VERIFY;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.PASSWORD_FORGOT;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.PASSWORD_RESET;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.REFRESH;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.SIGN_UP;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.SOCIAL_GOOGLE;
import static com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant.VERIFY_OTP;

import com.prishtha.mvp.identity.internal.service.AuthRateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Maps the JWT's "roles" claim (computed once at login) into
    // GrantedAuthoritys — never re-derived per request.
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    // AuthRateLimitFilter is a @Component so it can be wired into the security
    // chain below via addFilterBefore. Without this, Spring Boot would ALSO
    // auto-register it as a second, unscoped servlet filter — this bean turns
    // that auto-registration off so it only runs once, in the right place.
    @Bean
    public FilterRegistrationBean<AuthRateLimitFilter> disableAutoRateLimitFilterRegistration(
            AuthRateLimitFilter filter) {
        FilterRegistrationBean<AuthRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthRateLimitFilter rateLimitFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(AUTH_BASE_PATH + SIGN_UP, AUTH_BASE_PATH + VERIFY_OTP,
                        AUTH_BASE_PATH + LOGIN, AUTH_BASE_PATH + REFRESH, AUTH_BASE_PATH + LOGOUT,
                        AUTH_BASE_PATH + SOCIAL_GOOGLE, AUTH_BASE_PATH + MFA_VERIFY,
                        AUTH_BASE_PATH + PASSWORD_FORGOT, AUTH_BASE_PATH + PASSWORD_RESET).permitAll()
                .requestMatchers("/api/v1/posts/**").permitAll()
                .requestMatchers("/api/v1/tags/**").permitAll()
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
            ))
            .addFilterBefore(rateLimitFilter, BasicAuthenticationFilter.class);
        return http.build();
    }
}
