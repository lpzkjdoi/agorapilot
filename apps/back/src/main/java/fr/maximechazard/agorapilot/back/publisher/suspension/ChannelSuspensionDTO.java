package fr.maximechazard.agorapilot.back.publisher.suspension;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;

import java.time.LocalDateTime;

public record ChannelSuspensionDTO(DeliveryChannel channel, String reason, LocalDateTime suspendedAt) {

    static ChannelSuspensionDTO of(ChannelSuspension suspension) {
        return new ChannelSuspensionDTO(suspension.getChannel(), suspension.getReason(), suspension.getSuspendedAt());
    }
}
