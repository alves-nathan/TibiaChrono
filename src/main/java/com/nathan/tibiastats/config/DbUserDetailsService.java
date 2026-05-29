package com.nathan.tibiastats.config;

import com.nathan.tibiastats.domain.port.UserAccountRepositoryPort;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class DbUserDetailsService implements UserDetailsService {
    private final UserAccountRepositoryPort repo;
    public DbUserDetailsService(UserAccountRepositoryPort repo){this.repo=repo;}
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var acc = repo.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("User not found"));
        var authorities = Arrays.stream(acc.getRoles().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(r -> r.startsWith("ROLE_") ? r : "ROLE_"+r)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return User.withUsername(acc.getUsername())
                .password(acc.getPassword())
                .authorities(authorities)
                .accountLocked(!acc.getEnabled())
                .disabled(!acc.getEnabled())
                .build();
    }
}