package com.nathan.tibiastats.config;

import com.nathan.tibiastats.application.service.TokenService;
import com.nathan.tibiastats.domain.model.UserAccount;
import com.nathan.tibiastats.domain.port.UserAccountRepositoryPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConfigBatch59CoverageTest {
    private static final String SECRET = "0123456789ABCDEF0123456789ABCDEF";

    @Test
    void dbUserDetailsMapsAuthoritiesLocksDisabledUsersAndThrowsWhenMissing() {
        UserAccountRepositoryPort repo = mock(UserAccountRepositoryPort.class);
        DbUserDetailsService service = new DbUserDetailsService(repo);
        UserAccount account = new UserAccount();
        account.setUsername("nathan");
        account.setPassword("{noop}pw");
        account.setRoles("admin, ROLE_USER, ,");
        account.setEnabled(false);

        when(repo.findByUsername("nathan")).thenReturn(Optional.of(account));
        when(repo.findByUsername("missing")).thenReturn(Optional.empty());

        var details = service.loadUserByUsername("nathan");

        assertThat(details.getUsername()).isEqualTo("nathan");
        assertThat(details.getPassword()).isEqualTo("{noop}pw");
        assertThat(details.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_admin", "ROLE_USER");
        assertThat(details.isAccountNonLocked()).isFalse();
        assertThat(details.isEnabled()).isFalse();

        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    @SuppressWarnings("unchecked")
    void tokenBlacklistFilterContinuesForNullJtiAndJwtServiceDefaultAccessTokenUsesUserRole() throws Exception {
        TokenService tokens = mock(TokenService.class);
        JwtService jwt = mock(JwtService.class);
        TokenBlacklistFilter filter = new TokenBlacklistFilter(tokens, jwt);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        Jws<Claims> jws = mock(Jws.class);
        Claims claims = mock(Claims.class);

        when(request.getHeader("Authorization")).thenReturn("Bearer token-without-jti");
        when(jwt.parse("token-without-jti")).thenReturn(jws);
        when(jws.getBody()).thenReturn(claims);
        when(claims.getId()).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        verify(tokens, never()).isBlacklisted(org.mockito.ArgumentMatchers.any());
        verify(chain).doFilter(request, response);

        JwtService realJwt = new JwtService(SECRET, 60_000, 60_000);
        var parsed = realJwt.parse(realJwt.generateAccessToken("nathan")).getBody();

        assertThat(parsed.getSubject()).isEqualTo("nathan");
        assertThat(parsed.get("roles", java.util.List.class)).containsExactly("USER");
        assertThat(new OpenApiConfig()).isNotNull();
    }
}
