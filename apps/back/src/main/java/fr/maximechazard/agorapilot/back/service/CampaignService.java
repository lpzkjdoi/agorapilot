package fr.maximechazard.agorapilot.back.service;

import fr.maximechazard.agorapilot.back.model.Campaign;
import fr.maximechazard.agorapilot.back.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CampaignService {
    private final CampaignRepository campaignRepository;

    public Iterable<Campaign> getAll() {
        return campaignRepository.findAll();
    }
}
