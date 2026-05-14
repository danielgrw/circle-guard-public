package com.circleguard.auth.service;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.model.Permission;
import com.circleguard.auth.model.Role;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private LocalUserRepository userRepository;

    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_returnsUserWithAuthorities() {
        Role role = Role.builder().name("STAFF").permissions(Set.of(Permission.builder().name("PERM_A").build())).build();
        LocalUser user = LocalUser.builder()
                .username("synthetic-user-a")
                .password("encoded")
                .isActive(true)
                .roles(Set.of(role))
                .build();

        when(userRepository.findByUsername("synthetic-user-a")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("synthetic-user-a");
        assertEquals("synthetic-user-a", details.getUsername());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_STAFF")));
        assertTrue(details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("PERM_A")));
    }

    @Test
    void loadUserByUsername_throwsWhenMissing() {
        when(userRepository.findByUsername("missing-synthetic")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("missing-synthetic"));
    }

    @Test
    void loadUserByUsername_throwsWhenInactive() {
        LocalUser user = LocalUser.builder()
                .username("inactive-synthetic")
                .password("pw")
                .isActive(false)
                .roles(Set.of())
                .build();
        when(userRepository.findByUsername("inactive-synthetic")).thenReturn(Optional.of(user));

        assertThrows(DisabledException.class, () -> service.loadUserByUsername("inactive-synthetic"));
    }
}
