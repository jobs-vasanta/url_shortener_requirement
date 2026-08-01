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

	@Modifying
	@Query("update Link l set l.status = com.urlshortener.domain.LinkStatus.EXPIRED "
			+ "where l.status = com.urlshortener.domain.LinkStatus.ACTIVE and l.expiresAt <= :now")
	int markExpiredLinks(@Param("now") Instant now);

	List<Link> findByStatusAndExpiresAtLessThanEqual(com.urlshortener.domain.LinkStatus status, Instant now);
}
