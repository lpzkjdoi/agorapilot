package fr.maximechazard.agorapilot.back.publication.dtos.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.DeliveryStatus;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.PublicationDelivery;
import fr.maximechazard.agorapilot.back.media.MediaMapper;
import fr.maximechazard.agorapilot.back.publication.PublicationMapper;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDeliveryDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Une occurrence et ses livraisons se référencent mutuellement côté entités.
 * Ces tests verrouillent le fait que le DTO, lui, ne porte pas ce cycle : c'est
 * ce qui a produit une réponse 200 tronquée sur
 * {@code GET /api/occurrences/weekly}, illisible par le front.
 */
class PublicationOccurrenceMapperTest {

    private final PublicationOccurrenceMapper mapper =
            new PublicationOccurrenceMapper(new PublicationMapper(new MediaMapper()), new PublicationDeliveryMapper());

    private static PublicationOccurrence occurrenceWithOneDelivery() {
        Publication publication = new Publication();
        publication.setId(1L);
        publication.setContent("Test création publication");
        publication.setStatus(PublicationStatus.VERIFIED);

        PublicationOccurrence occurrence = new PublicationOccurrence();
        occurrence.setId(1L);
        occurrence.setScheduledAt(LocalDateTime.of(2026, 8, 22, 18, 20, 5));
        occurrence.setPublication(publication);

        PublicationDelivery delivery = new PublicationDelivery();
        delivery.setId(1L);
        delivery.setChannel(DeliveryChannel.FACEBOOK);
        delivery.setStatus(DeliveryStatus.PUBLISHED);
        delivery.setPublishedAt(LocalDateTime.of(2026, 8, 22, 18, 20, 5));
        delivery.setExternalId("1144547398751185_122106824271408514");

        // Positionne la référence retour delivery → occurrence, comme le fait
        // Hibernate au chargement : c'est elle qui bouclait à la sérialisation.
        occurrence.addDelivery(delivery);

        return occurrence;
    }

    @Test
    void maps_deliveries_to_dtos_and_not_to_entities() {
        PublicationOccurrenceDTO dto = mapper.toDTO(occurrenceWithOneDelivery());

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.publication().content()).isEqualTo("Test création publication");
        assertThat(dto.deliveries())
                .singleElement()
                .isInstanceOf(PublicationDeliveryDTO.class)
                .satisfies(delivery -> {
                    assertThat(delivery.id()).isEqualTo(1L);
                    assertThat(delivery.occurrenceId()).isEqualTo(1L);
                    assertThat(delivery.channel()).isEqualTo(DeliveryChannel.FACEBOOK);
                    assertThat(delivery.status()).isEqualTo(DeliveryStatus.PUBLISHED);
                    assertThat(delivery.externalId()).isEqualTo("1144547398751185_122106824271408514");
                });
    }

    @Test
    void serialises_without_recursing_through_the_back_reference() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        // Sur la version fautive, cet appel partait en récursion infinie.
        String json = objectMapper.writeValueAsString(mapper.toDTO(occurrenceWithOneDelivery()));

        assertThat(json).doesNotContain("\"occurrence\"");
        assertThat(json).contains("\"occurrenceId\":1");
        // La ré-analyse échouerait sur un JSON tronqué, exactement comme côté front.
        assertThat(objectMapper.readTree(json).at("/deliveries/0/channel").asText())
                .isEqualTo("FACEBOOK");
    }

    @Test
    void maps_an_occurrence_without_delivery_to_an_empty_list() {
        PublicationOccurrence occurrence = occurrenceWithOneDelivery();
        occurrence.getDeliveries().clear();

        assertThat(mapper.toDTO(occurrence).deliveries()).isEmpty();
    }
}
