package dev.platform.task.infrastructure.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.platform.task.assignmentcleanup.MembershipAssignmentLock;
import dev.platform.task.assignmentcleanup.persistence.entity.MembershipAssignmentFenceId;
import dev.platform.task.assignmentcleanup.persistence.repository.MembershipAssignmentFenceJpaRepository;
import dev.platform.task.application.port.out.SaveCardPort;
import dev.platform.task.domain.aggregate.CardAggregate;
import dev.platform.task.domain.aggregate.CardAggregate.CommentValue;
import dev.platform.task.domain.aggregate.CardAggregate.Status;
import dev.platform.task.infrastructure.persistence.entity.CardEntity;
import dev.platform.task.infrastructure.persistence.entity.TaskAssigneeEntity;
import dev.platform.task.infrastructure.persistence.repository.CardJpaRepository;
import dev.platform.task.infrastructure.persistence.repository.TaskAssigneeJpaRepository;
import lombok.RequiredArgsConstructor;

// Adapter JPA cho cổng ra SaveCardPort.
@Component
@RequiredArgsConstructor
public class SaveCardAdapter implements SaveCardPort {

    private final CardJpaRepository cardJpaRepository;
    private final TaskAssigneeJpaRepository taskAssigneeJpaRepository;
    private final MembershipAssignmentFenceJpaRepository fenceRepository;
    private final MembershipAssignmentLock membershipAssignmentLock;

    @Override
    @Transactional
    public CardAggregate save(CardAggregate card) {
        if (card.getProjectId() == null) {
            throw new IllegalArgumentException("projectId khong duoc null");
        }
        TaskAssigneeEntity existingAssignee = null;
        if (card.getId() != null) {
            cardJpaRepository.lockByIdAndProjectId(card.getId(), card.getProjectId())
                    .orElseThrow(() -> new IllegalStateException("CARD_NOT_FOUND"));
        }
        if (card.isAssigneeChangeRequested()) {
            validateAssignment(card);
            if (card.getId() != null) {
                existingAssignee = primaryAssignee(card.getId());
            }
        }

        CardEntity saved = cardJpaRepository.save(toEntity(card));
        if (card.isAssigneeChangeRequested()) {
            persistAssignment(saved, card, existingAssignee);
        }
        return toAggregate(saved, primaryAssignee(saved.getId()));
    }

    @Override
    public Optional<CardAggregate> findById(Long projectId, Long id) {
        return cardJpaRepository.findByIdAndProjectId(id, projectId)
                .map(entity -> toAggregate(entity, primaryAssignee(entity.getId())));
    }

    @Override
    public List<CardAggregate> findByProjectIdAndBoardIdOrderByPosition(Long projectId, Long boardId) {
        return toAggregates(cardJpaRepository.findByProjectIdAndBoardIdOrderByPositionAsc(projectId, boardId));
    }

    @Override
    public List<CardAggregate> findByProjectIdAndBoardIdAndColumnId(Long projectId, Long boardId, Long columnId) {
        return toAggregates(cardJpaRepository.findByProjectIdAndBoardIdAndColumnIdOrderByPositionAsc(
                projectId, boardId, columnId));
    }

    // Chuyển Aggregate sang Entity để lưu.
    private CardEntity toEntity(CardAggregate card) {
        return CardEntity.builder()
                .id(card.getId())
                .projectId(card.getProjectId())
                .boardId(card.getBoardId())
                .columnId(card.getColumnId())
                .title(card.getTitle())
                .description(card.getDescription())
                .status(card.getStatus() == null ? "TODO" : card.getStatus().name())
                .position(card.getPosition())
                .dueDate(card.getDueDate())
                .build();
    }

    // Chuyển Entity sang Aggregate để trả về (chưa nạp comments kỳ này).
    private CardAggregate toAggregate(CardEntity entity, TaskAssigneeEntity assignee) {
        CardAggregate card = new CardAggregate();
        card.setId(entity.getId());
        card.setProjectId(entity.getProjectId());
        card.setBoardId(entity.getBoardId());
        card.setColumnId(entity.getColumnId());
        card.setTitle(entity.getTitle());
        card.setDescription(entity.getDescription());
        card.setStatus(parseStatus(entity.getStatus()));
        card.setPosition(entity.getPosition());
        card.setAssigneeId(assignee == null ? null : assignee.getId().getUserId());
        card.setAssigneeMembershipVersion(assignee == null ? null : assignee.getMembershipVersion());
        card.setAssigneeAssignedByUserId(assignee == null ? null : assignee.getAssignedByUserId());
        card.setDueDate(entity.getDueDate());
        card.setComments(new java.util.ArrayList<CommentValue>());
        return card;
    }

    private List<CardAggregate> toAggregates(List<CardEntity> cards) {
        if (cards.isEmpty()) {
            return List.of();
        }
        Map<Long, TaskAssigneeEntity> primaryAssignees = taskAssigneeJpaRepository
                .findByIdTaskIdIn(cards.stream().map(CardEntity::getId).toList())
                .stream()
                .filter(TaskAssigneeEntity::isPrimary)
                .collect(Collectors.toMap(assignee -> assignee.getId().getTaskId(), assignee -> assignee));
        return cards.stream().map(card -> toAggregate(card, primaryAssignees.get(card.getId()))).toList();
    }

    private TaskAssigneeEntity primaryAssignee(Long taskId) {
        return taskAssigneeJpaRepository.findFirstByIdTaskIdAndPrimaryTrue(taskId).orElse(null);
    }

    private void persistAssignment(
            CardEntity cardEntity, CardAggregate card, TaskAssigneeEntity existingAssignee) {
        if (card.getAssigneeId() == null) {
            taskAssigneeJpaRepository.deleteByIdTaskId(cardEntity.getId());
            return;
        }
        if (existingAssignee != null
                && existingAssignee.getId().getUserId().equals(card.getAssigneeId())) {
            existingAssignee.setAssignedByUserId(card.getAssigneeAssignedByUserId());
            existingAssignee.setAssignedAt(OffsetDateTime.now());
            existingAssignee.setMembershipVersion(card.getAssigneeMembershipVersion());
            return;
        }

        taskAssigneeJpaRepository.deleteByIdTaskId(cardEntity.getId());
        taskAssigneeJpaRepository.save(new TaskAssigneeEntity(
                cardEntity,
                card.getAssigneeId(),
                card.getAssigneeAssignedByUserId(),
                OffsetDateTime.now(),
                card.getAssigneeMembershipVersion()));
    }

    private void validateAssignment(CardAggregate card) {
        if (card.getAssigneeId() == null) {
            return;
        }
        if (card.getAssigneeMembershipVersion() == null || card.getAssigneeAssignedByUserId() == null) {
            throw new IllegalArgumentException("Assignee membership version and actor are required");
        }
        membershipAssignmentLock.lock(card.getProjectId(), card.getAssigneeId());
        MembershipAssignmentFenceId fenceId = new MembershipAssignmentFenceId(
                card.getProjectId(), card.getAssigneeId(), card.getAssigneeMembershipVersion());
        if (fenceRepository.existsById(fenceId)) {
            throw new IllegalStateException("ASSIGNEE_MEMBERSHIP_REVOKED");
        }
    }

    // Parse status string, lạ thì về TODO.
    private Status parseStatus(String raw) {
        if (raw == null) {
            return Status.TODO;
        }
        try {
            return Status.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return Status.TODO;
        }
    }
}
