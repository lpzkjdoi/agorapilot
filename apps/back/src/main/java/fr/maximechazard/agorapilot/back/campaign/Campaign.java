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

    /**
     * Sans cascade ni {@code orphanRemoval} : une publication vit par elle-même
     * et la campagne ne fait que la regrouper. Avec {@code CascadeType.ALL},
     * supprimer une campagne aurait supprimé ses publications — et leurs
     * diffusions avec elles ; retirer une publication de la liste l'aurait
     * effacée. C'est {@code Publication.campaign} qui porte le rattachement.
     */
    @OneToMany(mappedBy = "campaign")
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

    /**
     * Le statut à l'instant {@code now}.
     * <p>
     * Seuls {@code COMPLETED} et {@code CANCELED} sont des décisions — une
     * clôture, une annulation — et méritent d'être stockés. Les autres se
     * déduisent des dates : le statut stocké, figé à la création, laissait une
     * campagne « programmée » le rester bien après son démarrage.
     */
    public CampaignStatus statusAt(LocalDateTime now) {
        if (status == CampaignStatus.CANCELED || status == CampaignStatus.COMPLETED) {
            return status;
        }

        if (startDate == null) {
            return CampaignStatus.DRAFT;
        }

        if (startDate.isAfter(now)) {
            return CampaignStatus.SCHEDULED;
        }

        if (endDate != null && !endDate.isAfter(now)) {
            return CampaignStatus.COMPLETED;
        }

        return CampaignStatus.ACTIVE;
    }

    /**
     * Remplace le nom, la description et la période. Le statut stocké suit la
     * nouvelle date de début, sauf décision déjà prise (clôture, annulation).
     */
    public void update(String name, String description, LocalDateTime startDate, LocalDateTime endDate) {
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;

        if (status != CampaignStatus.COMPLETED && status != CampaignStatus.CANCELED) {
            status = determineStatus(startDate);
        }
    }

    /** Annule la campagne. Ses dates sont conservées : elles disent ce qui était prévu. */
    public void cancel() {
        status = CampaignStatus.CANCELED;
    }

    /**
     * Archive la campagne et en détache les publications, qui redeviennent
     * « sans campagne » plutôt que d'afficher le badge d'une campagne disparue.
     * Rien n'est effacé : la ligne reste en base, pour l'historique.
     */
    public void archive() {
        archived = true;
        publications.forEach(publication -> publication.setCampaign(null));
        publications.clear();
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
