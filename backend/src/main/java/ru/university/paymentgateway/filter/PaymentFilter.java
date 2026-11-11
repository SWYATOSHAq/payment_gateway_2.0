package ru.university.paymentgateway.filter;

import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import ru.university.paymentgateway.domain.AmountCategory;
import ru.university.paymentgateway.domain.Payment;

public record PaymentFilter(@Size(max = 120) String q, @Positive Long merchantId, Payment.Status status,
                            Boolean needsReview, AmountCategory amountCategory, @Size(max = 40) String tag,
                            Boolean archived,
                            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal amount,
                            Comparison amountOp) {
    public enum Comparison { EQ, GT, GTE, LT, LTE }

    public Specification<Payment> specification() {
        return (root, query, cb) -> {
            var predicates = new ArrayList<Predicate>();
            var payment_amount = root.<BigDecimal>get("amount");
            predicates.add(cb.equal(root.get("archived"), Boolean.TRUE.equals(archived)));
            if (merchantId != null) {
                predicates.add(cb.equal(root.get("merchant").get("id"), merchantId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (needsReview != null) {
                predicates.add(cb.equal(root.get("needs_review"), needsReview));
            }
            if (tag != null && !tag.isBlank()) {
                predicates.add(cb.isMember(normalize_tag(tag), root.get("tags")));
            }
            if (q != null && !q.isBlank()) {
                String search = q.strip().toLowerCase(Locale.ROOT);
                var matches = new ArrayList<Predicate>();
                matches.add(cb.gt(cb.locate(cb.lower(root.get("description")), search), 0));
                matches.add(cb.gt(cb.locate(cb.lower(root.get("order_number")), search), 0));
                matches.add(cb.gt(cb.locate(cb.lower(root.get("merchant").get("name")), search), 0));
                if (search.matches("[0-9]{1,18}")) {
                    matches.add(cb.equal(root.get("id"), Long.parseLong(search)));
                }
                predicates.add(cb.or(matches.toArray(Predicate[]::new)));
            }
            if (amount != null) {
                predicates.add(switch (amountOp == null ? Comparison.EQ : amountOp) {
                    case EQ -> cb.equal(payment_amount, amount);
                    case GT -> cb.greaterThan(payment_amount, amount);
                    case GTE -> cb.greaterThanOrEqualTo(payment_amount, amount);
                    case LT -> cb.lessThan(payment_amount, amount);
                    case LTE -> cb.lessThanOrEqualTo(payment_amount, amount);
                });
            }
            if (amountCategory != null) {
                predicates.add(switch (amountCategory) {
                    case SMALL -> cb.lessThan(payment_amount, AmountCategory.MEDIUM_FROM);
                    case MEDIUM -> cb.and(cb.greaterThanOrEqualTo(payment_amount, AmountCategory.MEDIUM_FROM),
                            cb.lessThan(payment_amount, AmountCategory.LARGE_FROM));
                    case LARGE -> cb.greaterThanOrEqualTo(payment_amount, AmountCategory.LARGE_FROM);
                });
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static String normalize_tag(String tag) {
        return tag.strip().toLowerCase(Locale.ROOT);
    }
}
