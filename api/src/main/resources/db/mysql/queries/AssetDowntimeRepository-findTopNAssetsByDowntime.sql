SELECT a.id, a.name,
              COUNT(ad.id) AS cnt,
              COALESCE(SUM(GREATEST(0, (TIMESTAMPDIFF(MICROSECOND,GREATEST(ad.starts_on, :start),LEAST(TIMESTAMPADD(MICROSECOND,ad.duration*1000000,ad.starts_on), :end)) / 1000000.0))), 0) AS total_duration,
              (TIMESTAMPDIFF(MICROSECOND,GREATEST(COALESCE(a.in_service_date, a.created_at), :start),:end) / 1000000.0) AS living_time
            FROM asset a
            LEFT JOIN asset_downtime ad ON ad.asset_id = a.id
              AND ad.starts_on <= :end
              AND (TIMESTAMPADD(MICROSECOND,ad.duration*1000000,ad.starts_on)) >= :start
            WHERE a.company_id = :companyId
              AND a.created_at < :end
            GROUP BY a.id, a.name, a.in_service_date, a.created_at
            HAVING COUNT(ad.id) > 0
            ORDER BY cnt DESC
            LIMIT :limit
