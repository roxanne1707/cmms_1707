package com.grash.service.query;

import com.grash.dto.query.*;
import com.grash.exception.CustomException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.CacheMode;
import org.hibernate.Session;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** JPA native DTO projection is the baseline; no entity collections or N+1 reads. */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "enhancement.query.enabled", havingValue = "true")
public class AssetWorkOrderOverviewRepository {
    private final EntityManagerFactory entityManagerFactory;
    private final JdbcTemplate jdbc;
    public static final String SQL = """
        SELECT count(*),
          count(*) FILTER (WHERE status=3),
          avg(EXTRACT(EPOCH FROM (completed_on-created_at)))
            FILTER (WHERE status=3 AND completed_on IS NOT NULL AND completed_on>=created_at),
          count(*) FILTER (WHERE status=3 AND completed_on IS NOT NULL AND completed_on>=created_at),
          count(*) FILTER (WHERE status=3 AND (completed_on IS NULL OR completed_on<created_at))
        FROM work_order
        WHERE company_id=:company AND asset_id=:asset
          AND created_at>=CAST(:start AS timestamp) AND created_at<CAST(:end AS timestamp)
          AND (:archived=true OR archived=false)
          AND (:demo=true OR COALESCE(is_demo,false)=false)
        """;

    /** Must use the primary PG database; neither an asynchronous replica nor Redis is authoritative. */
    public long epoch(long companyId, long assetId) {
        var versions = jdbc.queryForList("""
                SELECT e.epoch FROM asset_summary_epoch e
                JOIN asset a ON a.id=e.asset_id AND a.company_id=e.company_id
                WHERE e.company_id=? AND e.asset_id=?
                """, Long.class, companyId, assetId);
        if (versions.isEmpty()) throw new CustomException("Asset not found", HttpStatus.NOT_FOUND);
        return versions.get(0);
    }

    public AssetWorkOrderOverview aggregate(AuthorizedQueryScope scope, long assetId, long epoch,
                                            OverviewQuery query) {
        Instant generatedAt = Instant.now();
        try (EntityManager em = entityManagerFactory.createEntityManager()) {
            em.unwrap(Session.class).setCacheMode(CacheMode.IGNORE);
            Object[] row = (Object[]) em.createNativeQuery(SQL)
                    .setParameter("company", scope.companyId()).setParameter("asset", assetId)
                    .setParameter("start", query.getStartUtc()).setParameter("end", query.getEndUtc())
                    .setParameter("archived", query.isIncludeArchived()).setParameter("demo", query.isIncludeDemo())
                    .setHint("jakarta.persistence.query.timeout", 3000).getSingleResult();
            long total = ((Number) row[0]).longValue();
            long complete = ((Number) row[1]).longValue();
            BigDecimal rate = total == 0 ? null : BigDecimal.valueOf(complete)
                    .divide(BigDecimal.valueOf(total), 8, RoundingMode.HALF_UP);
            return new AssetWorkOrderOverview(AssetWorkOrderOverview.METRIC_VERSION, scope.companyId(),
                    assetId, epoch, query.getStart(), query.getEnd(), query.isIncludeArchived(),
                    query.isIncludeDemo(), generatedAt, total, complete, rate,
                    row[2] == null ? null : new BigDecimal(row[2].toString()),
                    ((Number) row[3]).longValue(), ((Number) row[4]).longValue());
        }
    }
}
