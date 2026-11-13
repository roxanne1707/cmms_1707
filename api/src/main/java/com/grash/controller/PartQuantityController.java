package com.grash.controller;

import com.grash.dto.PartQuantityCompletePatchDTO;
import com.grash.dto.PartQuantityPatchDTO;
import com.grash.dto.PartQuantityShowDTO;
import com.grash.dto.SuccessResponse;
import com.grash.exception.CustomException;
import com.grash.mapper.PartQuantityMapper;
import com.grash.model.*;
import com.grash.model.enums.PermissionEntity;
import com.grash.service.*;
import com.grash.utils.Helper;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/part-quantities")
@Tag(name = "Part Quantities", description = "Operations on part quantities")
@RequiredArgsConstructor
public class PartQuantityController {

    private final PartQuantityService partQuantityService;
    private final InventoryCommandService inventoryCommandService;
    private final PartQuantityMapper partQuantityMapper;
    private final UserService userService;
    private final com.grash.security.FreshMaintenanceActor freshMaintenanceActor;
    private final WorkOrderService workOrderService;
    private final PartService partService;
    private final PurchaseOrderService purchaseOrderService;

    @GetMapping("/work-order/{id}")
    @PreAuthorize("permitAll()")
    public Collection<PartQuantityShowDTO> getByWorkOrder(HttpServletRequest req,
                                                          @PathVariable("id") Long id) {
        User user = userService.whoami(req);
        Optional<WorkOrder> optionalWorkOrder = workOrderService.findById(id);
        if (optionalWorkOrder.isPresent()) {
            if (!optionalWorkOrder.get().canBeViewedBy(user))
                throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
            return partQuantityService.findByWorkOrder(id).stream().map(partQuantityMapper::toShowDto).collect(Collectors.toList());
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    @GetMapping("/purchase-order/{id}")
    @PreAuthorize("permitAll()")
    public Collection<PartQuantityShowDTO> getByPurchaseOrder(HttpServletRequest req, @PathVariable(
            "id") Long id) {
        User user = userService.whoami(req);
        if (!user.getRole().getViewPermissions().contains(PermissionEntity.PURCHASE_ORDERS)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        Optional<PurchaseOrder> optionalPurchaseOrder = purchaseOrderService.findById(id);
        if (optionalPurchaseOrder.isPresent()) {
            if (!optionalPurchaseOrder.get().canBeViewedBy(user))
                throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
            return partQuantityService.findByPurchaseOrder(id).stream().map(partQuantityMapper::toShowDto).collect(Collectors.toList());
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    @PatchMapping("/work-order/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Collection<PartQuantityShowDTO> patchWorkOrder(@Parameter(description = "List of part IDs to associate " +
                                                                  "with work order") @Valid @RequestBody List<Long> parts,
                                                          @PathVariable("id") Long id,
                                                          HttpServletRequest req) {
        return inventoryCommandService.replaceWorkOrder(id, parts, freshMaintenanceActor.load(userService.whoami(req)), req.getHeader("Idempotency-Key"));
    }

    @PatchMapping("/purchase-order/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Collection<PartQuantityShowDTO> patchPurchaseOrder(@Parameter(description = "List of part quantities to " +
                                                                      "update for purchase order") @Valid @RequestBody List<PartQuantityCompletePatchDTO> partQuantitiesReq, @PathVariable("id") Long id,
                                                              HttpServletRequest req) {
        return inventoryCommandService.replacePurchaseOrder(id, partQuantitiesReq, freshMaintenanceActor.load(userService.whoami(req)), req.getHeader("Idempotency-Key"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("permitAll()")
    public PartQuantityShowDTO getById(@PathVariable("id") Long id, HttpServletRequest req) {
        User user = userService.whoami(req);
        Optional<PartQuantity> optionalPartQuantity = partQuantityService.findById(id);
        if (optionalPartQuantity.isPresent()) {
            PartQuantity savedPartQuantity = optionalPartQuantity.get();
            if (savedPartQuantity.getPurchaseOrder() != null && !savedPartQuantity.getPurchaseOrder().canBeViewedBy(user)) {
                throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
            }
            if (savedPartQuantity.getWorkOrder() != null && !savedPartQuantity.getWorkOrder().canBeViewedBy(user)) {
                throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
            }
            return partQuantityMapper.toShowDto(savedPartQuantity);
        } else throw new CustomException("Not found", HttpStatus.NOT_FOUND);
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    PartQuantityShowDTO create(@Parameter(description = "Part quantity to create") @Valid @RequestBody PartQuantity partQuantityReq,
                               HttpServletRequest req) {
        return inventoryCommandService.create(partQuantityReq, freshMaintenanceActor.load(userService.whoami(req)), req.getHeader("Idempotency-Key"));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public PartQuantityShowDTO patch(@Parameter(description = "Part quantity fields to update") @Valid @RequestBody PartQuantityPatchDTO partQuantity,
                                     @PathVariable("id") Long id,
                                     HttpServletRequest req) {
        return inventoryCommandService.setQuantity(id, partQuantity.getQuantity(), freshMaintenanceActor.load(userService.whoami(req)), req.getHeader("Idempotency-Key"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable("id") Long id, HttpServletRequest req) {
        inventoryCommandService.delete(id, freshMaintenanceActor.load(userService.whoami(req)), req.getHeader("Idempotency-Key"));
        return new ResponseEntity<>(new SuccessResponse(true, "Deleted successfully"), HttpStatus.OK);
    }

}


