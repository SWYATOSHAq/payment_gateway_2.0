package ru.university.paymentgateway.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID uuid = UUID.randomUUID();
    private String name;
    private String website;
    private boolean active = true;

    protected Merchant() {}

    public Merchant(String name, String website) {
        update(name, website, true);
    }

    public void update(String name, String website, boolean active) {
        this.name = name;
        this.website = website;
        this.active = active;
    }

    public Long id() { return id; }
    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public String website() { return website; }
    public boolean active() { return active; }
}
