package com.grash.readmodel.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.grash.dto.query.LedgerQuery;
import com.grash.dto.query.MaintenanceLedgerRow;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/** Deliberately no BaseMapper: this read model exposes no generated write operations. */
public interface MaintenanceLedgerMapper {
    List<MaintenanceLedgerRow> selectPage(IPage<?> page,@Param("company") long companyId,@Param("q") LedgerQuery query);
    long count(@Param("company") long companyId,@Param("q") LedgerQuery query);
}
