package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.campaign.Campaign;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "publications")
@NoArgsConstructor
@Data
@EntityListeners(AuditingEntityListener.class)
public class Publication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicationStatus status;

    private LocalDateTime scheduledAt;

    private LocalDateTime publishedAt;

    private LocalDateTime endAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(nullable = false)
    private Boolean archived = false;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public Publication(String content, LocalDateTime scheduledAt, LocalDateTime publishedAt, LocalDateTime endAt) {
        this.content = content;
        this.scheduledAt = scheduledAt;
        this.publishedAt = publishedAt;
        this.endAt = endAt;
        this.status = PublicationStatus.DRAFT;
    }
}
