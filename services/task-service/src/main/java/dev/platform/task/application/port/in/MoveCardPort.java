package dev.platform.task.application.port.in;

import dev.platform.task.application.command.MoveCardCommand;
import dev.platform.task.domain.aggregate.CardAggregate;

// Cổng vào: kéo thẻ qua cột / đổi vị trí.
public interface MoveCardPort {

    CardAggregate move(MoveCardCommand cmd);
}
