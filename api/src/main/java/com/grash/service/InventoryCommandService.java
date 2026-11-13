package com.grash.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.grash.dto.PartQuantityShowDTO;
import com.grash.dto.PartQuantityCompletePatchDTO;
import com.grash.exception.CustomException;
import com.grash.mapper.PartQuantityMapper;
import com.grash.model.*;
import com.grash.repository.PartQuantityRepository;
import com.grash.utils.Helper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Supplier;

/** All work-order inventory mutations lock the parent, then parts in ascending ID order. */
@Service
@RequiredArgsConstructor
public class InventoryCommandService {
    private final EntityManager em;
    private final PartQuantityRepository quantities;
    private final PartQuantityMapper mapper;
    private final PartService parts;
    private final DomainCommandStore commands;
    private final com.grash.mapper.PurchaseOrderMapper purchaseOrderMapper;
    private static final TypeReference<List<PartQuantityShowDTO>> RESULT = new TypeReference<>() {};
    private record Subject(String type, Long id) {}

    @Transactional
    public List<PartQuantityShowDTO> replaceWorkOrder(Long id, List<Long> ids, User user, String key) {
        if (ids == null || ids.stream().anyMatch(Objects::isNull)) bad("Part IDs are required");
        SortedSet<Long> desired = new TreeSet<>(ids);
        return execute(key, user, "replace-work-order:" + id + ":" + desired, () -> new Subject("WORK_ORDER", id), () -> {
            WorkOrder wo = em.find(WorkOrder.class, id);
            List<PartQuantity> existing = new ArrayList<>(quantities.findByWorkOrder_Id(id));
            // A caller may have loaded these lines before this command obtained the parent lock.
            existing.forEach(em::refresh);
            Map<Long, PartQuantity> byPart = new TreeMap<>();
            for (PartQuantity q : existing) {
                if (byPart.put(q.getPart().getId(), q) != null) conflict("Duplicate legacy part quantities must be reconciled first");
            }
            SortedSet<Long> all = new TreeSet<>(desired);
            all.addAll(byPart.keySet());
            for (Long partId : all) {
                Part p = lockedPart(partId, user);
                PartQuantity q = byPart.get(partId);
                if (desired.contains(partId) && q == null) {
                    adjust(p, wo, 1, user);
                    q = new PartQuantity(p, wo, null, 1);
                    q.setCompany(user.getCompany());
                    quantities.save(q);
                } else if (!desired.contains(partId)) {
                    adjust(p, wo, -q.getQuantity(), user);
                    quantities.delete(q);
                }
            }
            if (wo.getFirstTimeToReact() == null) wo.setFirstTimeToReact(new Date());
            em.flush();
            return quantities.findByWorkOrder_Id(id).stream().map(mapper::toShowDto).toList();
        });
    }

    @Transactional
    public List<PartQuantityShowDTO> replacePurchaseOrder(Long id, List<PartQuantityCompletePatchDTO> input, User user, String key) {
        SortedMap<Long, Double> desired = new TreeMap<>();
        if (input == null || input.stream().anyMatch(Objects::isNull)) bad("Part quantities are required");
        for (PartQuantityCompletePatchDTO item : input) {
            if (item.getPart() == null || item.getPart().getId() == null) bad("Part is required");
            if (desired.put(item.getPart().getId(), item.getQuantity()) != null) bad("Duplicate part");
        }
        return execute(key, user, "replace-purchase-order:" + id + ":" + desired, () -> new Subject("PURCHASE_ORDER", id), () -> {
            PurchaseOrder order = em.find(PurchaseOrder.class, id);
            Map<Long, PartQuantity> existing = new TreeMap<>();
            quantities.findByPurchaseOrder_Id(id).forEach(q -> {
                em.refresh(q);
                if (existing.put(q.getPart().getId(), q) != null) conflict("Duplicate legacy part quantities must be reconciled first");
            });
            for (var entry : desired.entrySet()) {
                Part p = lockedPart(entry.getKey(), user);
                validateQuantity(entry.getValue(), p);
                PartQuantity q = existing.remove(entry.getKey());
                if (q == null) q = new PartQuantity(p, null, order, entry.getValue());
                q.setQuantity(entry.getValue());
                q.setCompany(user.getCompany());
                quantities.save(q);
            }
            existing.values().forEach(quantities::delete);
            em.flush();
            return quantities.findByPurchaseOrder_Id(id).stream().map(mapper::toShowDto).toList();
        });
    }

    @Transactional
    public PartQuantityShowDTO create(PartQuantity request, User user, String key) {
        if (request.getPart() == null || request.getPart().getId() == null || (request.getWorkOrder() == null) == (request.getPurchaseOrder() == null)) bad("Exactly one parent and a part are required");
        Subject subject = request.getWorkOrder() != null ? new Subject("WORK_ORDER", request.getWorkOrder().getId()) : new Subject("PURCHASE_ORDER", request.getPurchaseOrder().getId());
        String payload = "create:" + subject + ":" + request.getPart().getId() + ":" + request.getQuantity();
        return execute(key, user, payload, () -> subject, () -> {
            Part p = lockedPart(request.getPart().getId(), user);
            validateQuantity(request.getQuantity(), p);
            Collection<PartQuantity> current = subject.type().equals("WORK_ORDER") ? quantities.findByWorkOrder_Id(subject.id()) : quantities.findByPurchaseOrder_Id(subject.id());
            if (current.stream().anyMatch(q -> q.getPart().getId().equals(p.getId()))) conflict("Part already belongs to this order; update the existing quantity");
            WorkOrder wo = subject.type().equals("WORK_ORDER") ? em.find(WorkOrder.class, subject.id()) : null;
            PurchaseOrder po = wo == null ? em.find(PurchaseOrder.class, subject.id()) : null;
            if (wo != null) adjust(p, wo, request.getQuantity(), user);
            PartQuantity q = new PartQuantity(p, wo, po, request.getQuantity());
            q.setCompany(user.getCompany());
            quantities.saveAndFlush(q);
            return List.of(mapper.toShowDto(q));
        }).get(0);
    }

    @Transactional
    public PartQuantityShowDTO setQuantity(Long id, double value, User user, String key) {
        return execute(key, user, "set:" + id + ":" + value, () -> subjectFor(id), () -> {
            PartQuantity q = quantity(id);
            // It may already be managed from subjectFor; reload AFTER the parent lock.
            em.refresh(q);
            Part p = lockedPart(q.getPart().getId(), user);
            validateQuantity(value, p);
            if (q.getWorkOrder() != null) adjust(p, q.getWorkOrder(), value - q.getQuantity(), user);
            q.setQuantity(value);
            quantities.saveAndFlush(q);
            return List.of(mapper.toShowDto(q));
        }).get(0);
    }

    @Transactional
    public void delete(Long id, User user, String key) {
        execute(key, user, "delete:" + id, () -> subjectFor(id), () -> {
            PartQuantity q = quantity(id);
            em.refresh(q);
            Part p = lockedPart(q.getPart().getId(), user);
            if (q.getWorkOrder() != null) adjust(p, q.getWorkOrder(), -q.getQuantity(), user);
            quantities.delete(q);
            em.flush();
            return List.of();
        });
    }

    @Transactional
    public void restock(Long id, com.grash.dto.PartRestockDTO request, User user, String key) {
        String description = request.getDescription();
        String descriptionKey = description == null ? "null" : description.length() + ":" + description;
        String payload = "restock:" + id + ":" + request.getQuantity() + ":" + descriptionKey;
        Optional<DomainCommandStore.Stored> previous = commands.begin("RESTOCK", key, user, payload);
        Part part = em.find(Part.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (part == null) missing();
        em.refresh(part, LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(part.getCompany().getId(), user.getCompany().getId()) || !part.canBeEditedBy(user)) forbidden();
        if (previous.isPresent()) return;
        validateQuantity(request.getQuantity(), part);
        if (request.getQuantity() == 0) bad("Restock quantity must be positive");
        parts.restockPart(id, request.getQuantity(), description);
        em.flush();
        commands.complete("RESTOCK", key, user, payload, "PART", id, Boolean.TRUE);
    }

    @Transactional
    public com.grash.dto.PurchaseOrderShowDTO respondToPurchaseOrder(Long id, boolean approved, User user, String key) {
        String payload = "respond:" + id + ":" + approved;
        Optional<DomainCommandStore.Stored> previous = commands.begin("PURCHASE_RESPONSE",key,user,payload);
        authorizeAndLock(new Subject("PURCHASE_ORDER",id),user);
        if (!user.getRole().getEditOtherPermissions().contains(com.grash.model.enums.PermissionEntity.PURCHASE_ORDERS)) forbidden();
        if (previous.isPresent()) return commands.result(previous.get(),new TypeReference<com.grash.dto.PurchaseOrderShowDTO>() {});
        PurchaseOrder order = em.find(PurchaseOrder.class,id);
        if (order.getStatus() == com.grash.model.enums.ApprovalStatus.APPROVED) conflict("Purchase order is already approved");
        List<PartQuantity> lines = new ArrayList<>(quantities.findByPurchaseOrder_Id(id));
        lines.forEach(em::refresh);
        lines.sort(Comparator.comparing(q -> q.getPart().getId()));
        if (approved) for (PartQuantity line : lines) {
            Part part = lockedPart(line.getPart().getId(), user);
            validateQuantity(line.getQuantity(), part);
            if (line.getQuantity() > 0) parts.restockPart(part.getId(),line.getQuantity(),"Purchase order " + id);
        }
        order.setStatus(approved ? com.grash.model.enums.ApprovalStatus.APPROVED : com.grash.model.enums.ApprovalStatus.REJECTED);
        em.flush();
        com.grash.dto.PurchaseOrderShowDTO result = purchaseOrderMapper.toShowDto(order);
        result.setPartQuantities(lines.stream().map(mapper::toShowDto).toList());
        commands.complete("PURCHASE_RESPONSE",key,user,payload,"PURCHASE_ORDER",id,result);
        return result;
    }

    private List<PartQuantityShowDTO> execute(String key, User user, String payload, Supplier<Subject> subject, Supplier<List<PartQuantityShowDTO>> action) {
        Optional<DomainCommandStore.Stored> previous = commands.begin("INVENTORY", key, user, payload);
        Subject target = previous.map(s -> new Subject(s.subjectType(), s.subjectId())).orElseGet(subject);
        authorizeAndLock(target, user); // also recheck current permissions before replaying a saved response
        if (previous.isPresent()) return commands.result(previous.get(), RESULT);
        if (target.type().equals("PURCHASE_ORDER") && em.find(PurchaseOrder.class,target.id()).getStatus() == com.grash.model.enums.ApprovalStatus.APPROVED)
            conflict("Approved purchase order quantities cannot be edited");
        List<PartQuantityShowDTO> result = action.get();
        commands.complete("INVENTORY", key, user, payload, target.type(), target.id(), result);
        return result;
    }
    private void authorizeAndLock(Subject subject, User user) {
        if (subject.id() == null) bad("Parent is required");
        if (subject.type().equals("WORK_ORDER")) {
            WorkOrder wo = em.find(WorkOrder.class, subject.id(), LockModeType.PESSIMISTIC_WRITE);
            if (wo == null) missing();
            em.refresh(wo, LockModeType.PESSIMISTIC_WRITE);
            if (!Objects.equals(wo.getCompany().getId(), user.getCompany().getId()) || !wo.canBeEditedBy(user)) forbidden();
        } else {
            PurchaseOrder po = em.find(PurchaseOrder.class, subject.id(), LockModeType.PESSIMISTIC_WRITE);
            if (po == null) missing();
            em.refresh(po, LockModeType.PESSIMISTIC_WRITE);
            if (!Objects.equals(po.getCompany().getId(), user.getCompany().getId()) || !po.canBeEditedBy(user)) forbidden();
        }
    }
    private Subject subjectFor(Long id) {
        PartQuantity q = quantity(id);
        if (q.getWorkOrder() != null) return new Subject("WORK_ORDER", q.getWorkOrder().getId());
        if (q.getPurchaseOrder() != null) return new Subject("PURCHASE_ORDER", q.getPurchaseOrder().getId());
        throw new CustomException("Part quantity has no parent", HttpStatus.CONFLICT);
    }
    private PartQuantity quantity(Long id) {
        return quantities.findById(id).orElseThrow(() ->
                new CustomException("PartQuantity not found", HttpStatus.NOT_FOUND));
    }
    private Part lockedPart(Long id, User user) {
        Part p = em.find(Part.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (p == null) missing();
        em.refresh(p, LockModeType.PESSIMISTIC_WRITE);
        if (!Objects.equals(p.getCompany().getId(), user.getCompany().getId()) || !p.canBeViewedBy(user)) forbidden();
        return p;
    }
    private void adjust(Part p, WorkOrder wo, double delta, User user) {
        if (delta != 0) parts.consumePart(p.getId(), delta, wo, Helper.getLocale(user), false);
    }
    static void validateQuantity(double value, Part part) {
        if (!Double.isFinite(value) || value < 0 || (part.getUnit() == null && value != Math.rint(value))) bad("Quantity must be finite, non-negative and whole for parts without a unit");
    }
    private static void bad(String message) { throw new CustomException(message, HttpStatus.BAD_REQUEST); }
    private static void conflict(String message) { throw new CustomException(message, HttpStatus.CONFLICT); }
    private static void forbidden() { throw new CustomException("Access denied", HttpStatus.FORBIDDEN); }
    private static void missing() { throw new CustomException("Order or part not found", HttpStatus.NOT_FOUND); }
}
