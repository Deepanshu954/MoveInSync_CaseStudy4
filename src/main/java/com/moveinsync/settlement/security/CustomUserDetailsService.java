package com.moveinsync.settlement.security;

import com.moveinsync.settlement.entity.AppUser;
import com.moveinsync.settlement.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Custom {@link UserDetailsService} bridging Spring Security with MoveInSync's database user store.
 *
 * <p>Loads user accounts by username, resolves active status, and wraps them in {@link CustomUserDetails}.
 *
 * @author Deepanshu
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
        return new CustomUserDetails(user);
    }
}
