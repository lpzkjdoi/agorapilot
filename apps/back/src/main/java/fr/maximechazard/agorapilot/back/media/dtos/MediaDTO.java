package fr.maximechazard.agorapilot.back.media.dtos;

import java.time.LocalDateTime;

/**
 * Représentation d'un média hors de l'API.
 * <p>
 * Aucune URL n'y figure : le front la dérive de l'identifiant
 * ({@code /api/medias/{id}/file}). Une URL absolue stockée en base deviendrait
 * fausse au premier changement de nom de domaine.
 */
public record MediaDTO(
        Long id,
        String title,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        Integer width,
        Integer height,
        String altText,
        String checksum,
        Boolean archived,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
