package com.grash.service.query;

import com.grash.dto.query.*;
import com.grash.readmodel.typehandler.PriorityCodeTypeHandler;
import com.grash.readmodel.typehandler.StatusCodeTypeHandler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Uncached JPA projection baseline for correctness/performance comparisons with the MP ledger. */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name="enhancement.query.enabled",havingValue="true")
public class MaintenanceLedgerJpaRepository {
    private final EntityManagerFactory entityManagerFactory;
    public LedgerPage search(AuthorizedQueryScope scope,LedgerQuery q) {
        q.normalizeAndValidate();
        String where=" WHERE w.company_id=:company AND w.created_at>=CAST(:start AS timestamp)"
                +" AND w.created_at<CAST(:end AS timestamp)";
        if(!q.isIncludeArchived()) where+=" AND w.archived=false";
        if(!q.isIncludeDemo()) where+=" AND COALESCE(w.is_demo,false)=false";
        if(q.getAssetId()!=null) where+=" AND w.asset_id=:asset";
        if(q.getStatus()!=null) where+=" AND w.status=:status";
        if(q.getPriority()!=null) where+=" AND w.priority=:priority";
        if(q.getMeterTriggered()!=null) where+=" AND "+(q.getMeterTriggered()?"EXISTS":"NOT EXISTS")
                +"(SELECT 1 FROM meter_trigger_execution me WHERE me.work_order_id=w.id AND me.company_id=w.company_id)";
        String columns="""
            SELECT w.id,w.custom_id,w.title,a.id,a.name,w.status,w.priority,
                   w.created_at,w.completed_on,w.archived,COALESCE(w.is_demo,false),
                   CASE WHEN w.status=3 AND w.completed_on IS NOT NULL AND w.completed_on>=w.created_at
                   THEN EXTRACT(EPOCH FROM(w.completed_on-w.created_at)) END,
                   EXISTS(SELECT 1 FROM meter_trigger_execution me WHERE me.work_order_id=w.id AND me.company_id=w.company_id)
            FROM work_order w LEFT JOIN asset a ON a.id=w.asset_id AND a.company_id=w.company_id
            """;
        String order=q.getSort()==LedgerQuery.Sort.CREATED_ASC?" ASC":" DESC";
        try(EntityManager em=entityManagerFactory.createEntityManager()){
            em.getTransaction().begin();
            try {
                em.createNativeQuery("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY").executeUpdate();
                long total=((Number) bind(em.createNativeQuery("SELECT count(*) FROM work_order w"+where),scope,q)
                        .getSingleResult()).longValue();
                List<?> raw=bind(em.createNativeQuery(columns+where+" ORDER BY w.created_at"+order+",w.id"+order),scope,q)
                        .setFirstResult(q.getPage()*q.getSize()).setMaxResults(q.getSize()).getResultList();
                List<MaintenanceLedgerRow> rows=raw.stream().map(value->map((Object[]) value)).toList();
                em.getTransaction().commit();
                return new LedgerPage(rows,total,q.getPage(),q.getSize());
            }catch(RuntimeException e){if(em.getTransaction().isActive())em.getTransaction().rollback();throw e;}
        }
    }
    private Query bind(Query sql,AuthorizedQueryScope scope,LedgerQuery q){
        sql.setParameter("company",scope.companyId()).setParameter("start",q.getStartUtc())
                .setParameter("end",q.getEndUtc()).setHint("jakarta.persistence.query.timeout",3000);
        if(q.getAssetId()!=null)sql.setParameter("asset",q.getAssetId());
        if(q.getStatus()!=null)sql.setParameter("status",q.getStatusCode());
        if(q.getPriority()!=null)sql.setParameter("priority",q.getPriorityCode());
        return sql;
    }
    private static MaintenanceLedgerRow map(Object[] v){
        try {
            MaintenanceLedgerRow row=new MaintenanceLedgerRow();
            row.setId(((Number)v[0]).longValue());row.setCustomId((String)v[1]);row.setTitle((String)v[2]);
            row.setAssetId(v[3]==null?null:((Number)v[3]).longValue());row.setAssetName((String)v[4]);
            row.setStatus(v[5]==null?null:StatusCodeTypeHandler.decode(((Number)v[5]).intValue()));
            row.setPriority(v[6]==null?null:PriorityCodeTypeHandler.decode(((Number)v[6]).intValue()));
            row.setCreatedAt(instant(v[7]));row.setCompletedOn(instant(v[8]));
            row.setArchived(Boolean.TRUE.equals(v[9]));row.setDemo(Boolean.TRUE.equals(v[10]));
            row.setCycleSeconds(v[11]==null?null:new BigDecimal(v[11].toString()));
            row.setMeterTriggered(Boolean.TRUE.equals(v[12]));
            return row;
        }catch(SQLException e){throw new IllegalStateException("Invalid persisted ledger enum",e);}
    }
    private static Instant instant(Object value){
        if(value==null)return null;
        if(value instanceof Timestamp timestamp)return timestamp.toLocalDateTime().toInstant(ZoneOffset.UTC);
        if(value instanceof LocalDateTime time)return time.toInstant(ZoneOffset.UTC);
        if(value instanceof Instant instant)return instant;
        throw new IllegalStateException("Unexpected timestamp projection type: "+value.getClass());
    }
}
