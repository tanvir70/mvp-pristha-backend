package com.prishtha.mvp.identity.internal.service;

import com.prishtha.mvp.identity.internal.config.AuthProperties;
import com.prishtha.mvp.identity.internal.util.constant.IdentityRouteConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// Registered explicitly into the Spring Security filter chain (see
// SecurityConfig) rather than left as a plain @Component, so it isn't also
// auto-registered by Spring Boot as a second, unscoped servlet filter.
@Component
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private final CacheService cacheService;
    private final AuthProperties authProperties;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith(IdentityRouteConstant.AUTH_BASE_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        String bucketKey = request.getRemoteAddr() + ":" + uri;
        long count = cacheService.incrementRateLimit(bucketKey, authProperties.rateLimitWindow());
        if (count > authProperties.rateLimitMaxRequests()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/problem+json");
            response.getWriter().write(
                    "{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,"
                            + "\"detail\":\"Rate limit exceeded, try again later.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
