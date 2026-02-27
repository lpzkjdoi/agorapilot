package fr.maximechazard.agorapilot.back.service;

import fr.maximechazard.agorapilot.back.PublicationStatus;
import fr.maximechazard.agorapilot.back.dto.PublicationDTO;
import fr.maximechazard.agorapilot.back.dto.mapper.PublicationMapper;
import fr.maximechazard.agorapilot.back.model.Publication;
import fr.maximechazard.agorapilot.back.repository.PublicationRepository;
import fr.maximechazard.agorapilot.back.request.CreatePublicationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicationServiceTest {

    @Mock
    private PublicationRepository publicationRepository;

    @Mock
    private PublicationMapper mapper;

    @InjectMocks
    private PublicationService publicationService;

    @Test
    void create_shouldSavePublicationAndReturnDto() {
        // Arrange
        LocalDateTime scheduledAt = LocalDateTime.now().plusDays(10);
        LocalDateTime publishedAt = null;
        LocalDateTime endAt = LocalDateTime.now().plusDays(20);

        CreatePublicationRequest request = mock(CreatePublicationRequest.class);
        when(request.getContent()).thenReturn("Hello\nWorld");
        when(request.getScheduledAt()).thenReturn(scheduledAt);
        when(request.getPublishedAt()).thenReturn(publishedAt);
        when(request.getEndAt()).thenReturn(endAt);

        ArgumentCaptor<Publication> publicationCaptor = ArgumentCaptor.forClass(Publication.class);

        Publication saved = new Publication("Hello\nWorld", scheduledAt, publishedAt, endAt);
        saved.setId(1L);
        when(publicationRepository.save(any(Publication.class))).thenReturn(saved);

        PublicationDTO dto = mock(PublicationDTO.class);
        when(mapper.toDTO(saved)).thenReturn(dto);

        // Act
        PublicationDTO result = publicationService.create(request);

        // Assert
        assertThat(result).isSameAs(dto);
        verify(publicationRepository, times(1)).save(publicationCaptor.capture());

        Publication toSave = publicationCaptor.getValue();
        assertThat(toSave.getContent()).isEqualTo("Hello\nWorld");
        assertThat(toSave.getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(toSave.getPublishedAt()).isNull();
        assertThat(toSave.getEndAt()).isEqualTo(endAt);

        assertThat(toSave.getStatus()).isEqualTo(PublicationStatus.DRAFT);

        verify(mapper, times(1)).toDTO(saved);
        verifyNoMoreInteractions(mapper, publicationRepository);
    }

    @Test
    void create_shouldAllowAllOptionalDatesToBeNull() {
        // Arrange
        CreatePublicationRequest request = mock(CreatePublicationRequest.class);
        when(request.getContent()).thenReturn("Content");
        when(request.getScheduledAt()).thenReturn(null);
        when(request.getPublishedAt()).thenReturn(null);
        when(request.getEndAt()).thenReturn(null);

        Publication saved = new Publication("Content", null, null, null);
        saved.setId(1L);
        when(publicationRepository.save(any(Publication.class))).thenReturn(saved);

        PublicationDTO dto = mock(PublicationDTO.class);
        when(mapper.toDTO(saved)).thenReturn(dto);

        // Act
        PublicationDTO result = publicationService.create(request);

        // Assert
        assertThat(result).isSameAs(dto);

        ArgumentCaptor<Publication> captor = ArgumentCaptor.forClass(Publication.class);
        verify(publicationRepository).save(captor.capture());

        Publication toSave = captor.getValue();
        assertThat(toSave.getScheduledAt()).isNull();
        assertThat(toSave.getPublishedAt()).isNull();
        assertThat(toSave.getEndAt()).isNull();
        assertThat(toSave.getStatus()).isEqualTo(PublicationStatus.DRAFT);

        verify(mapper).toDTO(saved);
        verifyNoMoreInteractions(mapper, publicationRepository);
    }
}