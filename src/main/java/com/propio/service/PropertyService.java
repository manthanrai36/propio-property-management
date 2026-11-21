package com.propio.service;

import com.propio.dto.PropertyCardDto;
import com.propio.dto.PropertyFilterDto;
import com.propio.dto.PropertyFormDto;
import com.propio.exception.PropertyDeletionBlockedException;
import com.propio.exception.ResourceNotFoundException;
import com.propio.model.Property;
import com.propio.model.Unit;
import com.propio.model.UnitStatus;
import com.propio.repository.LeaseRepository;
import com.propio.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final LeaseRepository leaseRepository;
    private final FileStorageService fileStorageService;

    private static final List<String> GRADIENT_PRESETS = Arrays.asList(
            "linear-gradient(135deg, #6C4CF1 0%, #8E74F8 100%)",
            "linear-gradient(135deg, #3B82F6 0%, #2DD4BF 100%)",
            "linear-gradient(135deg, #EC4899 0%, #8B5CF6 100%)",
            "linear-gradient(135deg, #10B981 0%, #06B6D4 100%)",
            "linear-gradient(135deg, #F59E0B 0%, #EF4444 100%)",
            "linear-gradient(135deg, #6366F1 0%, #A855F7 100%)"
    );

    @Transactional(readOnly = true)
    public Page<PropertyCardDto> getPropertiesPaged(PropertyFilterDto filter) {
        // Construct Sort
        Sort sort = Sort.by(Sort.Direction.ASC, "name");
        String sortBy = filter.getSortBy();
        if ("name_desc".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "name");
        } else if ("newest".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        } else if ("oldest".equalsIgnoreCase(sortBy)) {
            sort = Sort.by(Sort.Direction.ASC, "createdAt");
        }

        int pageNum = Math.max(0, filter.getPage());
        int pageSize = filter.getSize() > 0 ? filter.getSize() : 6;
        Pageable pageable = PageRequest.of(pageNum, pageSize, sort);

        String search = filter.getSearch() != null ? filter.getSearch().trim() : null;
        String propType = filter.getPropertyType() != null ? filter.getPropertyType().trim() : null;
        String status = filter.getStatus() != null ? filter.getStatus().trim() : null;

        UnitStatus unitStatus = null;
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
            try {
                unitStatus = UnitStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }

        Page<Property> propertyPage = propertyRepository.findWithFiltersAndStatus(
                search, propType, status, unitStatus, pageable
        );

        return propertyPage.map(this::mapToCardDto);
    }

    @Transactional(readOnly = true)
    public Property getPropertyById(Long id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
    }

    @Transactional(readOnly = true)
    public Property getPropertyWithUnits(Long id) {
        return propertyRepository.findByIdWithUnits(id)
                .orElseThrow(() -> new ResourceNotFoundException("Property", "id", id));
    }

    @Transactional(readOnly = true)
    public List<String> getAllPropertyTypes() {
        return propertyRepository.findDistinctPropertyTypes();
    }

    @Transactional
    public Property createProperty(PropertyFormDto dto) {
        log.info("Creating new property: {}", dto.getName());

        String uploadedImageUrl = null;
        if (dto.getImageFile() != null && !dto.getImageFile().isEmpty()) {
            uploadedImageUrl = fileStorageService.storePropertyImage(dto.getImageFile());
        }

        Property property = Property.builder()
                .name(dto.getName().trim())
                .address(dto.getAddress().trim())
                .city(dto.getCity().trim())
                .state(dto.getState().trim())
                .zipCode(dto.getZipCode().trim())
                .propertyType(dto.getPropertyType().trim())
                .description(dto.getDescription())
                .imageUrl(uploadedImageUrl != null ? uploadedImageUrl : dto.getExistingImageUrl())
                .build();

        return propertyRepository.save(property);
    }

    @Transactional
    public Property updateProperty(Long id, PropertyFormDto dto) {
        Property property = getPropertyById(id);
        log.info("Updating property ID {}: {}", id, dto.getName());

        if (dto.getImageFile() != null && !dto.getImageFile().isEmpty()) {
            String uploadedImageUrl = fileStorageService.storePropertyImage(dto.getImageFile());
            property.setImageUrl(uploadedImageUrl);
        } else if (dto.getExistingImageUrl() != null) {
            property.setImageUrl(dto.getExistingImageUrl().trim());
        }

        property.setName(dto.getName().trim());
        property.setAddress(dto.getAddress().trim());
        property.setCity(dto.getCity().trim());
        property.setState(dto.getState().trim());
        property.setZipCode(dto.getZipCode().trim());
        property.setPropertyType(dto.getPropertyType().trim());
        property.setDescription(dto.getDescription());

        return propertyRepository.save(property);
    }

    @Transactional
    public void deleteProperty(Long id) {
        Property property = getPropertyById(id);
        log.info("Attempting to delete property ID {}: {}", id, property.getName());

        long activeLeasesCount = leaseRepository.countActiveLeasesByPropertyId(id);
        if (activeLeasesCount > 0) {
            log.warn("Cannot delete property ID {} - {} active lease(s) exist", id, activeLeasesCount);
            throw new PropertyDeletionBlockedException(id, property.getName(), activeLeasesCount);
        }

        propertyRepository.delete(property);
        log.info("Successfully deleted property ID {}: {}", id, property.getName());
    }

    public PropertyCardDto mapToCardDto(Property prop) {
        List<Unit> units = prop.getUnits();
        int total = units != null ? units.size() : 0;
        int occupied = 0;
        int vacant = 0;
        int booked = 0;
        BigDecimal minRent = null;
        BigDecimal maxRent = null;

        if (units != null && !units.isEmpty()) {
            for (Unit u : units) {
                if (u.getStatus() == UnitStatus.OCCUPIED) occupied++;
                else if (u.getStatus() == UnitStatus.VACANT) vacant++;
                else if (u.getStatus() == UnitStatus.BOOKED) booked++;

                BigDecimal rent = u.getRentAmount();
                if (rent != null) {
                    if (minRent == null || rent.compareTo(minRent) < 0) minRent = rent;
                    if (maxRent == null || rent.compareTo(maxRent) > 0) maxRent = rent;
                }
            }
        }

        String badge;
        if (total == 0) {
            badge = "No Units";
        } else if (vacant == 0) {
            badge = "Fully Occupied";
        } else {
            badge = vacant + " Vacant";
        }

        int hash = Math.abs(prop.getName().hashCode());
        String gradient = GRADIENT_PRESETS.get(hash % GRADIENT_PRESETS.size());

        return PropertyCardDto.builder()
                .id(prop.getId())
                .name(prop.getName())
                .address(prop.getAddress())
                .city(prop.getCity())
                .state(prop.getState())
                .zipCode(prop.getZipCode())
                .propertyType(prop.getPropertyType())
                .description(prop.getDescription())
                .imageUrl(prop.getImageUrl())
                .totalUnits(total)
                .occupiedUnits(occupied)
                .vacantUnits(vacant)
                .bookedUnits(booked)
                .minRent(minRent)
                .maxRent(maxRent)
                .statusBadge(badge)
                .gradientFallback(gradient)
                .build();
    }
}
