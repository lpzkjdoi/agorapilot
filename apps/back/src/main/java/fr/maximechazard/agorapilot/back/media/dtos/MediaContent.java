package fr.maximechazard.agorapilot.back.media.dtos;

import org.springframework.core.io.Resource;

/**
 * Le binaire d'un média et les métadonnées nécessaires pour l'en-tête de la
 * réponse — évite au contrôleur un second aller-retour en base.
 */
public record MediaContent(MediaDTO metadata, Resource resource) {
}
