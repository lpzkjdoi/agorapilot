package fr.maximechazard.agorapilot.back.media.dtos;

/**
 * Forme réduite d'un média, embarquée dans une publication.
 * <p>
 * Le {@link MediaDTO} complet porte des métadonnées (empreinte, dates,
 * archivage) qui n'ont pas d'usage dans le contexte d'une publication et
 * alourdiraient chaque élément de la liste.
 */
public record MediaSummaryDTO(
        Long id,
        String title,
        String originalFilename,
        String contentType,
        Integer width,
        Integer height,
        String altText
) {
}
