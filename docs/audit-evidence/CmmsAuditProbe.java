import com.grash.model.*;
import com.grash.model.enums.*;
import com.grash.service.*;
import com.grash.repository.*;
import com.grash.mapper.*;
import com.grash.dto.*;
import com.grash.dto.workOrder.WorkOrderPostDTO;
import com.grash.controller.PartQuantityController;
import com.grash.exception.CustomException;
import com.grash.utils.CacheKeyUtils;
import org.springframework.context.MessageSource;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class CmmsAuditProbe {
 public static void main(String[] args) throws Exception {
  long base=1200000L*1000;
  String a=CacheKeyUtils.dateRangeKey(7L,new Date(base+1000),new Date(base+2400000+5000));
  String b=CacheKeyUtils.dateRangeKey(7L,new Date(base+1199000),new Date(base+3599999));
  if(!a.equals(b))throw new AssertionError();
  System.out.println("CONFIRMED key collision: two distinct exact ranges share key "+a);
  WorkOrder w=new WorkOrder();w.setCreatedAt(new Date(base));w.setCompletedOn(new Date(base+20*3600000L));
  long age=WorkOrder.getAverageAge(List.of(w));if(age!=0)throw new AssertionError();
  System.out.println("CONFIRMED 20-hour cycle average returned "+age+" whole days");

  Map<Class<?>,Object> deps=new HashMap<>();
  Constructor<?> ctor=ReadingService.class.getConstructors()[0];
  Object[] values=Arrays.stream(ctor.getParameterTypes()).map(t->deps.computeIfAbsent(t,CmmsAuditProbe::mockType)).toArray();
  ReadingService service=(ReadingService)ctor.newInstance(values);
  WorkOrderMeterTriggerService triggerService=(WorkOrderMeterTriggerService)deps.get(WorkOrderMeterTriggerService.class);
  WorkOrderService workOrders=(WorkOrderService)deps.get(WorkOrderService.class);
  Meter meter=new Meter();meter.setId(1L);meter.setName("local mock");meter.setUnit("bar");
  WorkOrderMeterTrigger trigger=new WorkOrderMeterTrigger();trigger.setId(1L);trigger.setName("threshold");trigger.setValue(10);trigger.setTriggerCondition(WorkOrderMeterTriggerCondition.MORE_THAN);trigger.setRecurrent(false);trigger.setWaitBefore(999);
  User user=new User();user.setLanguage(Language.EN);Company company=new Company();company.setId(1L);user.setCompany(company);
  when(triggerService.findByMeter(1L)).thenReturn(List.of(trigger));
  WorkOrderPostDTO template=new WorkOrderPostDTO();when(workOrders.getWorkOrderFromWorkOrderBase(trigger)).thenReturn(template);
  WorkOrder created=new WorkOrder();created.setId(99L);when(workOrders.create(template,company)).thenReturn(created);
  Method process=ReadingService.class.getDeclaredMethod("processMeterTriggers",Meter.class,double.class,User.class);process.setAccessible(true);
  process.invoke(service,meter,15d,user);process.invoke(service,meter,16d,user);
  verify(workOrders,times(2)).create(template,company);
  System.out.println("CONFIRMED trigger core creates twice for 15 then 16 with recurrent=false and waitBefore=999 (mocked repositories, no DB/concurrency claim)");

  PartQuantityService quantities=mock(PartQuantityService.class);PartService parts=mock(PartService.class);UserService users=mock(UserService.class);
  PartQuantityController controller=new PartQuantityController(quantities,mock(PartQuantityMapper.class),users,mock(WorkOrderService.class),parts,mock(PurchaseOrderService.class));
  HttpServletRequest req=mock(HttpServletRequest.class);when(users.whoami(req)).thenReturn(user);
  WorkOrder forbidden=mock(WorkOrder.class);when(forbidden.canBeEditedBy(user)).thenReturn(false);
  Part part=new Part();part.setId(12L);part.setUnit("piece");PartQuantity pq=new PartQuantity(part,forbidden,null,1);pq.setId(5L);
  when(quantities.findById(5L)).thenReturn(Optional.of(pq));PartQuantityPatchDTO patch=new PartQuantityPatchDTO();patch.setQuantity(2);
  try {controller.patch(patch,5L,req);throw new AssertionError("Should reject");}catch(CustomException expected){}
  verify(parts).consumePart(eq(12L),eq(1d),eq(forbidden),any(),eq(false));verify(quantities,never()).update(any(),any());
  System.out.println("CONFIRMED stock consume call precedes permission rejection (mocked service; persisted side effect still needs isolated DB test)");
 }
 static Object mockType(Class<?> type){return mock(type);}
}
