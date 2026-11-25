package com.example.questionnaire.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A named list of email addresses to invite to campaigns; owned like a campaign (README "Recipient lists"). */
@Entity
@Table(name = "recipient_lists",
        uniqueConstraints = @UniqueConstraint(name = "uk_recipient_list_owner_name", columnNames = {"owner_sub", "name"}))
public class RecipientList {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    /** OIDC subject (or {@code api:<label>}) of the owner. */
    @Column(name = "owner_sub", nullable = false, length = 128)
    private String ownerSub;

    @Column(name = "owner_name", length = 128)
    private String ownerName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recipient_list_entries", joinColumns = @JoinColumn(name = "list_id"))
    @OrderColumn(name = "position")
    private List<Entry> entries = new ArrayList<>();

    /** One address on a list, with an optional display name. */
    @Embeddable
    public static class Entry {
        @Column(nullable = false, length = 254)
        private String email;

        @Column(length = 120)
        private String name;

        protected Entry() { }

        public Entry(String email, String name) {
            this.email = email;
            this.name = name;
        }

        public String getEmail() { return email; }
        public String getName() { return name; }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOwnerSub() { return ownerSub; }
    public void setOwnerSub(String ownerSub) { this.ownerSub = ownerSub; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<Entry> getEntries() { return entries; }
    public void setEntries(List<Entry> entries) { this.entries = entries; }
}
