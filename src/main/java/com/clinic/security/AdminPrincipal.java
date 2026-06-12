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
        this(admin.getId(), admin.getDoctor().getId(), admin.getUsername(), admin.getPasswordHash(),
                admin.getRole().name());
    }

    private AdminPrincipal(Long adminId, Long doctorId, String username, String password, String role) {
        this.adminId = adminId;
        this.doctorId = doctorId;
        this.username = username;
        this.password = password;
        this.authorities = List.of(new SimpleGrantedAuthority(role));
    }

    public static AdminPrincipal fromToken(Long adminId, Long doctorId, String username, String role) {
        return new AdminPrincipal(adminId, doctorId, username, "", role);
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