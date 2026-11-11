package ru.university.paymentgateway.service;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.university.paymentgateway.domain.Merchant;
import ru.university.paymentgateway.dto.MerchantView;
import ru.university.paymentgateway.repository.AuditRepository;
import ru.university.paymentgateway.repository.MerchantRepository;

@Service
@Transactional(readOnly = true)
public class MerchantService {
    private final MerchantRepository merchants;
    private final AuditRepository audit;

    public MerchantService(MerchantRepository merchants, AuditRepository audit) {
        this.merchants = merchants;
        this.audit = audit;
    }

    public List<MerchantView> list() {
        return merchants.findAll(Sort.by("name", "id")).stream().map(MerchantView::from).toList();
    }

    @Transactional
    public MerchantView create(UUID actor, String name, String website) {
        Merchant merchant = save(new Merchant(name.strip(), validate_website(website)));
        audit.record(actor, "MERCHANT_CREATED", "MERCHANT", merchant.uuid(), "Создан магазин");
        return MerchantView.from(merchant);
    }

    @Transactional
    public MerchantView update(UUID actor, long id, String name, String website, boolean active) {
        Merchant merchant = merchants.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Магазин не найден"));
        String updated_website = merchant.website().equals(website) ? website : validate_website(website);
        merchant.update(name.strip(), updated_website, active);
        save(merchant);
        audit.record(actor, "MERCHANT_UPDATED", "MERCHANT", merchant.uuid(), "Изменён магазин");
        return MerchantView.from(merchant);
    }

    private Merchant save(Merchant merchant) {
        try {
            return merchants.saveAndFlush(merchant);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Адрес сайта уже используется");
        }
    }

    private static String validate_website(String website) {
        String value = website.strip();
        try {
            URI url = URI.create(value);
            if (url.getHost() != null && url.getUserInfo() == null
                    && ("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))) {
                return value;
            }
        } catch (IllegalArgumentException ignored) {
            // Report the same validation message for malformed URLs.
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите адрес сайта с http:// или https://");
    }
}
