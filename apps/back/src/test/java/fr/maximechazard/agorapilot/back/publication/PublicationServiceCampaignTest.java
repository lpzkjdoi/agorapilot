package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.campaign.Campaign;
import fr.maximechazard.agorapilot.back.campaign.CampaignRepository;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotFoundException;
import fr.maximechazard.agorapilot.back.media.MediaMapper;
import fr.maximechazard.agorapilot.back.media.MediaRepository;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre le rattachement d'une publication à une campagne.
 */
@ExtendWith(MockitoExtension.class)
class PublicationServiceCampaignTest {

    private static final long PUBLICATION_ID = 7L;
    private static final long CAMPAIGN_ID = 3L;

    @Mock
    private PublicationRepository publicationRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private CampaignRepository campaignRepository;

    private PublicationService service;
    private Publication publication;
    private Campaign campaign;

    @BeforeEach
    void setUp() {
        service = new PublicationService(
                publicationRepository, mediaRepository, campaignRepository, new PublicationMapper(new MediaMapper()));

        publication = new Publication("Marché de producteurs", PublicationStatus.DRAFT);
        publication.setId(PUBLICATION_ID);

        campaign = new Campaign("Marché de Noël", "Les annonces", LocalDateTime.now().plusDays(1), null);
        campaign.setId(CAMPAIGN_ID);
    }

    private void givenPublication() {
        when(publicationRepository.findById(PUBLICATION_ID)).thenReturn(Optional.of(publication));
    }

    private void givenSaveEchoesBack() {
        when(publicationRepository.save(any(Publication.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void rattacheLaPublicationALaCampagne() {
        givenPublication();
        givenSaveEchoesBack();
        when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign));

        PublicationDTO dto = service.setCampaign(PUBLICATION_ID, CAMPAIGN_ID);

        assertThat(publication.getCampaign()).isSameAs(campaign);
        assertThat(dto.campaign()).isNotNull();
        assertThat(dto.campaign().id()).isEqualTo(CAMPAIGN_ID);
        assertThat(dto.campaign().name()).isEqualTo("Marché de Noël");
    }

    @Test
    void detacheLaPublicationAvecUnIdentifiantNul() {
        publication.setCampaign(campaign);
        givenPublication();
        givenSaveEchoesBack();

        PublicationDTO dto = service.setCampaign(PUBLICATION_ID, null);

        assertThat(publication.getCampaign()).isNull();
        assertThat(dto.campaign()).isNull();
        verify(campaignRepository, never()).findById(any());
    }

    @Test
    void remplaceLaCampagnePrecedente() {
        Campaign previous = new Campaign("Budget participatif", "", null, null);
        previous.setId(99L);
        publication.setCampaign(previous);
        givenPublication();
        givenSaveEchoesBack();
        when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign));

        assertThat(service.setCampaign(PUBLICATION_ID, CAMPAIGN_ID).campaign().id()).isEqualTo(CAMPAIGN_ID);
    }

    /**
     * Seule la publication est enregistrée : elle porte la clé étrangère, et
     * rendre la campagne sale la ferait revalider sans raison.
     */
    @Test
    void nEnregistreQueLaPublication() {
        givenPublication();
        givenSaveEchoesBack();
        when(campaignRepository.findById(CAMPAIGN_ID)).thenReturn(Optional.of(campaign));

        service.setCampaign(PUBLICATION_ID, CAMPAIGN_ID);

        verify(publicationRepository).save(publication);
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void signaleUnePublicationInconnue() {
        when(publicationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setCampaign(404L, CAMPAIGN_ID))
                .isInstanceOf(PublicationNotFoundException.class);
        verify(publicationRepository, never()).save(any());
    }

    @Test
    void signaleUneCampagneInconnue() {
        givenPublication();
        when(campaignRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setCampaign(PUBLICATION_ID, 404L))
                .isInstanceOf(CampaignNotFoundException.class);
        assertThat(publication.getCampaign()).isNull();
        verify(publicationRepository, never()).save(any());
    }
}
