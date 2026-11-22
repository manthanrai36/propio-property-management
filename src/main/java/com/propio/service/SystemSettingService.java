package com.propio.service;

import com.propio.dto.AdminSettingsDto;
import com.propio.model.SystemSetting;
import com.propio.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingService {

    private final SystemSettingRepository settingRepository;

    @Value("${propio.billing.late-fee-percentage:5.0}")
    private double defaultLateFeePercentage;

    public static final String KEY_LATE_FEE_PERCENTAGE = "billing.late_fee_percentage";
    public static final String KEY_INVOICE_DAY = "billing.invoice_day";
    public static final String KEY_REMINDER_DAYS = "billing.reminder_days";

    @Transactional(readOnly = true)
    public BigDecimal getLateFeePercentage() {
        return settingRepository.findByKeyName(KEY_LATE_FEE_PERCENTAGE)
                .map(s -> {
                    try {
                        return new BigDecimal(s.getKeyValue());
                    } catch (Exception e) {
                        return BigDecimal.valueOf(defaultLateFeePercentage);
                    }
                })
                .orElse(BigDecimal.valueOf(defaultLateFeePercentage));
    }

    @Transactional(readOnly = true)
    public int getInvoiceDay() {
        return settingRepository.findByKeyName(KEY_INVOICE_DAY)
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getKeyValue());
                    } catch (Exception e) {
                        return 1;
                    }
                })
                .orElse(1);
    }

    @Transactional(readOnly = true)
    public int getReminderDays() {
        return settingRepository.findByKeyName(KEY_REMINDER_DAYS)
                .map(s -> {
                    try {
                        return Integer.parseInt(s.getKeyValue());
                    } catch (Exception e) {
                        return 3;
                    }
                })
                .orElse(3);
    }

    @Transactional(readOnly = true)
    public AdminSettingsDto getAdminSettings() {
        AdminSettingsDto dto = new AdminSettingsDto();
        dto.setLateFeePercentage(getLateFeePercentage());
        dto.setInvoiceDay(getInvoiceDay());
        dto.setReminderDays(getReminderDays());
        return dto;
    }

    @Transactional
    public void saveAdminSettings(AdminSettingsDto dto) {
        saveOrUpdateSetting(KEY_LATE_FEE_PERCENTAGE, dto.getLateFeePercentage().toPlainString(), "Late fee surcharge percentage applied to overdue invoices");
        saveOrUpdateSetting(KEY_INVOICE_DAY, String.valueOf(dto.getInvoiceDay()), "Day of month when rent invoices are generated");
        saveOrUpdateSetting(KEY_REMINDER_DAYS, String.valueOf(dto.getReminderDays()), "Days prior to invoice due date when automated reminders are dispatched");
        log.info("Updated admin system settings: LateFee={}%, InvoiceDay={}, ReminderDays={}",
                dto.getLateFeePercentage(), dto.getInvoiceDay(), dto.getReminderDays());
    }

    private void saveOrUpdateSetting(String key, String value, String description) {
        SystemSetting setting = settingRepository.findByKeyName(key)
                .orElse(SystemSetting.builder()
                        .keyName(key)
                        .description(description)
                        .build());
        setting.setKeyValue(value);
        setting.setDescription(description);
        settingRepository.save(setting);
    }
}
