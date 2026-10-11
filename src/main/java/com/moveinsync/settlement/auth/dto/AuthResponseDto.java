package com.moveinsync.settlement.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDto {
    private String token;
    private long expiresInMs;
    private String username;
    private Set<String> roles;
}
