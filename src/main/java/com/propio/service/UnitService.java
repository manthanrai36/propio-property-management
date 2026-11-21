package com.propio.service;

import com.propio.dto.UnitFormDto;
import com.propio.exception.PropertyDeletionBlockedException;
import com.propio.exception.ResourceNotFoundException;
import com.propio.model.Property;
import com.propio.model.Unit;
import com.propio.repository.LeaseRepository;
import com.propio.repository.PropertyRepository;
import com.propio.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnitService {

    private final UnitRepository unitRepository;
    private final PropertyRepository propertyRepository;
    private final LeaseRepository leaseRepository;

    @Transactional(readOnly = true)
    public List<Unit> getUnitsByPropertyId(Long propertyId) {
        return unitRepository.findByPropertyIdOrderByUnitNumberAsc(propertyId);
    }

    @Transactional(readOnly = true)
    public Unit getUnitById(Long unitId) {
        return unitRepository.findById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", unitId));
    }

    @Transactional
    public Unit createUnit(Long propertyId, UnitFormDto dto) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", propertyId));

        log.info("Creating unit '{}' for property ID {}", dto.getUnitNumber(), propertyId);

        Unit unit = Unit.builder()
                .unitNumber(dto.getUnitNumber().trim())
                .bedrooms(dto.getBedrooms() != null ? dto.getBedrooms() : 1)
                .bathrooms(dto.getBathrooms() != null ? dto.getBathrooms() : 1)
                .squareFeet(dto.getSquareFeet())
                .rentAmount(dto.getRentAmount())
                .depositAmount(dto.getDepositAmount() != null ? dto.getDepositAmount() : dto.getRentAmount())
                .status(dto.getStatus())
                .property(property)
                .build();

        return unitRepository.save(unit);
    }

    @Transactional
    public Unit updateUnit(Long unitId, UnitFormDto dto) {
        Unit unit = getUnitById(unitId);
        log.info("Updating unit ID {}: '{}'", unitId, dto.getUnitNumber());

        unit.setUnitNumber(dto.getUnitNumber().trim());
        unit.setBedrooms(dto.getBedrooms() != null ? dto.getBedrooms() : unit.getBedrooms());
        unit.setBathrooms(dto.getBathrooms() != null ? dto.getBathrooms() : unit.getBathrooms());
        unit.setSquareFeet(dto.getSquareFeet());
        unit.setRentAmount(dto.getRentAmount());
        unit.setDepositAmount(dto.getDepositAmount());
        unit.setStatus(dto.getStatus());

        return unitRepository.save(unit);
    }

    @Transactional
    public void deleteUnit(Long unitId) {
        Unit unit = getUnitById(unitId);
        long activeLeasesCount = leaseRepository.countActiveLeasesByUnitId(unitId);

        if (activeLeasesCount > 0) {
            log.warn("Cannot delete unit ID {} - {} active lease(s) exist", unitId, activeLeasesCount);
            throw new PropertyDeletionBlockedException(
                    unit.getProperty().getId(),
                    unit.getProperty().getName() + " - Unit " + unit.getUnitNumber(),
                    activeLeasesCount
            );
        }

        unitRepository.delete(unit);
        log.info("Successfully deleted unit ID {}", unitId);
    }
}
