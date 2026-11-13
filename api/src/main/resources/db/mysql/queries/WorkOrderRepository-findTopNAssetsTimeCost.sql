WITH wo_agg AS (
              SELECT
                wo.asset_id,
                COALESCE(labor_stats.total_labor_time, 0) AS labor_time,
                COALESCE(labor_stats.total_labor_cost, 0) AS labor_cost,
                COALESCE(part_stats.total_part_cost, 0) AS part_cost,
                COALESCE(add_stats.total_additional_cost, 0) AS additional_cost
              FROM work_order wo
              LEFT JOIN (
                SELECT l.work_order_id,
                       SUM(l.duration) AS total_labor_time,
                       SUM(l.hourly_rate * l.duration / 3600) AS total_labor_cost
                FROM labor l
                GROUP BY l.work_order_id
              ) labor_stats ON labor_stats.work_order_id = wo.id
              LEFT JOIN (
                SELECT pq.work_order_id, SUM(p.cost * pq.quantity) AS total_part_cost
                FROM part_quantity pq
                JOIN part p ON pq.part_id = p.id
                GROUP BY pq.work_order_id
              ) part_stats ON part_stats.work_order_id = wo.id
              LEFT JOIN (
                SELECT ac.work_order_id, SUM(ac.cost) AS total_additional_cost
                FROM additional_cost ac
                GROUP BY ac.work_order_id
              ) add_stats ON add_stats.work_order_id = wo.id
              WHERE wo.company_id = :companyId
                AND wo.created_at BETWEEN :start AND :end
                AND wo.status = 3
            )
            SELECT
              a.id,
              a.name,
              SUM(wo_agg.labor_time) AS total_time,
              SUM(wo_agg.labor_cost) AS total_labor_cost,
              SUM(wo_agg.part_cost) AS total_part_cost,
              SUM(wo_agg.additional_cost) AS total_additional_cost
            FROM wo_agg
            JOIN asset a ON wo_agg.asset_id = a.id
            GROUP BY a.id, a.name
            ORDER BY  SUM(wo_agg.labor_time)
                   + SUM(wo_agg.labor_cost)
                   + SUM(wo_agg.part_cost)
                   + SUM(wo_agg.additional_cost) DESC
            LIMIT :limit
