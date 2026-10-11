package com.moveinsync.settlement.auth.dto;


import java.util.Set;

public class AuthResponseDto {
    private String token;
    private long expiresInMs;
    private String username;
    private Set<String> roles;

    public AuthResponseDto() {}

    public AuthResponseDto(String token, long expiresInMs, String username, Set<String> roles) {
        this.token = token;
        this.expiresInMs = expiresInMs;
        this.username = username;
        this.roles = roles;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public long getExpiresInMs() {
        return expiresInMs;
    }

    public void setExpiresInMs(long expiresInMs) {
        this.expiresInMs = expiresInMs;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }
}
