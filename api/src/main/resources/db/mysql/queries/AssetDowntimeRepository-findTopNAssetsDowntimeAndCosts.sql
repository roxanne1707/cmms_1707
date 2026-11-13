WITH wo_costs AS (
              SELECT wo_data.asset_id,
                COALESCE(SUM(wo_data.labor_cost), 0) AS labor_cost,
                COALESCE(SUM(wo_data.part_cost), 0) AS part_cost,
                COALESCE(SUM(wo_data.additional_cost), 0) AS additional_cost
              FROM (
                SELECT wo.asset_id,
                  COALESCE(labor_stats.total_labor_cost, 0) AS labor_cost,
                  COALESCE(part_stats.total_part_cost, 0) AS part_cost,
                  COALESCE(add_stats.total_additional_cost, 0) AS additional_cost
                FROM work_order wo
                LEFT JOIN (
                  SELECT l.work_order_id, SUM(l.hourly_rate * l.duration / 3600) AS total_labor_cost
                  FROM labor l GROUP BY l.work_order_id
                ) labor_stats ON labor_stats.work_order_id = wo.id
                LEFT JOIN (
                  SELECT pq.work_order_id, SUM(p.cost * pq.quantity) AS total_part_cost
                  FROM part_quantity pq JOIN part p ON pq.part_id = p.id
                  GROUP BY pq.work_order_id
                ) part_stats ON part_stats.work_order_id = wo.id
                LEFT JOIN (
                  SELECT ac.work_order_id, SUM(ac.cost) AS total_additional_cost
                  FROM additional_cost ac GROUP BY ac.work_order_id
                ) add_stats ON add_stats.work_order_id = wo.id
                WHERE wo.company_id = :companyId
                  AND wo.created_at BETWEEN :start AND :end
                  AND wo.status = 3
              ) wo_data
              GROUP BY wo_data.asset_id
            ),
            downtime_agg AS (
              SELECT ad.asset_id,
                COALESCE(SUM(GREATEST(0, (TIMESTAMPDIFF(MICROSECOND,GREATEST(ad.starts_on, :start),LEAST(TIMESTAMPADD(MICROSECOND,ad.duration*1000000,ad.starts_on), :end)) / 1000000.0))), 0) AS total_duration
              FROM asset_downtime ad
              WHERE ad.starts_on <= :end
                AND (TIMESTAMPADD(MICROSECOND,ad.duration*1000000,ad.starts_on)) >= :start
              GROUP BY ad.asset_id
            )
            SELECT a.id, a.name,
              COALESCE(downtime_agg.total_duration, 0) AS duration,
              COALESCE(wo_costs.labor_cost, 0) AS labor_cost,
              COALESCE(wo_costs.part_cost, 0) AS part_cost,
              COALESCE(wo_costs.additional_cost, 0) AS additional_cost
            FROM asset a
            LEFT JOIN wo_costs ON wo_costs.asset_id = a.id
            LEFT JOIN downtime_agg ON downtime_agg.asset_id = a.id
            WHERE a.company_id = :companyId AND a.created_at < :end
              AND (wo_costs.asset_id IS NOT NULL OR downtime_agg.asset_id IS NOT NULL)
            ORDER BY duration DESC
            LIMIT :limit
