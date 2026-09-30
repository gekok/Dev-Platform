package dev.platform.task.application.port.in;

import dev.platform.task.application.command.CreateCardCommand;
import dev.platform.task.domain.aggregate.CardAggregate;

// Cổng vào: tạo thẻ mới.
public interface CreateCardPort {

    CardAggregate create(CreateCardCommand cmd);
}
