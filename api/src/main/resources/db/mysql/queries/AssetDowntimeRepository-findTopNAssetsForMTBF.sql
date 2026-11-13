SELECT a.id, a.name, COUNT(ad.id) AS cnt
            FROM asset a
            LEFT JOIN asset_downtime ad ON ad.asset_id = a.id AND ad.starts_on BETWEEN :start AND :end
            WHERE a.company_id = :companyId AND a.created_at < :end
            GROUP BY a.id, a.name
            HAVING COUNT(ad.id) >= 2
            ORDER BY cnt DESC
            LIMIT :limit
