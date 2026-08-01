package com.urlshortener.repository;

import com.urlshortener.domain.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

	/** Retained for periodic reconciliation against {@code Link.clickCount}; not used on the hot read path. */
	long countByLinkId(Long linkId);

	/**
	 * Combines min/max into a single query (one round trip instead of two) since both
	 * values are always needed together by {@code AnalyticsService.getAnalytics}.
	 */
	@Query("select new com.urlshortener.repository.ClickAccessWindow(min(c.occurredAt), max(c.occurredAt)) "
			+ "from ClickEvent c where c.linkId = :linkId")
	ClickAccessWindow findAccessWindow(@Param("linkId") Long linkId);
}
