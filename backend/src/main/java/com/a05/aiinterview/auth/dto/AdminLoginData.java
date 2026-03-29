package com.a05.aiinterview.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminLoginData {

    private String token;
    private String username;
    private String displayName;
}
