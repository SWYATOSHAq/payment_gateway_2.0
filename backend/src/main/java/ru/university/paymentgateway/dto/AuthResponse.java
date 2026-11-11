package ru.university.paymentgateway.dto;

public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserProfile user) {}
