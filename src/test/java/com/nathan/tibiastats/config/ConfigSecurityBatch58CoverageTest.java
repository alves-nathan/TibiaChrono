package com.nathan.tibiastats.config;

import com.nathan.tibiastats.application.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigSecurityBatch58CoverageTest {
    private static final String SECRET = "0123456789ABCDEF0123456789ABCDEF";

    @Test
    @SuppressWarnings("unchecked")
    void jwtServiceNormalizesBlankAndPrefixedRolesAndRefreshType() {
        JwtService jwt = new JwtService(SECRET, 60_000, 120_000);

        List<String> blankRoles = jwt.parse(jwt.generateAccessToken("nathan", " ")).getBody().get("roles", List.class);
        List<String> normalizedRoles = jwt.parse(jwt.generateAccessToken("nathan", "ROLE_admin, user, ,ROLE_USER")).getBody().get("roles", List.class);
        String refreshType = jwt.parse(jwt.generateRefreshToken("nathan")).getBody().get("type", String.class);

        assertThat(blankRoles).containsExactly("USER");
        assertThat(normalizedRoles).containsExactly("ADMIN", "USER");
        assertThat(refreshType).isEqualTo("refresh");
        assertThat(jwt.parse(jwt.generateAccessToken("nathan")).getBody().getSubject()).isEqualTo("nathan");
        assertThatThrownBy(() -> jwt.parse("not-a-jwt")).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenBlacklistFilterRejectsBlacklistedJwtAndLetsInvalidOrMissingBearerContinue() throws Exception {
        TokenService tokens = mock(TokenService.class);
        JwtService jwt = mock(JwtService.class);
        TokenBlacklistFilter filter = new TokenBlacklistFilter(tokens, jwt);

        HttpServletRequest blacklistedRequest = mock(HttpServletRequest.class);
        HttpServletResponse blacklistedResponse = mock(HttpServletResponse.class);
        FilterChain blacklistedChain = mock(FilterChain.class);
        @SuppressWarnings("unchecked")
        Jws<Claims> jws = mock(Jws.class);
        Claims claims = mock(Claims.class);
        when(blacklistedRequest.getHeader("Authorization")).thenReturn("Bearer token-1");
        when(jwt.parse("token-1")).thenReturn(jws);
        when(jws.getBody()).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(tokens.isBlacklisted("jti-1")).thenReturn(true);

        filter.doFilterInternal(blacklistedRequest, blacklistedResponse, blacklistedChain);

        verify(blacklistedResponse).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(blacklistedChain, never()).doFilter(blacklistedRequest, blacklistedResponse);

        HttpServletRequest invalidRequest = mock(HttpServletRequest.class);
        HttpServletResponse invalidResponse = mock(HttpServletResponse.class);
        FilterChain invalidChain = mock(FilterChain.class);
        when(invalidRequest.getHeader("Authorization")).thenReturn("Bearer broken");
        when(jwt.parse("broken")).thenThrow(new JwtException("invalid"));

        filter.doFilterInternal(invalidRequest, invalidResponse, invalidChain);

        verify(invalidChain).doFilter(invalidRequest, invalidResponse);

        HttpServletRequest noBearerRequest = mock(HttpServletRequest.class);
        HttpServletResponse noBearerResponse = mock(HttpServletResponse.class);
        FilterChain noBearerChain = mock(FilterChain.class);
        when(noBearerRequest.getHeader("Authorization")).thenReturn("Basic abc");

        filter.doFilterInternal(noBearerRequest, noBearerResponse, noBearerChain);

        verify(noBearerChain).doFilter(noBearerRequest, noBearerResponse);
    }
}
