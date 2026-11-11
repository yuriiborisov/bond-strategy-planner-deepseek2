package com.bondplanner.api.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds, UserDto user) {
}
