package fr.maximechazard.agorapilot.back.publication.recurrence;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.Publication;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Programmation par fréquence d'une publication : une règle de répétition
 * ({@link RecurrenceRule}), un jour de départ, une heure fixe ou automatique, et
 * des canaux. Les diffusions sont créées à l'avance par un job quotidien, sur un
 * horizon borné, et gardent le lien vers leur série.
 */
@Getter
@Setter
@Entity
@Table(name = "occurrence_series")
@EntityListeners(AuditingEntityListener.class)
public class OccurrenceSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publication_id", nullable = false)
    private Publication publication;

    /** Forme normalisée de la règle, sans préfixe {@code RRULE:} ni {@code DTSTART}. */
    @Column(nullable = false)
    private String rule;

    @Column(nullable = false)
    private LocalDate startDate;

    /** Heure fixe de chaque diffusion ; nulle, l'heure est répartie automatiquement dans la fenêtre du jour. */
    @Column(name = "publish_time")
    private LocalTime publishTime;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "occurrence_series_channels", joinColumns = @JoinColumn(name = "series_id"))
    @Column(name = "channel", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private Set<DeliveryChannel> channels = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OccurrenceSeriesStatus status = OccurrenceSeriesStatus.ACTIVE;

    /**
     * Curseur de génération : les dates jusqu'à ce jour ont été traitées (diffusion
     * créée, ou écartée car déjà passée). Une diffusion déplacée ou annulée à la main
     * n'est donc jamais recréée.
     */
    private LocalDate generatedThrough;

    /** Pourquoi la série s'est arrêtée, lisible tel quel. */
    private String stopReason;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(updatable = false, length = 32)
    private String createdBy;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public RecurrenceRule recurrence() {
        return RecurrenceRule.parse(rule);
    }

    public Set<DeliveryChannel> channelSet() {
        return channels.isEmpty() ? EnumSet.noneOf(DeliveryChannel.class) : EnumSet.copyOf(channels);
    }

    public void stop(String reason) {
        status = OccurrenceSeriesStatus.STOPPED;
        stopReason = reason;
    }
}
