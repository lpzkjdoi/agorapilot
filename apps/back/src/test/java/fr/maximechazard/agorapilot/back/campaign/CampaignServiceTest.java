package fr.maximechazard.agorapilot.back.campaign;

import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        void deriveLeStatutDeLaDateDeDebut() {
            when(campaignRepository.save(any(Campaign.class))).thenAnswer(invocation -> invocation.getArgument(0));

            CampaignDTO sansDate = service.create(request("Brouillon", null));
            CampaignDTO aVenir = service.create(request("À venir", START));

            assertThat(sansDate.status()).isEqualTo(CampaignStatus.DRAFT);
            assertThat(aVenir.status()).isEqualTo(CampaignStatus.SCHEDULED);
        }
    }
}
