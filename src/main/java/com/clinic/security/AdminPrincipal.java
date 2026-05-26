package com.clinic.security;

import com.clinic.entity.Admin;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class AdminPrincipal implements UserDetails {

    private final Long adminId;
    private final Long doctorId;
    private final String username;
    private final String password;
    private final List<GrantedAuthority> authorities;

    public AdminPrincipal(Admin admin) {
        this.adminId = admin.getId();
        this.doctorId = admin.getDoctor().getId();
        this.username = admin.getUsername();
        this.password = admin.getPasswordHash();
        this.authorities = List.of(new SimpleGrantedAuthority(admin.getRole().name()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
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
        return true;
    }
}
