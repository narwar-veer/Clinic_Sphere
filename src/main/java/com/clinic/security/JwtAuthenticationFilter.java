package com.clinic.security;

import com.clinic.service.AdminSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AdminDetailsService adminDetailsService;
    private final AdminSessionService adminSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            String username = jwtService.extractUsername(token);
            String tokenId = jwtService.extractTokenId(token);
            if (username != null
                    && tokenId != null
                    && adminSessionService.validateAndTouchSession(tokenId)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                AdminPrincipal adminPrincipal = (AdminPrincipal) adminDetailsService.loadUserByUsername(username);
                if (jwtService.isTokenValid(token, adminPrincipal)) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    adminPrincipal,
                                    null,
                                    adminPrincipal.getAuthorities()
                            );
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            }
        } catch (IllegalArgumentException | UsernameNotFoundException ex) {
            // Any token parsing / user lookup error should fail authentication silently,
            // not break request handling with a 500.
            log.warn("Ignoring invalid JWT authentication: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
