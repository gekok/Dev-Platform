package dev.platform.board.infrastructure.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import dev.platform.board.infrastructure.persistence.entity.BoardEntity;

// Truy vấn bảng boards.
public interface BoardJpaRepository extends JpaRepository<BoardEntity, Long> {

    @Query("select b, m.id.userId from BoardEntity b join ProjectMembershipEntity m on m.id.projectId = b.projectId where b.id = :id and m.role = 'OWNER' and m.status = 'ACTIVE'")
    Optional<Object[]> findWithOwnerById(@Param("id") Long id);

    @Query("select b, m.id.userId from BoardEntity b join ProjectMembershipEntity m on m.id.projectId = b.projectId where m.id.userId = :ownerId and m.role = 'OWNER' and m.status = 'ACTIVE'")
    List<Object[]> findWithOwnerByOwnerId(@Param("ownerId") UUID ownerId);
}
