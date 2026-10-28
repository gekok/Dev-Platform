package dev.platform.shared.messaging.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class InboxEventId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String consumerName;
    private UUID eventId;
}
