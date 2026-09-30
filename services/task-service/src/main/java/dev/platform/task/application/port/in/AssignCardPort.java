package dev.platform.task.application.port.in;

import dev.platform.task.application.command.AssignCardCommand;
import dev.platform.task.domain.aggregate.CardAggregate;

// Cổng vào: gán người làm cho thẻ.
public interface AssignCardPort {

    CardAggregate assign(AssignCardCommand cmd);
}
