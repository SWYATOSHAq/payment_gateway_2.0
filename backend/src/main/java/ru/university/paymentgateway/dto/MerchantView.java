package ru.university.paymentgateway.dto;

import java.util.UUID;
import ru.university.paymentgateway.domain.Merchant;

public record MerchantView(long id, UUID uuid, String name, String website, boolean active) {
    public static MerchantView from(Merchant merchant) {
        return new MerchantView(merchant.id(), merchant.uuid(), merchant.name(), merchant.website(), merchant.active());
    }
}
