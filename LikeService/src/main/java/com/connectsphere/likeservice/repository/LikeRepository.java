package com.connectsphere.likeservice.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.connectsphere.likeservice.entity.Like;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {
	Optional<Like> findByUserIdAndTargetIdAndTargetType(int userId, int targetId, String targetType);

	boolean existsByUserIdAndTargetIdAndTargetType(int userId, int targetId, String targetType);

	List<Like> findByTargetIdAndTargetType(int targetId, String targetType);

	long countByTargetIdAndTargetType(int targetId, String targetType);
	
	long countByTargetIdAndTargetTypeAndReactionType(int targetId, String targetType, String reactionType);

	@Query("Select l.reactionType, Count(1) FROM Like l WHERE l.targetId = :targetId AND l.targetType = :targetType GROUP BY l.reactionType ORDER BY COUNT(1) DESC")
	List<Object[]> findReactionSummaryRaw(@Param("targetId") int targetId, @Param("targetType") String targetType);

	@Query("SELECT COUNT(l) FROM Like l WHERE l.targetId = :targetId AND l.targetType = :targetType")
	long countTotalByTargetIdAndTargetType(@Param("targetId") int targetId, @Param("targetType") String targetType);

	List<Like> findByUserId(int userId);

	List<Like> findByUserIdAndTargetType(int userId, String targetType);

	void deleteByUserIdAndTargetIdAndTargetType(int userId, int targetId, String targetType);
}
