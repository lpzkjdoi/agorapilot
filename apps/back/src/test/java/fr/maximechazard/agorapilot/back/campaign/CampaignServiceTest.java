package fr.maximechazard.agorapilot.back.campaign;

import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotCancelableException;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotClosableException;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotFoundException;
import fr.maximechazard.agorapilot.back.campaign.requests.CreateCampaignRequest;
import fr.maximechazard.agorapilot.back.campaign.requests.UpdateCampaignRequest;
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
import java.time.LocalDate;
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

    // Relatives à aujourd'hui : le statut se dérive de la date de début, et des
    // dates fixes faisaient basculer la campagne « à venir » en « active » une
    // fois le 1er octobre 2026 passé.
    private static final LocalDateTime START = LocalDate.now().plusDays(10).atTime(9, 0);
    private static final LocalDateTime END = START.plusDays(30);

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
    private static CreateCampaignRequest request(String name, LocalDateTime startDate) {
        CreateCampaignRequest request = new CreateCampaignRequest();
        set(request, "name", name);
        set(request, "description", "Une description");
        set(request, "startDate", startDate);
        set(request, "endDate", startDate == null ? null : END);
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
            when(campaignRepository.findAllByArchivedFalse()).thenReturn(List.of(campaign));

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
            when(campaignRepository.findAllByArchivedFalse()).thenReturn(List.of(campaign(4L, "Vide")));

            assertThat(service.getAll()).singleElement()
                                        .satisfies(dto -> assertThat(dto.publications()).isEmpty());
        }

        @Test
        void rendUneListeVideSansCampagne() {
            when(campaignRepository.findAllByArchivedFalse()).thenReturn(List.of());

            assertThat(service.getAll()).isEmpty();
        }
    }

    @Nested
    class Create {

        @Test
        void deriveLeStatutDeLaDateDeDebutALaCreation() {
            when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

            CampaignDTO sansDate = service.create(request("Brouillon", null));
            CampaignDTO aVenir = service.create(request("À venir", START));

            assertThat(sansDate.status()).isEqualTo(CampaignStatus.DRAFT);
            assertThat(aVenir.status()).isEqualTo(CampaignStatus.SCHEDULED);
        }

        /** La colonne est `NOT NULL`, la description facultative. */
        @Test
        void remplaceUneDescriptionAbsenteParUneChaineVide() {
            when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));
            CreateCampaignRequest request = request("Sans description", START);
            set(request, "description", null);

            assertThat(service.create(request).description()).isEmpty();
        }
    }

    /**
     * Le statut renvoyé se déduit des dates à la lecture : le statut stocké,
     * figé à la création, laissait une campagne « programmée » pour toujours.
     */
    @Nested
    class DerivedStatus {

        private CampaignStatus statusOf(Campaign campaign) {
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.of(campaign));
            return service.getById(3L).status();
        }

        @Test
        void passeEnCoursUneCampagneProgrammeeDontLaDateEstArrivee() {
            Campaign campaign = campaign(3L, "Démarrée");
            campaign.setStartDate(LocalDateTime.now().minusDays(1));
            campaign.setStatus(CampaignStatus.SCHEDULED);

            assertThat(statusOf(campaign)).isEqualTo(CampaignStatus.ACTIVE);
        }

        @Test
        void termineUneCampagneDontLaDateDeFinEstPassee() {
            Campaign campaign = campaign(3L, "Finie");
            campaign.setStartDate(LocalDateTime.now().minusDays(10));
            campaign.setEndDate(LocalDateTime.now().minusDays(1));
            campaign.setStatus(CampaignStatus.ACTIVE);

            assertThat(statusOf(campaign)).isEqualTo(CampaignStatus.COMPLETED);
        }

        @Test
        void resteEnCoursSansDateDeFin() {
            Campaign campaign = campaign(3L, "Permanente");
            campaign.setStartDate(LocalDateTime.now().minusDays(10));
            campaign.setEndDate(null);

            assertThat(statusOf(campaign)).isEqualTo(CampaignStatus.ACTIVE);
        }

        @Test
        void resteProgrammeeAvantSaDateDeDebut() {
            assertThat(statusOf(campaign(3L, "À venir"))).isEqualTo(CampaignStatus.SCHEDULED);
        }

        @Test
        void resteBrouillonSansDateDeDebut() {
            Campaign campaign = campaign(3L, "Brouillon");
            campaign.setStartDate(null);

            assertThat(statusOf(campaign)).isEqualTo(CampaignStatus.DRAFT);
        }

        /** Une annulation est une décision : les dates ne la défont pas. */
        @Test
        void conserveUneAnnulationMemeEnPleinePeriode() {
            Campaign campaign = campaign(3L, "Annulée");
            campaign.setStartDate(LocalDateTime.now().minusDays(1));
            campaign.setStatus(CampaignStatus.CANCELED);

            assertThat(statusOf(campaign)).isEqualTo(CampaignStatus.CANCELED);
        }

        @Test
        void signaleUneCampagneInconnueOuArchivee() {
            when(campaignRepository.findByIdAndArchivedFalse(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(404L)).isInstanceOf(CampaignNotFoundException.class);
        }
    }

    @Nested
    class Update {

        private UpdateCampaignRequest update(String name, LocalDateTime startDate, LocalDateTime endDate) {
            UpdateCampaignRequest request = new UpdateCampaignRequest();
            set(request, "name", name);
            set(request, "description", "Nouvelle description");
            set(request, "startDate", startDate);
            set(request, "endDate", endDate);
            return request;
        }

        private CampaignDTO apply(Campaign campaign, UpdateCampaignRequest request) {
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.of(campaign));
            when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
            return service.update(3L, request);
        }

        @Test
        void remplaceNomDescriptionEtPeriode() {
            LocalDateTime start = START.plusDays(5);
            LocalDateTime end = START.plusDays(60);

            CampaignDTO dto = apply(campaign(3L, "Ancien nom"), update("Nouveau nom", start, end));

            assertThat(dto.name()).isEqualTo("Nouveau nom");
            assertThat(dto.description()).isEqualTo("Nouvelle description");
            assertThat(dto.startDate()).isEqualTo(start);
            assertThat(dto.endDate()).isEqualTo(end);
        }

        /** Une campagne en cours garde sa date de début passée : la modifier ne doit pas l'interdire. */
        @Test
        void accepteUneDateDeDebutPasseePourUneCampagneEnCours() {
            LocalDateTime started = LocalDateTime.now().minusDays(10);

            CampaignDTO dto = apply(campaign(3L, "En cours"), update("En cours", started, null));

            assertThat(dto.startDate()).isEqualTo(started);
            assertThat(dto.status()).isEqualTo(CampaignStatus.ACTIVE);
        }

        @Test
        void retireLesDatesPourRepasserEnBrouillon() {
            CampaignDTO dto = apply(campaign(3L, "Programmée"), update("Programmée", null, null));

            assertThat(dto.status()).isEqualTo(CampaignStatus.DRAFT);
        }

        @Test
        void neRevientPasSurUneAnnulation() {
            Campaign campaign = campaign(3L, "Annulée");
            campaign.setStatus(CampaignStatus.CANCELED);

            CampaignDTO dto = apply(campaign, update("Annulée, renommée", START, END));

            assertThat(dto.name()).isEqualTo("Annulée, renommée");
            assertThat(dto.status()).isEqualTo(CampaignStatus.CANCELED);
        }

        @Test
        void signaleUneCampagneInconnue() {
            when(campaignRepository.findByIdAndArchivedFalse(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(404L, update("X", null, null)))
                    .isInstanceOf(CampaignNotFoundException.class);
            verify(campaignRepository, never()).save(any());
        }
    }

    @Nested
    class Cancel {

        private CampaignDTO cancel(Campaign campaign) {
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.of(campaign));
            when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));
            return service.cancel(3L);
        }

        @Test
        void annuleUneCampagneProgrammee() {
            assertThat(cancel(campaign(3L, "À venir")).status()).isEqualTo(CampaignStatus.CANCELED);
        }

        @Test
        void annuleUneCampagneEnCoursEnConservantSesDates() {
            Campaign campaign = campaign(3L, "En cours");
            LocalDateTime started = LocalDateTime.now().minusDays(2);
            campaign.setStartDate(started);

            CampaignDTO dto = cancel(campaign);

            assertThat(dto.status()).isEqualTo(CampaignStatus.CANCELED);
            assertThat(dto.startDate()).isEqualTo(started);
            assertThat(dto.endDate()).isEqualTo(END);
        }

        @Test
        void annuleUnBrouillon() {
            Campaign campaign = campaign(3L, "Brouillon");
            campaign.setStartDate(null);

            assertThat(cancel(campaign).status()).isEqualTo(CampaignStatus.CANCELED);
        }

        @Test
        void refuseUneCampagneDejaAnnulee() {
            Campaign campaign = campaign(3L, "Annulée");
            campaign.setStatus(CampaignStatus.CANCELED);
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> service.cancel(3L))
                    .isInstanceOf(CampaignNotCancelableException.class)
                    .hasMessageContaining("déjà annulée");
            verify(campaignRepository, never()).save(any());
        }

        @Test
        void refuseUneCampagneCloturee() {
            Campaign campaign = campaign(3L, "Clôturée");
            campaign.setStatus(CampaignStatus.COMPLETED);
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> service.cancel(3L))
                    .isInstanceOf(CampaignNotCancelableException.class)
                    .hasMessageContaining("terminée");
        }

        /** Terminée de fait, sans clôture : sa date de fin est passée. */
        @Test
        void refuseUneCampagneDontLaDateDeFinEstPassee() {
            Campaign campaign = campaign(3L, "Finie");
            campaign.setStartDate(LocalDateTime.now().minusDays(10));
            campaign.setEndDate(LocalDateTime.now().minusDays(1));
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> service.cancel(3L)).isInstanceOf(CampaignNotCancelableException.class);
        }
    }

    @Nested
    class Archive {

        @Test
        void archiveEtDetacheLesPublicationsSansLesSupprimer() {
            Campaign campaign = campaign(3L, "Marché de Noël");
            Publication publication = publication(8L, "Rendez-vous samedi");
            campaign.addPublication(publication);
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.of(campaign));

            service.archive(3L);

            ArgumentCaptor<Campaign> saved = ArgumentCaptor.forClass(Campaign.class);
            verify(campaignRepository).save(saved.capture());
            assertThat(saved.getValue().getArchived()).isTrue();
            assertThat(saved.getValue().getPublications()).isEmpty();
            assertThat(publication.getCampaign()).isNull();
            verify(campaignRepository, never()).delete(any());
        }

        @Test
        void signaleUneCampagneDejaArchivee() {
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.archive(3L)).isInstanceOf(CampaignNotFoundException.class);
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
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.of(campaign));
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
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.of(campaign));

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
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> service.close(3L))
                    .isInstanceOf(CampaignNotClosableException.class);
            verify(campaignRepository, never()).save(any());
        }

        @Test
        void refuseUneCampagneDejaCloturee() {
            Campaign campaign = started(LocalDateTime.now().minusDays(1));
            campaign.setStatus(CampaignStatus.COMPLETED);
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> service.close(3L))
                    .isInstanceOf(CampaignNotClosableException.class)
                    .hasMessageContaining("déjà clôturée");
            verify(campaignRepository, never()).save(any());
        }

        @Test
        void refuseUneCampagneAnnulee() {
            Campaign campaign = started(null);
            campaign.setStatus(CampaignStatus.CANCELED);
            when(campaignRepository.findByIdAndArchivedFalse(3L)).thenReturn(Optional.of(campaign));

            assertThatThrownBy(() -> service.close(3L))
                    .isInstanceOf(CampaignNotClosableException.class)
                    .hasMessageContaining("annulée");
            verify(campaignRepository, never()).save(any());
        }

        @Test
        void signaleUneCampagneInconnue() {
            when(campaignRepository.findByIdAndArchivedFalse(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.close(404L))
                    .isInstanceOf(CampaignNotFoundException.class);
        }
    }
}
