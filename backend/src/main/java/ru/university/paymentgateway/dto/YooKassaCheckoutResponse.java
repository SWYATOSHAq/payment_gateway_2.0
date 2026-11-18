package ru.university.paymentgateway.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record YooKassaCheckoutResponse(UUID id, String orderNumber, String merchantName, String description,
                                      BigDecimal amount, String currency, String status, String paymentMethod,
                                      String confirmationToken, boolean canPay, boolean processing) {}
