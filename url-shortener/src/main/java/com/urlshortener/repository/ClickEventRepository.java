package com.urlshortener.repository;

import com.urlshortener.domain.ClickEvent;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

	long countByLinkId(Long linkId);

	@Query("select min(c.occurredAt) from ClickEvent c where c.linkId = :linkId")
	Instant findFirstAccessedAt(@Param("linkId") Long linkId);

	@Query("select max(c.occurredAt) from ClickEvent c where c.linkId = :linkId")
	Instant findLastAccessedAt(@Param("linkId") Long linkId);
}
