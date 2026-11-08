package dev.platform.task.application.service;

import dev.platform.task.application.port.in.GetColumnTaskCountPort;
import dev.platform.task.application.port.out.ColumnTaskCountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetColumnTaskCountService implements GetColumnTaskCountPort {
    private final ColumnTaskCountPort taskCounts;

    @Override
    public long countByColumnId(Long columnId) {
        return taskCounts.countByColumnId(columnId);
    }
}
