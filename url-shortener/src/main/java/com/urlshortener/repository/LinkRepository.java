package com.urlshortener.repository;

import com.urlshortener.domain.Link;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LinkRepository extends JpaRepository<Link, Long> {

	Optional<Link> findByShortCode(String shortCode);

	boolean existsByShortCode(String shortCode);

	/**
	 * Bulk update executes as a single UPDATE statement, bypassing the persistence context
	 * entirely (no entity loading, no dirty checking). {@code clearAutomatically} evicts any
	 * already-loaded {@link Link} instances from the current session afterward so a subsequent
	 * read within the same transaction can't return stale, pre-update field values.
	 */
	@Modifying(clearAutomatically = true)
	@Query("update Link l set l.status = com.urlshortener.domain.LinkStatus.EXPIRED "
			+ "where l.status = com.urlshortener.domain.LinkStatus.ACTIVE and l.expiresAt <= :now")
	int markExpiredLinks(@Param("now") Instant now);

	/**
	 * Atomic counter increment (no entity load, no optimistic-lock check) so concurrent
	 * redirects of the same hot link never collide or contend on {@code version}.
	 */
	@Modifying
	@Query("update Link l set l.clickCount = l.clickCount + 1, l.updatedAt = :now where l.id = :id")
	int incrementClickCount(@Param("id") Long id, @Param("now") Instant now);

	List<Link> findByStatusAndExpiresAtLessThanEqual(com.urlshortener.domain.LinkStatus status, Instant now);
}
