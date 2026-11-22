package com.propio.service;

import com.propio.dto.ReportSummaryDto;
import com.propio.model.Payment;
import com.propio.model.PaymentStatus;
import com.propio.model.Property;
import com.propio.model.Tenant;
import com.propio.model.Unit;
import com.propio.model.UnitStatus;
import com.propio.repository.PaymentRepository;
import com.propio.repository.PropertyRepository;
import com.propio.repository.TenantRepository;
import com.propio.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final PaymentRepository paymentRepository;
    private final PropertyRepository propertyRepository;
    private final TenantRepository tenantRepository;
    private final UnitRepository unitRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter MONTH_LABEL_FMT = DateTimeFormatter.ofPattern("MMM yyyy");
    private static final DateTimeFormatter MONTH_KEY_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Transactional(readOnly = true)
    public ReportSummaryDto generateReportSummary(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.now().minusMonths(6).withDayOfMonth(1);
        }
        if (endDate == null) {
            endDate = LocalDate.now().plusMonths(1).withDayOfMonth(1).minusDays(1);
        }

        // 1. Fetch payments within range using eager fetch join (zero N+1)
        List<Payment> payments = paymentRepository.findPaymentsForReport(startDate, endDate);

        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;
        BigDecimal totalPending = BigDecimal.ZERO;

        // Group payments by Month
        Map<String, List<Payment>> paymentsByMonth = new TreeMap<>();
        // Group payments by Property ID
        Map<Long, List<Payment>> paymentsByProperty = new HashMap<>();

        for (Payment p : payments) {
            BigDecimal amt = p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO;
            if (p.getStatus() == PaymentStatus.PAID) {
                totalPaid = totalPaid.add(amt);
            } else if (p.getStatus() == PaymentStatus.OVERDUE) {
                totalOverdue = totalOverdue.add(amt);
            } else if (p.getStatus() == PaymentStatus.PENDING) {
                totalPending = totalPending.add(amt);
            }

            LocalDate referenceDate = p.getPaidDate() != null ? p.getPaidDate() : p.getDueDate();
            if (referenceDate != null) {
                String monthKey = referenceDate.format(MONTH_KEY_FMT);
                paymentsByMonth.computeIfAbsent(monthKey, k -> new ArrayList<>()).add(p);
            }

            if (p.getLease() != null && p.getLease().getUnit() != null && p.getLease().getUnit().getProperty() != null) {
                Long propId = p.getLease().getUnit().getProperty().getId();
                paymentsByProperty.computeIfAbsent(propId, k -> new ArrayList<>()).add(p);
            }
        }

        BigDecimal totalInvoiced = totalPaid.add(totalOverdue).add(totalPending);
        double collectionRate = 0.0;
        BigDecimal collectionBase = totalPaid.add(totalOverdue);
        if (collectionBase.compareTo(BigDecimal.ZERO) > 0) {
            collectionRate = totalPaid.multiply(BigDecimal.valueOf(100))
                    .divide(collectionBase, 1, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        // 2. Units & Occupancy Metrics
        List<Property> properties = propertyRepository.findAll();
        List<Unit> allUnits = unitRepository.findAllWithProperty();
        long totalUnits = allUnits.size();
        long occupiedUnits = allUnits.stream().filter(u -> u.getStatus() == UnitStatus.OCCUPIED).count();
        long vacantUnits = allUnits.stream().filter(u -> u.getStatus() == UnitStatus.VACANT).count();
        long maintenanceUnits = allUnits.stream().filter(u -> u.getStatus() == UnitStatus.MAINTENANCE).count();

        double occupancyRate = totalUnits > 0
                ? Math.round(((double) occupiedUnits / totalUnits) * 1000.0) / 10.0
                : 0.0;

        // 3. Monthly Revenue Trend Items
        List<ReportSummaryDto.MonthlyRevenueItem> monthlyRevenueItems = new ArrayList<>();
        // If paymentsByMonth is empty, generate at least current month with zero
        if (paymentsByMonth.isEmpty()) {
            monthlyRevenueItems.add(ReportSummaryDto.MonthlyRevenueItem.builder()
                    .monthKey(LocalDate.now().format(MONTH_KEY_FMT))
                    .monthLabel(LocalDate.now().format(MONTH_LABEL_FMT))
                    .revenue(BigDecimal.ZERO)
                    .overdue(BigDecimal.ZERO)
                    .transactionCount(0)
                    .build());
        } else {
            for (Map.Entry<String, List<Payment>> entry : paymentsByMonth.entrySet()) {
                String mKey = entry.getKey();
                LocalDate parsedDate = LocalDate.parse(mKey + "-01");
                BigDecimal mPaid = BigDecimal.ZERO;
                BigDecimal mOverdue = BigDecimal.ZERO;
                long count = 0;

                for (Payment p : entry.getValue()) {
                    if (p.getStatus() == PaymentStatus.PAID) {
                        mPaid = mPaid.add(p.getAmount());
                        count++;
                    } else if (p.getStatus() == PaymentStatus.OVERDUE) {
                        mOverdue = mOverdue.add(p.getAmount());
                    }
                }

                monthlyRevenueItems.add(ReportSummaryDto.MonthlyRevenueItem.builder()
                        .monthKey(mKey)
                        .monthLabel(parsedDate.format(MONTH_LABEL_FMT))
                        .revenue(mPaid)
                        .overdue(mOverdue)
                        .transactionCount(count)
                        .build());
            }
        }

        // 4. Property Performance Report Items
        List<ReportSummaryDto.PropertyReportItem> propertyReportItems = new ArrayList<>();
        for (Property prop : properties) {
            List<Unit> propUnits = allUnits.stream()
                    .filter(u -> u.getProperty() != null && u.getProperty().getId().equals(prop.getId()))
                    .collect(Collectors.toList());

            long pTotalUnits = propUnits.size();
            long pOccupiedUnits = propUnits.stream().filter(u -> u.getStatus() == UnitStatus.OCCUPIED).count();
            double pOccupancyRate = pTotalUnits > 0
                    ? Math.round(((double) pOccupiedUnits / pTotalUnits) * 1000.0) / 10.0
                    : 0.0;

            List<Payment> pPayments = paymentsByProperty.getOrDefault(prop.getId(), Collections.emptyList());
            BigDecimal pPaid = BigDecimal.ZERO;
            BigDecimal pOverdue = BigDecimal.ZERO;

            for (Payment p : pPayments) {
                if (p.getStatus() == PaymentStatus.PAID) {
                    pPaid = pPaid.add(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
                } else if (p.getStatus() == PaymentStatus.OVERDUE) {
                    pOverdue = pOverdue.add(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
                }
            }

            double pCollectionRate = 100.0;
            BigDecimal pBase = pPaid.add(pOverdue);
            if (pBase.compareTo(BigDecimal.ZERO) > 0) {
                pCollectionRate = pPaid.multiply(BigDecimal.valueOf(100))
                        .divide(pBase, 1, RoundingMode.HALF_UP)
                        .doubleValue();
            }

            propertyReportItems.add(ReportSummaryDto.PropertyReportItem.builder()
                    .propertyId(prop.getId())
                    .propertyName(prop.getName())
                    .city(prop.getCity())
                    .totalUnits(pTotalUnits)
                    .occupiedUnits(pOccupiedUnits)
                    .occupancyRate(pOccupancyRate)
                    .revenueCollected(pPaid)
                    .outstandingOverdue(pOverdue)
                    .collectionRate(pCollectionRate)
                    .build());
        }

        return ReportSummaryDto.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalRevenueCollected(totalPaid)
                .totalOutstandingOverdue(totalOverdue)
                .totalInvoiced(totalInvoiced)
                .totalProperties(properties.size())
                .totalUnits(totalUnits)
                .occupiedUnits(occupiedUnits)
                .vacantUnits(vacantUnits)
                .maintenanceUnits(maintenanceUnits)
                .occupancyRate(occupancyRate)
                .collectionRate(collectionRate)
                .monthlyRevenue(monthlyRevenueItems)
                .propertyReports(propertyReportItems)
                .build();
    }

    @Transactional(readOnly = true)
    public void exportPaymentsCsv(LocalDate startDate, LocalDate endDate, PrintWriter writer) {
        List<Payment> payments = paymentRepository.findPaymentsForReport(startDate, endDate);

        // CSV Header
        writer.println("Payment ID,Transaction Reference,Tenant Name,Tenant Email,Property,Unit,Amount,Payment Method,Status,Due Date,Paid Date,Notes");

        for (Payment p : payments) {
            String tenantName = (p.getLease() != null && p.getLease().getTenant() != null)
                    ? p.getLease().getTenant().getFullName() : "N/A";
            String tenantEmail = (p.getLease() != null && p.getLease().getTenant() != null)
                    ? p.getLease().getTenant().getEmail() : "N/A";
            String property = (p.getLease() != null && p.getLease().getUnit() != null && p.getLease().getUnit().getProperty() != null)
                    ? p.getLease().getUnit().getProperty().getName() : "N/A";
            String unit = (p.getLease() != null && p.getLease().getUnit() != null)
                    ? p.getLease().getUnit().getUnitNumber() : "N/A";
            String amount = p.getAmount() != null ? p.getAmount().toPlainString() : "0.00";
            String method = p.getPaymentMethod() != null ? p.getPaymentMethod() : "N/A";
            String status = p.getStatus() != null ? p.getStatus().name() : "N/A";
            String dueDate = p.getDueDate() != null ? p.getDueDate().toString() : "";
            String paidDate = p.getPaidDate() != null ? p.getPaidDate().toString() : "";
            String notes = p.getNotes() != null ? p.getNotes().replace("\"", "\"\"") : "";

            writer.printf("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%s,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"%n",
                    p.getId(),
                    p.getTransactionReference() != null ? p.getTransactionReference() : "",
                    escapeCsv(tenantName),
                    escapeCsv(tenantEmail),
                    escapeCsv(property),
                    escapeCsv(unit),
                    amount,
                    escapeCsv(method),
                    status,
                    dueDate,
                    paidDate,
                    notes);
        }
        writer.flush();
    }

    @Transactional(readOnly = true)
    public void exportTenantsCsv(PrintWriter writer) {
        List<Tenant> tenants = tenantRepository.findAllWithLeaseDetails();

        // CSV Header
        writer.println("Tenant ID,First Name,Last Name,Email,Phone,Current Property,Current Unit,Lease Start,Lease End,Monthly Rent,Status");

        for (Tenant t : tenants) {
            String property = "No Active Lease";
            String unit = "N/A";
            String leaseStart = "";
            String leaseEnd = "";
            String rent = "0.00";
            String status = "Active";

            if (t.getLeases() != null && !t.getLeases().isEmpty()) {
                var activeLease = t.getLeases().stream()
                        .filter(l -> l.isActive())
                        .findFirst()
                        .orElse(t.getLeases().get(0));

                if (activeLease != null) {
                    if (activeLease.getUnit() != null) {
                        unit = activeLease.getUnit().getUnitNumber();
                        if (activeLease.getUnit().getProperty() != null) {
                            property = activeLease.getUnit().getProperty().getName();
                        }
                    }
                    if (activeLease.getStartDate() != null) leaseStart = activeLease.getStartDate().toString();
                    if (activeLease.getEndDate() != null) leaseEnd = activeLease.getEndDate().toString();
                    if (activeLease.getMonthlyRent() != null) rent = activeLease.getMonthlyRent().toPlainString();
                    status = activeLease.isActive() ? "Leased" : "Former";
                }
            }

            writer.printf("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%s,\"%s\"%n",
                    t.getId(),
                    escapeCsv(t.getFirstName()),
                    escapeCsv(t.getLastName()),
                    escapeCsv(t.getEmail()),
                    escapeCsv(t.getPhone()),
                    escapeCsv(property),
                    escapeCsv(unit),
                    leaseStart,
                    leaseEnd,
                    rent,
                    status);
        }
        writer.flush();
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
    }
}
