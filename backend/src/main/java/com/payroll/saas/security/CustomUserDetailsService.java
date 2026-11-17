package com.payroll.saas.security;

import com.payroll.saas.entity.User;
import com.payroll.saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String userIdentifier = username == null ? "" : username.trim();

        return userRepository.findByUsernameIgnoreCase(userIdentifier)
                .or(() -> userRepository.findByEmailIgnoreCase(userIdentifier))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }
}
