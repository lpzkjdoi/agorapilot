package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.media.Media;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Rattachement d'un média à une publication, à une position donnée.
 * <p>
 * Entité d'association explicite plutôt qu'un {@code @ManyToMany} : l'ordre
 * compte — le premier média sert de vignette et de première photo à la
 * diffusion — et une table de jointure implicite ne saurait pas le porter.
 * <p>
 * Le média n'est jamais supprimé en cascade : la médiathèque est une
 * bibliothèque partagée, détacher un visuel d'une publication ne doit pas
 * l'effacer pour les autres.
 */
@Entity
@Table(
        name = "publication_medias",
        uniqueConstraints = @UniqueConstraint(columnNames = {"publication_id", "media_id"})
)
@NoArgsConstructor
@Data
public class PublicationMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Exclu de {@code toString}/{@code equals} : la publication porte la liste de
     * ses rattachements, la relation est donc circulaire. C'est exactement la
     * récursion qui avait tronqué la réponse de {@code GET /api/occurrences/weekly}
     * (commit dc6d463).
     */
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publication_id", nullable = false)
    private Publication publication;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_id", nullable = false)
    private Media media;

    /** Rang dans la liste, à partir de 0. */
    @Column(nullable = false)
    private Integer position;

    public PublicationMedia(Publication publication, Media media, int position) {
        this.publication = publication;
        this.media = media;
        this.position = position;
    }
}
