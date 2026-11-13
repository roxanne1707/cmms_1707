package com.grash.controller;

import com.grash.dto.PartQuantityPatchDTO;
import com.grash.exception.CustomException;
import com.grash.model.User;
import com.grash.security.FreshMaintenanceActor;
import com.grash.service.InventoryCommandService;
import com.grash.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Controller dispatch regression, independent of the separately tested Spring Security filter chain. */
@ExtendWith(MockitoExtension.class)
class InventoryFreshActorControllerTest {
    @Mock UserService userService;
    @Mock FreshMaintenanceActor freshMaintenanceActor;
    @Mock InventoryCommandService inventoryCommandService;
    @Mock HttpServletRequest request;
    @InjectMocks PartQuantityController quantities;
    @InjectMocks PurchaseOrderController purchases;

    @Test void staleCachedPrincipalCannotMutateInventory() {
        User stale = new User();
        when(userService.whoami(request)).thenReturn(stale);
        when(freshMaintenanceActor.load(stale)).thenThrow(new CustomException("Access denied", HttpStatus.FORBIDDEN));
        assertThrows(CustomException.class, () -> quantities.patch(new PartQuantityPatchDTO(), 1L, request));
        assertThrows(CustomException.class, () -> quantities.patchWorkOrder(List.of(1L), 10L, request));
        assertThrows(CustomException.class, () -> purchases.respond(true, 20L, request));
        verifyNoInteractions(inventoryCommandService);
    }

    @Test void approvedFreshPrincipalAndStableKeyReachDomainService() {
        User stale = new User(), fresh = new User();
        when(userService.whoami(request)).thenReturn(stale);
        when(freshMaintenanceActor.load(stale)).thenReturn(fresh);
        when(request.getHeader("Idempotency-Key")).thenReturn("repair-command-1");
        PartQuantityPatchDTO body = new PartQuantityPatchDTO(); body.setQuantity(2);
        quantities.patch(body, 1L, request);
        verify(inventoryCommandService).setQuantity(1L, 2, fresh, "repair-command-1");
    }
}
