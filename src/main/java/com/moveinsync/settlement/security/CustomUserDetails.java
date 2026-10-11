package com.moveinsync.settlement.security;

import com.moveinsync.settlement.entity.AppUser;
import com.moveinsync.settlement.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * UserDetails adapter wrapping AppUser for Spring Security authentication and RBAC.
 *
 * @author Deepanshu
 */
public class CustomUserDetails implements UserDetails {

    private final AppUser user;

    public CustomUserDetails(AppUser user) {
        this.user = user;
    }

    public AppUser getUser() {
        return user;
    }

    public Long getId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Role role = user.getRole();
        String roleStr = (role != null) ? "ROLE_" + role.name() : "ROLE_DRIVER";
        return Collections.singletonList(new SimpleGrantedAuthority(roleStr));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
