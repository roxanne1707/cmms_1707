package com.grash.service;
import com.grash.dto.*;
import com.grash.exception.CustomException;
import com.grash.mapper.*;
import com.grash.model.*;
import com.grash.repository.PartQuantityRepository;
import jakarta.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@ExtendWith(MockitoExtension.class)
class InventoryCommandServiceTest {
    @Mock EntityManager em; @Mock PartQuantityRepository quantities; @Mock PartQuantityMapper mapper;
    @Mock PartService parts; @Mock DomainCommandStore commands; @Mock PurchaseOrderMapper purchaseOrderMapper;
    @InjectMocks InventoryCommandService service;
    User user; Company company; WorkOrder order;
    @BeforeEach void setup() {
        company=new Company(); company.setId(1L); user=new User();user.setId(2L);user.setCompany(company);
        order=mock(WorkOrder.class);
    }
    @Test void forbiddenEditDoesNotTouchStockDetailsAuditOrCommandResult() {
        when(commands.begin(anyString(),any(),eq(user),anyString())).thenReturn(Optional.empty());
        when(em.find(WorkOrder.class,9L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(order);
        when(order.getCompany()).thenReturn(company); when(order.canBeEditedBy(user)).thenReturn(false);
        assertThrows(CustomException.class,()->service.replaceWorkOrder(9L,List.of(3L),user,"key"));
        verifyNoInteractions(parts,quantities,mapper); verify(commands,never()).complete(anyString(),any(),any(),anyString(),anyString(),anyLong(),any());
    }
    @Test void changingQuantityToSameValueDoesNotConsumeOrEraseAudit() {
        Part part=new Part();part.setId(3L);part.setCompany(company);
        PartQuantity q=new PartQuantity(part,order,null,2);q.setId(5L);
        when(commands.begin(anyString(),any(),eq(user),anyString())).thenReturn(Optional.empty());
        when(quantities.findById(5L)).thenReturn(Optional.of(q)); when(order.getId()).thenReturn(9L);
        when(em.find(WorkOrder.class,9L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(order);
        when(order.getCompany()).thenReturn(company);when(order.canBeEditedBy(user)).thenReturn(true);
        Part visible=spy(part); q.setPart(visible);doReturn(true).when(visible).canBeViewedBy(user);
        when(em.find(Part.class,3L,LockModeType.PESSIMISTIC_WRITE)).thenReturn(visible);
        when(mapper.toShowDto(q)).thenReturn(new PartQuantityShowDTO());
        service.setQuantity(5L,2,user,null);
        verifyNoInteractions(parts); assertEquals(2,q.getQuantity());
    }
    @Test void finiteWholeNonNegativeQuantityIsRequiredForUnitlessPart() {
        Part part=new Part();
        for(double value: new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,0.25})
            assertThrows(CustomException.class,()->InventoryCommandService.validateQuantity(value,part));
        assertDoesNotThrow(()->InventoryCommandService.validateQuantity(0,part));
        assertDoesNotThrow(()->InventoryCommandService.validateQuantity(2,part));
    }
}
