SELECT a.id, a.name,
              COALESCE(AVG(TRUNCATE(TIMESTAMPDIFF(MICROSECOND,COALESCE(pr.created_at, wo.created_at),wo.completed_on) / 86400000000.0,0)), 0) AS avg_duration
            FROM work_order wo
            JOIN asset a ON wo.asset_id = a.id
            LEFT JOIN request pr ON wo.parent_request_id = pr.id
            WHERE wo.company_id = :companyId
              AND wo.created_at BETWEEN :start AND :end
              AND wo.status = 3
              AND wo.completed_on IS NOT NULL
            GROUP BY a.id, a.name
            ORDER BY avg_duration DESC
            LIMIT :limit
