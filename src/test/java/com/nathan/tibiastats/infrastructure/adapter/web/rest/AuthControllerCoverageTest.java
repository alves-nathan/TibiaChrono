package com.nathan.tibiastats.infrastructure.adapter.web.rest;

import com.nathan.tibiastats.application.service.TokenService;
import com.nathan.tibiastats.config.JwtService;
import com.nathan.tibiastats.domain.model.RefreshToken;
import com.nathan.tibiastats.domain.model.UserAccount;
import com.nathan.tibiastats.domain.port.UserAccountRepositoryPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerCoverageTest {

    @Test
    void registerCreatesUserWithEncodedPasswordAndDefaultUserRole() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        JwtService jwt = mock(JwtService.class);
        UserAccountRepositoryPort users = mock(UserAccountRepositoryPort.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenService tokens = mock(TokenService.class);
        AuthController controller = new AuthController(authManager, jwt, users, encoder, tokens);
        when(users.findByUsername("nathan")).thenReturn(Optional.empty());
        when(encoder.encode("secret")).thenReturn("encoded-secret");

        ResponseEntity<?> response = controller.register(new RegisterRequest("nathan", "secret", "ADMIN"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        ArgumentCaptor<UserAccount> savedUser = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(savedUser.capture());
        assertThat(savedUser.getValue().getUsername()).isEqualTo("nathan");
        assertThat(savedUser.getValue().getPassword()).isEqualTo("encoded-secret");
        assertThat(savedUser.getValue().getRoles()).isEqualTo("USER");
    }

    @Test
    void registerRejectsDuplicatedUsername() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        JwtService jwt = mock(JwtService.class);
        UserAccountRepositoryPort users = mock(UserAccountRepositoryPort.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenService tokens = mock(TokenService.class);
        AuthController controller = new AuthController(authManager, jwt, users, encoder, tokens);
        when(users.findByUsername("nathan")).thenReturn(Optional.of(user("nathan", "USER")));

        assertThatThrownBy(() -> controller.register(new RegisterRequest("nathan", "secret", "USER")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("username already exists");
    }

    @Test
    void loginAuthenticatesAndReturnsAccessAndRefreshTokens() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        JwtService jwt = mock(JwtService.class);
        UserAccountRepositoryPort users = mock(UserAccountRepositoryPort.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenService tokens = mock(TokenService.class);
        AuthController controller = new AuthController(authManager, jwt, users, encoder, tokens);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("nathan");
        when(authManager.authenticate(any())).thenReturn(authentication);
        when(users.findByUsername("nathan")).thenReturn(Optional.of(user("nathan", "ADMIN")));
        when(jwt.generateAccessToken("nathan", "ADMIN")).thenReturn("access-token");
        when(tokens.issueRefreshToken("nathan")).thenReturn("refresh-token");

        ResponseEntity<AuthResponse> response = controller.login(new AuthRequest("nathan", "secret"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().accessToken()).isEqualTo("access-token");
        assertThat(response.getBody().refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void refreshRejectsInvalidJwtAndNonRefreshTokens() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        JwtService jwt = mock(JwtService.class);
        UserAccountRepositoryPort users = mock(UserAccountRepositoryPort.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenService tokens = mock(TokenService.class);
        AuthController controller = new AuthController(authManager, jwt, users, encoder, tokens);
        when(jwt.parse("invalid")).thenThrow(new JwtException("bad token"));
        Claims accessClaims = mock(Claims.class);
        when(accessClaims.get("type")).thenReturn("access");
        doReturn(jwsWith(accessClaims)).when(jwt).parse("access");

        ResponseEntity<?> invalid = controller.refresh(new RefreshRequest("invalid"));
        ResponseEntity<?> access = controller.refresh(new RefreshRequest("access"));

        assertThat(invalid.getStatusCode().value()).isEqualTo(401);
        assertThat(access.getStatusCode().value()).isEqualTo(401);
        assertThat(invalid.getBody()).isEqualTo(Map.of("error", "invalid refresh token"));
        assertThat(access.getBody()).isEqualTo(Map.of("error", "invalid refresh token"));
    }

    @Test
    void refreshRejectsMissingRevokedAndExpiredRefreshTokens() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        JwtService jwt = mock(JwtService.class);
        UserAccountRepositoryPort users = mock(UserAccountRepositoryPort.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenService tokens = mock(TokenService.class);
        AuthController controller = new AuthController(authManager, jwt, users, encoder, tokens);

        Claims missingUserClaims = refreshClaims("missing");
        Claims revokedClaims = refreshClaims("revoked");
        Claims expiredClaims = refreshClaims("expired");
        doReturn(jwsWith(missingUserClaims)).when(jwt).parse("missing-user");
        doReturn(jwsWith(revokedClaims)).when(jwt).parse("revoked-token");
        doReturn(jwsWith(expiredClaims)).when(jwt).parse("expired-token");
        when(users.findByUsername("missing")).thenReturn(Optional.empty());
        when(users.findByUsername("revoked")).thenReturn(Optional.of(user("revoked", "USER")));
        when(users.findByUsername("expired")).thenReturn(Optional.of(user("expired", "USER")));
        RefreshToken revoked = refreshToken(true, Instant.now().plusSeconds(60));
        RefreshToken expired = refreshToken(false, Instant.EPOCH);
        when(tokens.findRefreshToken("revoked-token")).thenReturn(Optional.of(revoked));
        when(tokens.findRefreshToken("expired-token")).thenReturn(Optional.of(expired));

        ResponseEntity<?> missing = controller.refresh(new RefreshRequest("missing-user"));
        ResponseEntity<?> revokedResponse = controller.refresh(new RefreshRequest("revoked-token"));
        ResponseEntity<?> expiredResponse = controller.refresh(new RefreshRequest("expired-token"));

        assertThat(missing.getStatusCode().value()).isEqualTo(401);
        assertThat(revokedResponse.getBody()).isEqualTo(Map.of("error", "revoked"));
        assertThat(expiredResponse.getBody()).isEqualTo(Map.of("error", "expired"));
    }

    @Test
    void refreshRevokesOldRefreshTokenAndReturnsRotatedTokens() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        JwtService jwt = mock(JwtService.class);
        UserAccountRepositoryPort users = mock(UserAccountRepositoryPort.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenService tokens = mock(TokenService.class);
        AuthController controller = new AuthController(authManager, jwt, users, encoder, tokens);
        UserAccount user = user("nathan", "ADMIN");
        Claims refreshClaims = refreshClaims("nathan");
        doReturn(jwsWith(refreshClaims)).when(jwt).parse("old-refresh");
        when(users.findByUsername("nathan")).thenReturn(Optional.of(user));
        when(tokens.findRefreshToken("old-refresh"))
                .thenReturn(Optional.of(refreshToken(false, Instant.now().plusSeconds(60))));
        when(tokens.issueRefreshToken("nathan")).thenReturn("new-refresh");
        when(jwt.generateAccessToken("nathan", "ADMIN")).thenReturn("new-access");

        ResponseEntity<?> response = controller.refresh(new RefreshRequest("old-refresh"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        AuthResponse body = (AuthResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.accessToken()).isEqualTo("new-access");
        assertThat(body.refreshToken()).isEqualTo("new-refresh");
        verify(tokens).revokeRefreshToken("old-refresh");
    }

    @Test
    void logoutRevokesBearerTokenAndIgnoresMissingOrInvalidTokens() {
        AuthenticationManager authManager = mock(AuthenticationManager.class);
        JwtService jwt = mock(JwtService.class);
        UserAccountRepositoryPort users = mock(UserAccountRepositoryPort.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TokenService tokens = mock(TokenService.class);
        AuthController controller = new AuthController(authManager, jwt, users, encoder, tokens);
        Claims accessClaims = mock(Claims.class);
        when(accessClaims.getId()).thenReturn("jti-1");
        doReturn(jwsWith(accessClaims)).when(jwt).parse("access");
        when(jwt.parse("invalid")).thenThrow(new JwtException("bad token"));

        assertThat(controller.logout("Bearer access").getStatusCode().value()).isEqualTo(200);
        assertThat(controller.logout("Bearer invalid").getStatusCode().value()).isEqualTo(200);
        assertThat(controller.logout(null).getStatusCode().value()).isEqualTo(200);

        verify(tokens).revokeAccessToken("jti-1", "access", "logout");
    }

    private static UserAccount user(String username, String roles) {
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setRoles(roles);
        return user;
    }

    private static RefreshToken refreshToken(boolean revoked, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setRevoked(revoked);
        token.setExpiresAt(expiresAt);
        return token;
    }

    private static Claims refreshClaims(String subject) {
        Claims claims = mock(Claims.class);
        when(claims.get("type")).thenReturn("refresh");
        when(claims.getSubject()).thenReturn(subject);
        return claims;
    }

    @SuppressWarnings("unchecked")
    private static Jws<Claims> jwsWith(Claims claims) {
        Jws<Claims> jws = mock(Jws.class);
        when(jws.getPayload()).thenReturn(claims);
        return jws;
    }
}
