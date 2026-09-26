package fr.maximechazard.agorapilot.back.publication.requests;

import lombok.Getter;

@Getter
@SuppressWarnings("unused")
public class SetPublicationCampaignRequest {

    /** Campagne de rattachement ; {@code null} détache la publication. */
    private Long campaignId;
}
