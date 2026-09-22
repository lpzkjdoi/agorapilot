package fr.maximechazard.agorapilot.back.campaign;

import fr.maximechazard.agorapilot.back.publication.Publication;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "campaigns")
@NoArgsConstructor
@Data
@EntityListeners(AuditingEntityListener.class)
public class Campaign {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    /**
     * Les dates ne portent <strong>aucune</strong> contrainte temporelle ici :
     * une campagne vieillit, et Hibernate revalide l'entité entière à chaque
     * update. Un `@FutureOrPresent` rendait toute campagne déjà commencée
     * impossible à modifier — donc à clôturer. Exiger une date future est une
     * règle de saisie : elle vit dans {@code CreateCampaignRequest}.
     * <p>
     * Une campagne sans date de fin est à durée indéterminée.
     */
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CampaignStatus status;

    @OneToMany(mappedBy = "campaign", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Publication> publications = new ArrayList<>();

    @Column(nullable = false)
    private Boolean archived = false;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public Campaign(String name, String description, LocalDateTime startDate, LocalDateTime endDate) {
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = determineStatus(startDate);
    }

    private CampaignStatus determineStatus(LocalDateTime startDate) {
        if (startDate == null) {
            return CampaignStatus.DRAFT;
        }

        LocalDateTime now = LocalDateTime.now();

        if (!startDate.isAfter(now)) {
            return CampaignStatus.ACTIVE;
        }

        return CampaignStatus.SCHEDULED;
    }

    public void addPublication(Publication publication) {
        publications.add(publication);
        publication.setCampaign(this);
    }

    /** Vrai dès lors que la date de début est atteinte ; une campagne sans date ne commence jamais. */
    public boolean hasStarted(LocalDateTime now) {
        return startDate != null && !startDate.isAfter(now);
    }

    /**
     * Arrête la campagne maintenant.
     * <p>
     * Une date de fin déjà passée est conservée : la campagne s'est arrêtée ce
     * jour-là, la clôture ne fait que l'acter. Une date de fin future, elle, est
     * ramenée au présent — c'est tout le sens d'une clôture anticipée.
     */
    public void close(LocalDateTime now) {
        if (endDate == null || endDate.isAfter(now)) {
            endDate = now;
        }

        status = CampaignStatus.COMPLETED;
    }
}
