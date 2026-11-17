package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.recurrence.OccurrenceSeries;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
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

    /**
     * Série qui l'a créée, pour une diffusion programmée par fréquence ; nulle
     * sinon. {@code seriesDate} est sa date d'origine dans la série, conservée
     * même si la diffusion est ensuite déplacée.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private OccurrenceSeries series;

    private LocalDate seriesDate;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Pseudo de qui l'a programmée, ou diffusée immédiatement. Pas de « dernier
     * modificateur » : la répartition automatique déplace les diffusions des
     * autres, et la lui attribuer tromperait. Déplacements, reprises et
     * annulations sont au journal.
     */
    @CreatedBy
    @Column(updatable = false, length = 32)
    private String createdBy;

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