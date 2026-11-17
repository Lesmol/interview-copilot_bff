package dev.interviewcopilot.bff;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Safety check, not authentication: the edge gateway always sets {@code X-User-Id}. */
@Component
class UserIdFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Sign-up and password reset happen before a user exists; the edge gateway exposes only these without a token.
        return !path.startsWith("/api/") || path.startsWith("/api/v1/accounts/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing X-User-Id");
            return;
        }
        chain.doFilter(request, response);
    }
}
