package com.propio.controller;

import com.propio.config.CustomUserDetails;
import com.propio.dto.PropertyCardDto;
import com.propio.dto.PropertyFilterDto;
import com.propio.dto.PropertyFormDto;
import com.propio.dto.UnitFormDto;
import com.propio.model.Property;
import com.propio.model.Unit;
import com.propio.model.UnitStatus;
import com.propio.service.PropertyService;
import com.propio.service.UnitService;
import com.propio.repository.LeaseRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/properties")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class PropertyController {

    private final PropertyService propertyService;
    private final UnitService unitService;
    private final LeaseRepository leaseRepository;

    @GetMapping
    public String listProperties(@ModelAttribute("filter") PropertyFilterDto filter,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 Model model) {
        model.addAttribute("activeNav", "properties");
        model.addAttribute("user", userDetails);

        Page<PropertyCardDto> propertyPage = propertyService.getPropertiesPaged(filter);
        model.addAttribute("propertyPage", propertyPage);
        model.addAttribute("properties", propertyPage.getContent());
        model.addAttribute("propertyTypes", propertyService.getAllPropertyTypes());
        model.addAttribute("unitStatuses", UnitStatus.values());

        return "properties/list";
    }

    @GetMapping("/new")
    public String showCreateForm(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        model.addAttribute("activeNav", "properties");
        model.addAttribute("user", userDetails);
        model.addAttribute("propertyForm", new PropertyFormDto());
        model.addAttribute("propertyTypes", propertyService.getAllPropertyTypes());
        model.addAttribute("isEdit", false);
        return "properties/form";
    }

    @PostMapping
    public String createProperty(@Valid @ModelAttribute("propertyForm") PropertyFormDto form,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("activeNav", "properties");
            model.addAttribute("user", userDetails);
            model.addAttribute("isEdit", false);
            model.addAttribute("propertyTypes", propertyService.getAllPropertyTypes());
            return "properties/form";
        }

        Property created = propertyService.createProperty(form);
        redirectAttributes.addFlashAttribute("successMessage",
                "Property '" + created.getName() + "' created successfully!");
        return "redirect:/properties/" + created.getId();
    }

    @GetMapping("/{id}")
    public String propertyDetails(@PathVariable("id") Long id,
                                  @AuthenticationPrincipal CustomUserDetails userDetails,
                                  Model model) {
        Property property = propertyService.getPropertyById(id);
        List<Unit> units = unitService.getUnitsByPropertyId(id);

        int totalUnits = units.size();
        int occupied = 0;
        int vacant = 0;
        int booked = 0;
        BigDecimal totalRent = BigDecimal.ZERO;

        for (Unit u : units) {
            if (u.getStatus() == UnitStatus.OCCUPIED) occupied++;
            else if (u.getStatus() == UnitStatus.VACANT) vacant++;
            else if (u.getStatus() == UnitStatus.BOOKED) booked++;

            if (u.getRentAmount() != null) {
                totalRent = totalRent.add(u.getRentAmount());
            }
        }

        double occupancyRate = totalUnits > 0 ? ((double) occupied / totalUnits) * 100 : 0.0;
        long activeLeasesCount = leaseRepository.countActiveLeasesByPropertyId(id);

        model.addAttribute("activeNav", "properties");
        model.addAttribute("user", userDetails);
        model.addAttribute("property", property);
        model.addAttribute("units", units);
        model.addAttribute("totalUnits", totalUnits);
        model.addAttribute("occupiedCount", occupied);
        model.addAttribute("vacantCount", vacant);
        model.addAttribute("bookedCount", booked);
        model.addAttribute("totalMonthlyRent", totalRent);
        model.addAttribute("occupancyRate", Math.round(occupancyRate));
        model.addAttribute("activeLeasesCount", activeLeasesCount);
        model.addAttribute("unitForm", UnitFormDto.builder().propertyId(id).build());
        model.addAttribute("unitStatuses", UnitStatus.values());

        return "properties/details";
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable("id") Long id,
                               @AuthenticationPrincipal CustomUserDetails userDetails,
                               Model model) {
        Property property = propertyService.getPropertyById(id);

        PropertyFormDto form = PropertyFormDto.builder()
                .id(property.getId())
                .name(property.getName())
                .address(property.getAddress())
                .city(property.getCity())
                .state(property.getState())
                .zipCode(property.getZipCode())
                .propertyType(property.getPropertyType())
                .description(property.getDescription())
                .existingImageUrl(property.getImageUrl())
                .build();

        model.addAttribute("activeNav", "properties");
        model.addAttribute("user", userDetails);
        model.addAttribute("propertyForm", form);
        model.addAttribute("propertyId", id);
        model.addAttribute("propertyTypes", propertyService.getAllPropertyTypes());
        model.addAttribute("isEdit", true);

        return "properties/form";
    }

    @PostMapping("/{id}/edit")
    public String updateProperty(@PathVariable("id") Long id,
                                 @Valid @ModelAttribute("propertyForm") PropertyFormDto form,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal CustomUserDetails userDetails,
                                 RedirectAttributes redirectAttributes,
                                 Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("activeNav", "properties");
            model.addAttribute("user", userDetails);
            model.addAttribute("isEdit", true);
            model.addAttribute("propertyId", id);
            model.addAttribute("propertyTypes", propertyService.getAllPropertyTypes());
            return "properties/form";
        }

        propertyService.updateProperty(id, form);
        redirectAttributes.addFlashAttribute("successMessage",
                "Property '" + form.getName() + "' updated successfully!");
        return "redirect:/properties/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deleteProperty(@PathVariable("id") Long id, RedirectAttributes redirectAttributes) {
        propertyService.deleteProperty(id);
        redirectAttributes.addFlashAttribute("successMessage", "Property deleted successfully!");
        return "redirect:/properties";
    }

    @GetMapping("/{id}/delete")
    public String deletePropertyGetFallback(@PathVariable("id") Long id) {
        return "redirect:/properties/" + id;
    }

    @PostMapping("/{propertyId}/units")
    public String addUnit(@PathVariable("propertyId") Long propertyId,
                          @Valid @ModelAttribute("unitForm") UnitFormDto unitForm,
                          BindingResult bindingResult,
                          RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String error = bindingResult.getFieldError() != null ?
                    bindingResult.getFieldError().getDefaultMessage() : "Invalid unit details";
            redirectAttributes.addFlashAttribute("errorMessage", error);
            return "redirect:/properties/" + propertyId;
        }

        unitService.createUnit(propertyId, unitForm);
        redirectAttributes.addFlashAttribute("successMessage",
                "Unit " + unitForm.getUnitNumber() + " added successfully!");
        return "redirect:/properties/" + propertyId;
    }

    @PostMapping("/{propertyId}/units/{unitId}/edit")
    public String editUnit(@PathVariable("propertyId") Long propertyId,
                           @PathVariable("unitId") Long unitId,
                           @Valid @ModelAttribute("unitForm") UnitFormDto unitForm,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            String error = bindingResult.getFieldError() != null ?
                    bindingResult.getFieldError().getDefaultMessage() : "Invalid unit details";
            redirectAttributes.addFlashAttribute("errorMessage", error);
            return "redirect:/properties/" + propertyId;
        }

        unitService.updateUnit(unitId, unitForm);
        redirectAttributes.addFlashAttribute("successMessage",
                "Unit " + unitForm.getUnitNumber() + " updated successfully!");
        return "redirect:/properties/" + propertyId;
    }

    @GetMapping("/{propertyId}/units/{unitId}/edit")
    public String editUnitGetFallback(@PathVariable("propertyId") Long propertyId) {
        return "redirect:/properties/" + propertyId;
    }

    @PostMapping("/{propertyId}/units/{unitId}/delete")
    public String deleteUnit(@PathVariable("propertyId") Long propertyId,
                             @PathVariable("unitId") Long unitId,
                             RedirectAttributes redirectAttributes) {
        unitService.deleteUnit(unitId);
        redirectAttributes.addFlashAttribute("successMessage", "Unit deleted successfully!");
        return "redirect:/properties/" + propertyId;
    }

    @GetMapping("/{propertyId}/units/{unitId}/delete")
    public String deleteUnitGetFallback(@PathVariable("propertyId") Long propertyId) {
        return "redirect:/properties/" + propertyId;
    }
}
