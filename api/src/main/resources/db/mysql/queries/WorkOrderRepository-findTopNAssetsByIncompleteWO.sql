SELECT a.id, a.name, COUNT(wo.id) AS cnt,
                   COALESCE(AVG((TIMESTAMPDIFF(MICROSECOND,wo.created_at,CURRENT_TIMESTAMP) / 1000000.0) / 86400), 0) AS avg_age
            FROM work_order wo
            INNER JOIN asset a ON wo.asset_id = a.id
            WHERE wo.company_id = :companyId
              AND wo.created_at BETWEEN :start AND :end
              AND wo.status != 3
            GROUP BY a.id, a.name
            ORDER BY cnt DESC
            LIMIT :limit
