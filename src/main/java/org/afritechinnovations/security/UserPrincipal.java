package org.afritechinnovations.security;

import lombok.Getter;
import org.afritechinnovations.model.common.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final boolean active;
    private final boolean approved;
    private final boolean emailVerified;
    private final boolean passwordSet;
    private final boolean mustChangePassword;
    private final long sessionVersion;
    private final List<String> roles;

    public UserPrincipal(User user, List<String> roles) {
        this.id = user.getId();
        this.email = user.getEmail();
        this.passwordHash = user.getPasswordHash();
        this.active = Boolean.TRUE.equals(user.getActive());
        this.approved = Boolean.TRUE.equals(user.getApproved());
        this.emailVerified = Boolean.TRUE.equals(user.getEmailVerified());
        this.passwordSet = Boolean.TRUE.equals(user.getPasswordSet());
        this.mustChangePassword = user.isMustChangePassword();
        this.sessionVersion = user.getSessionVersion();
        this.roles = java.util.stream.Stream.concat(java.util.stream.Stream.concat(roles.stream(), user.getPlatformRoles().stream()),
                user.isOwnerAccount() ? java.util.stream.Stream.of("SCHOOL_ADMIN") : java.util.stream.Stream.empty()).distinct().toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
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
        return active;
    }
}
