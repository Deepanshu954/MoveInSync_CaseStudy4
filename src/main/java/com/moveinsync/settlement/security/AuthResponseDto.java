package com.moveinsync.settlement.security;

import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "token")
public class AuthResponseDto {

    private String token;
    private long expiresInMs;
    private String username;
    private Set<String> roles;
}
