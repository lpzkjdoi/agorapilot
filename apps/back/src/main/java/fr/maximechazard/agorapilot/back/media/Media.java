package fr.maximechazard.agorapilot.back.media;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Un visuel de la médiathèque : affiche, photo ou document attachable à des
 * publications.
 * <p>
 * L'entité ne porte que les métadonnées ; le binaire vit sur le disque, désigné
 * par {@link #storageKey}. Les deux sont maintenus cohérents par
 * {@code MediaService}, qui n'efface un fichier qu'après le commit de la
 * transaction qui a supprimé sa ligne.
 */
@Entity
@Table(name = "medias")
@NoArgsConstructor
@Data
@EntityListeners(AuditingEntityListener.class)
public class Media {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Chemin relatif du fichier sous la racine de stockage, de la forme
     * {@code 2026/08/<uuid>.jpg}. Généré par le stockage : le nom fourni par le
     * client n'entre jamais dans la composition d'un chemin.
     */
    @NotBlank
    @Column(nullable = false, unique = true)
    private String storageKey;

    /** Nom d'origine, conservé pour l'affichage et le téléchargement seulement. */
    @NotBlank
    @Column(nullable = false)
    private String originalFilename;

    /** Type réel, déterminé par {@link MediaTypeDetector}, jamais celui annoncé. */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaFileType fileType;

    @NotNull
    @Column(nullable = false)
    private Long sizeBytes;

    /** SHA-256 du contenu, en hexadécimal. Sert d'{@code ETag} à la lecture. */
    @NotBlank
    @Column(nullable = false, length = 64)
    private String checksum;

    /** Nuls pour un PDF, et pour une image dont l'en-tête serait illisible. */
    private Integer width;
    private Integer height;

    /** Libellé éditable ; à défaut, le front retombe sur le nom d'origine. */
    private String title;

    /** Texte alternatif : accessibilité, et description envoyée au réseau social. */
    @Column(columnDefinition = "TEXT")
    private String altText;

    /**
     * Média retiré de la sélection sans être supprimé — utile pour un visuel déjà
     * diffusé qu'on ne veut plus réutiliser.
     */
    @Column(nullable = false)
    private Boolean archived = false;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public Media(String storageKey, String originalFilename, MediaFileType fileType, Long sizeBytes, String checksum) {
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.fileType = fileType;
        this.sizeBytes = sizeBytes;
        this.checksum = checksum;
    }
}
