package com.nathan.tibiastats.config;

import com.nathan.tibiastats.application.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class TokenBlacklistFilter extends OncePerRequestFilter {
    private final TokenService tokens; private final JwtService jwt;
    public TokenBlacklistFilter(@Lazy TokenService t, JwtService j){ this.tokens=t; this.jwt=j; }
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")){
            String token = auth.substring(7);
            try {
                var jws = jwt.parse(token);
                String jti = jws.getBody().getId();
                if (jti != null && tokens.isBlacklisted(jti)){
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
            } catch (Exception ignored) { /* Resource server will handle invalid */ }
        }
        chain.doFilter(req, res);
    }
}