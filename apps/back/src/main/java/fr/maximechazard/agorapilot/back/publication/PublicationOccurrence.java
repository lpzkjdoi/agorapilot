package fr.maximechazard.agorapilot.back.publication;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "publication_occurrences")
@EntityListeners(AuditingEntityListener.class)
public class PublicationOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PublicationOccurrenceStatus status = PublicationOccurrenceStatus.SCHEDULED;

    /**
     * Heure fixée à la main. Une occurrence épinglée garde son heure ; les autres
     * sont réparties automatiquement dans la fenêtre de publication de leur jour,
     * et recalculées à chaque ajout, déplacement ou annulation ce jour-là.
     * <p>
     * {@code @ColumnDefault} : {@code ddl-auto: update} ajoute la colonne sur une
     * table déjà peuplée, ce que PostgreSQL refuse pour un {@code NOT NULL} sans
     * valeur par défaut.
     */
    @Column(nullable = false)
    @ColumnDefault("false")
    private boolean pinned = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publication_id", nullable = false)
    private Publication publication;

    @OneToMany(mappedBy = "occurrence", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PublicationDelivery> deliveries = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public void schedule(LocalDateTime publishTime) {
        this.scheduledAt = publishTime;
    }

    public void addDelivery(PublicationDelivery delivery) {
        if (delivery == null) {
            return;
        }
        delivery.setOccurrence(this);
        this.deliveries.add(delivery);
    }
}