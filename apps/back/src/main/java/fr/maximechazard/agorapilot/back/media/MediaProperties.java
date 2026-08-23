package fr.maximechazard.agorapilot.back.media;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration de la médiathèque.
 * <p>
 * La taille maximale n'apparaît pas ici : elle est déjà portée par
 * {@code spring.servlet.multipart.max-file-size}, qui coupe la requête avant même
 * d'atteindre le contrôleur. La dupliquer exposerait à ce que les deux valeurs
 * divergent.
 *
 * @param root                 racine du stockage sur disque, absolue en production
 * @param allowedContentTypes  liste blanche des formats acceptés à l'upload
 */
@ConfigurationProperties(prefix = "agorapilot.media")
public record MediaProperties(
        String root,
        List<String> allowedContentTypes
) {
}
