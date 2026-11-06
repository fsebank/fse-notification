package com.fse.fsenotification.modules.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "message")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    private static final String RECIPIENT_SEPARATOR = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "access_key_id", nullable = false, updatable = false)
    private Long accessKeyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private MessageChannel channel;

    /** Comma-separated; use {@link #getRecipientList()}. */
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String recipients;

    @Column(name = "recipient_count", nullable = false, updatable = false)
    private int recipientCount;

    @Column(name = "sender_id", length = 11, updatable = false)
    private String senderId;

    @Column(updatable = false)
    private String subject;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String body;

    /** Billable units per recipient: SMS pages, or 1 for email. */
    @Column(nullable = false, updatable = false)
    private int units;

    @Column(nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal cost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status = MessageStatus.PENDING;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "client_reference", length = 100, updatable = false)
    private String clientReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public List<String> getRecipientList() {
        return Arrays.asList(recipients.split(RECIPIENT_SEPARATOR));
    }

    public void setRecipientList(List<String> recipientList) {
        this.recipients = String.join(RECIPIENT_SEPARATOR, recipientList);
        this.recipientCount = recipientList.size();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
