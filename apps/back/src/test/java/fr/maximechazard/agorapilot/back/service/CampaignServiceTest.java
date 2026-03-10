package fr.maximechazard.agorapilot.back.service;

import fr.maximechazard.agorapilot.back.campaign.Campaign;
import fr.maximechazard.agorapilot.back.campaign.CampaignMapper;
import fr.maximechazard.agorapilot.back.campaign.CampaignRepository;
import fr.maximechazard.agorapilot.back.campaign.CampaignService;
import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
import fr.maximechazard.agorapilot.back.campaign.requests.CreateCampaignRequest;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CampaignMapper mapper;

    @InjectMocks
    private CampaignService campaignService;

    @Test
    void create_shouldSaveCampaignWithoutPublications_whenPublicationsEmpty() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(10);

        CreateCampaignRequest request = mock(CreateCampaignRequest.class);
        when(request.getName()).thenReturn("Rando du 25/04");
        when(request.getDescription()).thenReturn("Hihi");
        when(request.getStartDate()).thenReturn(start);
        when(request.getEndDate()).thenReturn(end);
        when(request.getPublications()).thenReturn(List.of());

        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);

        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignDTO dto = mock(CampaignDTO.class);
        when(mapper.toDTO(any(Campaign.class))).thenReturn(dto);

        // Act
        CampaignDTO result = campaignService.create(request);

        // Assert
        assertThat(result).isSameAs(dto);

        verify(campaignRepository).save(captor.capture());
        Campaign saved = captor.getValue();

        assertThat(saved.getName()).isEqualTo("Rando du 25/04");
        assertThat(saved.getDescription()).isEqualTo("Hihi");
        assertThat(saved.getStartDate()).isEqualTo(start);
        assertThat(saved.getEndDate()).isEqualTo(end);

        assertThat(saved.getPublications()).isNotNull();
        assertThat(saved.getPublications()).isEmpty();

        verify(mapper).toDTO(any(Campaign.class));
        verifyNoMoreInteractions(campaignRepository, mapper);
    }

    @Test
    void create_shouldSaveCampaignWithPublications_whenProvided() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(10);

        CreatePublicationRequest pubReq = mock(CreatePublicationRequest.class);
        when(pubReq.getContent()).thenReturn("Ça y est la rando arrive !");
        when(pubReq.getScheduledAt()).thenReturn(LocalDateTime.now().plusDays(2));
        when(pubReq.getPublishedAt()).thenReturn(null);
        when(pubReq.getEndAt()).thenReturn(null);

        CreateCampaignRequest request = mock(CreateCampaignRequest.class);
        when(request.getName()).thenReturn("Rando du 25/04");
        when(request.getDescription()).thenReturn("Hihi");
        when(request.getStartDate()).thenReturn(start);
        when(request.getEndDate()).thenReturn(end);
        when(request.getPublications()).thenReturn(List.of(pubReq));

        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);

        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignDTO dto = mock(CampaignDTO.class);
        when(mapper.toDTO(any(Campaign.class))).thenReturn(dto);

        // Act
        CampaignDTO result = campaignService.create(request);

        // Assert
        assertThat(result).isSameAs(dto);

        verify(campaignRepository).save(captor.capture());
        Campaign saved = captor.getValue();

        assertThat(saved.getPublications()).hasSize(1);

        Publication p = saved.getPublications().getFirst();
        assertThat(p.getContent()).isEqualTo("Ça y est la rando arrive !");
        assertThat(p.getScheduledAt()).isNotNull();

        assertThat(p.getCampaign()).isSameAs(saved);

        verify(mapper).toDTO(any(Campaign.class));
        verifyNoMoreInteractions(campaignRepository, mapper);
    }

    @Test
    void create_shouldHandleNullPublicationsList() {
        // Arrange
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(10);

        CreateCampaignRequest request = mock(CreateCampaignRequest.class);
        when(request.getName()).thenReturn("Rando du 25/04");
        when(request.getDescription()).thenReturn("Hihi");
        when(request.getStartDate()).thenReturn(start);
        when(request.getEndDate()).thenReturn(end);
        when(request.getPublications()).thenReturn(null);

        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        when(campaignRepository.save(any(Campaign.class))).thenAnswer(inv -> inv.getArgument(0));

        CampaignDTO dto = mock(CampaignDTO.class);
        when(mapper.toDTO(any(Campaign.class))).thenReturn(dto);

        // Act
        CampaignDTO result = Assertions.assertDoesNotThrow(() -> campaignService.create(request));

        // Assert
        assertThat(result).isSameAs(dto);

        verify(campaignRepository).save(captor.capture());
        Campaign saved = captor.getValue();

        assertThat(saved.getPublications()).isNotNull();
        assertThat(saved.getPublications()).isEmpty();
    }
}