package fr.maximechazard.agorapilot.back.publication.dtos;

/**
 * La campagne telle qu'une publication la porte : son identité, rien de plus.
 * <p>
 * Forme réduite volontairement — un {@code CampaignDTO} complet embarquerait
 * ses publications, dont celle-ci, et Jackson boucherait.
 */
public record PublicationCampaignDTO(
        Long id,
        String name
) {
}
