package fr.maximechazard.agorapilot.back.campaign;

import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotClosableException;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotFoundException;
import fr.maximechazard.agorapilot.back.campaign.requests.CreateCampaignRequest;
import fr.maximechazard.agorapilot.back.media.MediaMapper;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.PublicationMapper;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre la lecture et la création des campagnes. La lecture renvoie des DTO :
 * l'entité {@link Campaign} porte ses publications, qui référencent en retour
 * leur campagne, et Jackson sérialiserait le cycle sans fin.
 */
@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 31, 23, 59);

    @Mock
    private CampaignRepository campaignRepository;

    private CampaignService service;

    @BeforeEach
    void setUp() {
        service = new CampaignService(campaignRepository, new CampaignMapper(new PublicationMapper(new MediaMapper())));
    }

    private static Campaign campaign(long id, String name) {
        Campaign campaign = new Campaign(name, "Une description", START, END);
        campaign.setId(id);
        return campaign;
    }

    private static Publication publication(long id, String content) {
        Publication publication = new Publication(content, PublicationStatus.VERIFIED);
        publication.setId(id);
        return publication;
    }

    /** {@link CreateCampaignRequest} n'expose que des getters : Spring la peuple par réflexion. */
    private static CreateCampaignRequest request(String name, LocalDateTime startDate, String... contents) {
        CreateCampaignRequest request = new CreateCampaignRequest();
        set(request, "name", name);
        set(request, "description", "Une description");
        set(request, "startDate", startDate);
        set(request, "endDate", startDate == null ? null : END);
        set(request, "publications", Arrays.stream(contents).map(content -> {
            CreatePublicationRequest publication = new CreatePublicationRequest();
            set(publication, "content", content);
            set(publication, "status", PublicationStatus.VERIFIED);
            return publication;
        }).toList());
        return request;
    }

    private static void set(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    class GetAll {

        @Test
        void renvoieDesDTOEtNonLesEntites() {
            Campaign campaign = campaign(3L, "Marché de Noël");
            campaign.addPublication(publication(8L, "Rendez-vous samedi"));
            when(campaignRepository.findAll()).thenReturn(List.of(campaign));

            List<CampaignDTO> campaigns = service.getAll();

            assertThat(campaigns).singleElement().satisfies(dto -> {
                assertThat(dto.id()).isEqualTo(3L);
                assertThat(dto.name()).isEqualTo("Marché de Noël");
                assertThat(dto.startDate()).isEqualTo(START);
                assertThat(dto.endDate()).isEqualTo(END);
                assertThat(dto.status()).isEqualTo(CampaignStatus.SCHEDULED);
                assertThat(dto.publications()).singleElement().satisfies(p -> {
                    assertThat(p.id()).isEqualTo(8L);
                    assertThat(p.content()).isEqualTo("Rendez-vous samedi");
                });
            });
        }

        @Test
        void rendUneCampagneSansPublication() {
            when(campaignRepository.findAll()).thenReturn(List.of(campaign(4L, "Vide")));

            assertThat(service.getAll()).singleElement()
                                        .satisfies(dto -> assertThat(dto.publications()).isEmpty());
        }

        @Test
        void rendUneListeVideSansCampagne() {
            when(campaignRepository.findAll()).thenReturn(List.of());

            assertThat(service.getAll()).isEmpty();
        }
    }

    @Nested
    class Create {

        @Test
        void rattacheLesPublicationsALaCampagne() {
            when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> {
                Campaign saved = invocation.getArgument(0);
                saved.setId(5L);
                return saved;
            });

            CampaignDTO dto = service.create(request("Marché de Noël", START, "Rendez-vous samedi"));

            ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
            verify(campaignRepository).save(captor.capture());
            assertThat(captor.getValue().getPublications()).singleElement()
                                                           .satisfies(p -> assertThat(p.getCampaign()).isSameAs(captor.getValue()));
            assertThat(dto.id()).isEqualTo(5L);
            assertThat(dto.publications()).hasSize(1);
        }

        @Test
        void deriveLeStatutDeLaDateDeDebutALaCreation() {
            when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

            CampaignDTO sansDate = service.create(request("Brouillon", null));
            CampaignDTO aVenir = service.create(request("À venir", START));

            assertThat(sansDate.status()).isEqualTo(CampaignStatus.DRAFT);
            assertThat(aVenir.status()).isEqualTo(CampaignStatus.SCHEDULED);
        }
    }

    @Nested
    class Close {

        private Campaign started(LocalDateTime endDate) {
            Campaign campaign = campaign(3L, "En cours");
            campaign.setStartDate(LocalDateTime.now().minusDays(10));
            campaign.setEndDate(endDate);
            campaign.setStatus(CampaignStatus.ACTIVE);
            return campaign;
        }

        private CampaignDTO close(Campaign campaign) {
            when(campaignRepository.findById(3L)).thenReturn(Optional.of(campaign));
            when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
            return service.close(3L);
        }

        @Test
        void arreteLaCampagneMaintenant() {
            Campaign campaign = started(null);

            CampaignDTO dto = close(campaign);

            assertThat(dto.status()).isEqualTo(CampaignStatus.COMPLETED);
            assertThat(dto.endDate()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
        }

        /** Clôture anticipée : la fin prévue plus tard est ramenée au présent. */
        @Test
        void avanceUneDateDeFinFuture() {
            Campaign campaign = started(LocalDateTime.now().plusDays(30));

            CampaignDTO dto = close(campaign);

            assertThat(dto.endDate()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
        }

        /** La campagne s'est arrêtée ce jour-là : la clôture ne fait que l'acter. */
        @Test
        void conserveUneDateDeFinDejaPassee() {
            LocalDateTime passee = LocalDateTime.now().minusDays(3);
            Campaign campaign = started(passee);

            CampaignDTO dto = close(campaign);

            assertThat(dto.endDate()).isEqualTo(passee);
            assertThat(dto.status()).isEqualTo(CampaignStatus.COMPLETED);
        }

        /**
         * Le statut stocké ne bascule jamais de `SCHEDULED` à `ACTIVE` : seule la
         * date de début dit si la campagne a commencé.
         */
        @Test
        void clotureUneCampagneResteeScheduledDontLaDateEstPassee() {
            Campaign campaign = started(null);
            campaign.setStatus(CampaignStatus.SCHEDULED);

            assertThat(close(campaign).status()).isEqualTo(CampaignStatus.COMPLETED);
        }

        @Test
        void refuseUneCampagneQuiNAPasCommence() {
            Campaign campaign = campaign(3L, "À venir");
            campaign.setStartDate(LocalDateTime.now().plusDays(1));
            campaign.setStatus(CampaignStatus.SCHEDULED);
            when(campaignRepository.findById(3L)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> service.close(3L))
                    .isInstanceOf(CampaignNotClosableException.class)
                    .hasMessageContaining("n'a pas encore commencé");
            verify(campaignRepository, never()).save(any());
        }

        @Test
        void refuseUneCampagneSansDateDeDebut() {
            Campaign campaign = campaign(3L, "Brouillon");
            campaign.setStartDate(null);
            campaign.setStatus(CampaignStatus.DRAFT);
            when(campaignRepository.findById(3L)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> service.close(3L))
                    .isInstanceOf(CampaignNotClosableException.class);
            verify(campaignRepository, never()).save(any());
        }

        @Test
        void refuseUneCampagneDejaCloturee() {
            Campaign campaign = started(LocalDateTime.now().minusDays(1));
            campaign.setStatus(CampaignStatus.COMPLETED);
            when(campaignRepository.findById(3L)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> service.close(3L))
                    .isInstanceOf(CampaignNotClosableException.class)
                    .hasMessageContaining("déjà clôturée");
            verify(campaignRepository, never()).save(any());
        }

        @Test
        void refuseUneCampagneAnnulee() {
            Campaign campaign = started(null);
            campaign.setStatus(CampaignStatus.CANCELED);
            when(campaignRepository.findById(3L)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> service.close(3L))
                    .isInstanceOf(CampaignNotClosableException.class)
                    .hasMessageContaining("annulée");
            verify(campaignRepository, never()).save(any());
        }

        @Test
        void signaleUneCampagneInconnue() {
            when(campaignRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.close(404L))
                    .isInstanceOf(CampaignNotFoundException.class);
        }
    }
}
