package dev.platform.board.infrastructure.adapter;

import dev.platform.board.application.port.out.MembershipSnapshotPort;
import dev.platform.board.infrastructure.snapshot.ProjectAuthorizationSnapshotSyncRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class MembershipSnapshotAdapter implements MembershipSnapshotPort {
    private final ProjectAuthorizationSnapshotSyncRecorder recorder;

    @Override
    public void record(
            UUID operationId,
            Long projectId,
            UUID userId,
            String status,
            String role,
            long membershipVersion,
            long authorizationVersion) {
        recorder.recordSnapshot(operationId, projectId, userId, status, role,
                membershipVersion, authorizationVersion);
    }
}
