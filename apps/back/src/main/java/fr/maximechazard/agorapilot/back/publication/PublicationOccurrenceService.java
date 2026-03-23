package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.mappers.PublicationOccurrenceMapper;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationOccurrenceRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class PublicationOccurrenceService {
    private final PublicationRepository publicationRepository;
    private final PublicationOccurrenceRepository publicationOccurrenceRepository;
    private final PublicationOccurrenceMapper publicationOccurrenceMapper;

    public PublicationOccurrence create(CreatePublicationOccurrenceRequest request) {
        Publication publication = publicationRepository.findById(request.getPublicationId())
                .orElseThrow(() -> new PublicationNotFoundException("Publication with id " + request.getPublicationId() + " does not exist."));

        PublicationOccurrence occurrence = new PublicationOccurrence();

        for (DeliveryChannel channel : request.getChannels()) {
            PublicationDelivery delivery = new PublicationDelivery();
            delivery.setChannel(channel);
            occurrence.addDelivery(delivery);
        }

        occurrence.setPublication(publication);
        occurrence.setScheduledAt(request.getScheduledAt());

        return publicationOccurrenceRepository.save(occurrence);
    }

    public Map<String, List<PublicationOccurrenceDTO>> getWeeklyOccurrences() {
        LocalDateTime firstDay = LocalDate.now().atStartOfDay();
        LocalDateTime lastDay = LocalDate.now().plusDays(6).atStartOfDay();
        List<PublicationOccurrence> occurrences = publicationOccurrenceRepository.findAllByScheduledAtBetween(firstDay, lastDay);
        List<PublicationOccurrenceDTO> occurrencesDto = occurrences.stream().map(publicationOccurrenceMapper::toDTO).toList();
        return createMap(occurrencesDto);
    }

    private Map<String, List<PublicationOccurrenceDTO>> createMap(List<PublicationOccurrenceDTO> occurrences) {
        Map<String, List<PublicationOccurrenceDTO>> map = new TreeMap<>();

        for (int i = 0; i <= 6; i++) {
            LocalDateTime day = LocalDate.now().atStartOfDay().plusDays(i);
            List<PublicationOccurrenceDTO> list = occurrences.stream().filter(occurrence -> occurrence.scheduledAt().getDayOfMonth() == day.getDayOfMonth()).toList();
            map.put(day.toString(), list);
        }

        return map;
    }
}
