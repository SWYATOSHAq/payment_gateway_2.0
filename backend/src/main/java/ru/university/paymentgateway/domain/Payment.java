package ru.university.paymentgateway.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "payments")
public class Payment {
    public enum Status { CREATED, COMPLETED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID uuid = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id")
    private Merchant merchant;
    private String order_number;
    private BigDecimal amount;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 3)
    private String currency = "RUB";
    private String description;
    @Enumerated(EnumType.STRING)
    private Status status = Status.CREATED;
    private boolean needs_review;
    private boolean archived;
    private Instant checkout_created_at;
    private String payment_method;
    private Instant created_at = Instant.now();
    private Instant updated_at = created_at;
    @Version
    private long version;
    @ElementCollection
    @CollectionTable(name = "payment_tags", joinColumns = @JoinColumn(name = "payment_id"))
    @Column(name = "tag", length = 40)
    @BatchSize(size = 50)
    private Set<String> tags = new HashSet<>();

    protected Payment() {}

    public Payment(Merchant merchant, String order_number, BigDecimal amount,
                   String description, boolean needs_review, Set<String> tags) {
        this.merchant = merchant;
        this.order_number = order_number;
        update(amount, description, Status.CREATED, needs_review, tags);
    }

    public void update(BigDecimal amount, String description, Status status, boolean needs_review, Set<String> tags) {
        this.amount = amount;
        this.description = description;
        this.status = status;
        this.needs_review = needs_review;
        this.tags.clear();
        this.tags.addAll(tags);
        updated_at = Instant.now();
    }

    public void archive(boolean archived) {
        this.archived = archived;
        updated_at = Instant.now();
    }

    public Long id() { return id; }
    public UUID uuid() { return uuid; }
    public Merchant merchant() { return merchant; }
    public String order_number() { return order_number; }
    public BigDecimal amount() { return amount; }
    public String currency() { return currency; }
    public String description() { return description; }
    public Status status() { return status; }
    public boolean needs_review() { return needs_review; }
    public boolean archived() { return archived; }
    public boolean cloudpayments() { return checkout_created_at != null; }
    public String payment_method() { return payment_method; }
    public Instant created_at() { return created_at; }
    public Instant updated_at() { return updated_at; }
    public long version() { return version; }
    public Set<String> tags() { return Set.copyOf(tags); }
}
