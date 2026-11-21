package com.propio.service;

import com.propio.dto.LeaseFormDto;
import com.propio.exception.LeaseOverlapException;
import com.propio.exception.ResourceNotFoundException;
import com.propio.model.*;
import com.propio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service orchestrating lease agreements, business invariant checks
 * (non-overlapping dates, unit status transitions), and automated billing generation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LeaseService {

    private final LeaseRepository leaseRepository;
    private final UnitRepository unitRepository;
    private final TenantRepository tenantRepository;
    private final PaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public Page<Lease> getLeasesPaged(String keyword, String filter, Pageable pageable) {
        Page<Lease> page = leaseRepository.searchLeasesWithDetails(keyword != null ? keyword.trim() : "", pageable);

        if (filter != null && !filter.isBlank() && !"ALL".equalsIgnoreCase(filter)) {
            List<Lease> filtered = page.getContent().stream()
                    .filter(l -> matchesFilter(l, filter))
                    .collect(Collectors.toList());
            return new PageImpl<>(filtered, pageable, page.getTotalElements());
        }

        return page;
    }

    private boolean matchesFilter(Lease lease, String filter) {
        if ("ACTIVE".equalsIgnoreCase(filter)) {
            return lease.isActive();
        }
        if ("EXPIRING_SOON".equalsIgnoreCase(filter)) {
            return isExpiringSoon(lease);
        }
        if ("ENDED".equalsIgnoreCase(filter)) {
            return !lease.isActive();
        }
        return true;
    }

    @Transactional(readOnly = true)
    public Lease getLeaseById(Long id) {
        return leaseRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lease not found with ID: " + id));
    }

    @Transactional(readOnly = true)
    public List<Lease> getLeasesByTenantId(Long tenantId) {
        return leaseRepository.findByTenantIdWithDetails(tenantId);
    }

    /**
     * Creates a new lease agreement under strict business constraints:
     * 1. End date must be strictly after start date.
     * 2. Non-overlap invariant: [S_new, E_new] must not overlap with any active lease on the target unit.
     * 3. Target unit status transitions to OCCUPIED.
     * 4. Monthly payment schedule generated automatically from start to end date.
     */
    @Transactional
    public Lease createLease(LeaseFormDto dto) {
        // Business Rule 1: Date sanity check
        if (!dto.getEndDate().isAfter(dto.getStartDate())) {
            throw new IllegalArgumentException("Lease end date (" + dto.getEndDate() +
                    ") must be strictly after start date (" + dto.getStartDate() + ").");
        }

        Unit unit = unitRepository.findById(dto.getUnitId())
                .orElseThrow(() -> new ResourceNotFoundException("Property Unit not found with ID: " + dto.getUnitId()));

        Tenant tenant = tenantRepository.findById(dto.getTenantId())
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found with ID: " + dto.getTenantId()));

        // Business Rule 2: Non-overlapping active leases check
        long overlapping = leaseRepository.countOverlappingActiveLeases(
                unit.getId(),
                dto.getStartDate(),
                dto.getEndDate(),
                null
        );

        if (overlapping > 0) {
            throw new LeaseOverlapException("Unit '" + unit.getUnitNumber() +
                    "' already has an active lease overlapping the selected date range (" +
                    dto.getStartDate() + " to " + dto.getEndDate() + ").");
        }

        // Business Rule 3: Creating a lease sets unit status to OCCUPIED
        unit.setStatus(UnitStatus.OCCUPIED);
        unitRepository.save(unit);

        BigDecimal deposit = dto.getSecurityDeposit() != null ? dto.getSecurityDeposit() : dto.getMonthlyRent();

        Lease lease = Lease.builder()
                .unit(unit)
                .tenant(tenant)
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .monthlyRent(dto.getMonthlyRent())
                .securityDeposit(deposit)
                .active(true)
                .build();

        lease = leaseRepository.save(lease);

        // Business Rule 4: Generate automated monthly payment schedule
        List<Payment> paymentSchedule = generatePaymentSchedule(lease);
        paymentRepository.saveAll(paymentSchedule);

        log.info("Successfully created lease ID: {} for Tenant '{}' on Unit '{}' ({} monthly payments generated)",
                lease.getId(), tenant.getFullName(), unit.getUnitNumber(), paymentSchedule.size());

        return lease;
    }

    /**
     * Terminating or ending a lease sets lease active to false and returns unit to VACANT
     * if no other active leases remain.
     */
    @Transactional
    public void terminateLease(Long leaseId) {
        Lease lease = getLeaseById(leaseId);
        lease.setActive(false);
        leaseRepository.save(lease);

        // Check remaining active leases on the unit
        Unit unit = lease.getUnit();
        long otherActive = leaseRepository.countActiveLeasesByUnitId(unit.getId());
        if (otherActive == 0) {
            unit.setStatus(UnitStatus.VACANT);
            unitRepository.save(unit);
            log.info("Unit '{}' transitioned back to VACANT as all active leases ended.", unit.getUnitNumber());
        }

        log.info("Terminated lease ID: {}", leaseId);
    }

    /**
     * Generates a monthly Payment entity for every billing month between startDate and endDate.
     */
    public List<Payment> generatePaymentSchedule(Lease lease) {
        List<Payment> schedule = new ArrayList<>();
        LocalDate cur = lease.getStartDate();
        LocalDate end = lease.getEndDate();
        LocalDate today = LocalDate.now();

        int monthSeq = 1;
        while (!cur.isAfter(end)) {
            PaymentStatus status;
            if (cur.isBefore(today)) {
                status = PaymentStatus.OVERDUE;
            } else {
                status = PaymentStatus.PENDING;
            }

            String ref = String.format("INV-%d-%02d%02d", lease.getId(), cur.getYear() % 100, cur.getMonthValue());
            String propName = lease.getUnit() != null && lease.getUnit().getProperty() != null
                    ? lease.getUnit().getProperty().getName() : "Property";
            String unitNum = lease.getUnit() != null ? lease.getUnit().getUnitNumber() : "Unit";

            String notes = String.format("Monthly Rent for %s %d - %s (%s)",
                    cur.getMonth().name(), cur.getYear(), propName, unitNum);

            Payment payment = Payment.builder()
                    .lease(lease)
                    .amount(lease.getMonthlyRent())
                    .dueDate(cur)
                    .status(status)
                    .paymentMethod("UNPAID")
                    .transactionReference(ref)
                    .notes(notes)
                    .build();

            schedule.add(payment);
            cur = cur.plusMonths(1);
            monthSeq++;
        }

        return schedule;
    }

    public boolean isExpiringSoon(Lease lease) {
        if (!lease.isActive() || lease.getEndDate() == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        LocalDate end = lease.getEndDate();
        return !end.isBefore(today) && end.isBefore(today.plusDays(60));
    }

    @Transactional(readOnly = true)
    public long countActiveLeases() {
        return leaseRepository.countByActiveTrue();
    }

    @Transactional(readOnly = true)
    public long countExpiringSoonLeases() {
        return leaseRepository.countExpiringSoon(LocalDate.now(), LocalDate.now().plusDays(60));
    }

    @Transactional(readOnly = true)
    public BigDecimal getActiveMonthlyRentSum() {
        return leaseRepository.sumActiveMonthlyRent();
    }
}
