package com.propio.controller;

import com.propio.config.CustomUserDetails;
import com.propio.dto.AdminSettingsDto;
import com.propio.dto.ChangePasswordDto;
import com.propio.dto.UpdateProfileDto;
import com.propio.model.User;
import com.propio.service.SystemSettingService;
import com.propio.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings")
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

    private final UserService userService;
    private final SystemSettingService systemSettingService;

    @GetMapping
    public String showSettings(
            @RequestParam(name = "tab", required = false, defaultValue = "profile") String activeTab,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        User user = userService.getUserById(userDetails.getId());

        UpdateProfileDto profileDto = new UpdateProfileDto();
        profileDto.setFullName(user.getFullName());
        profileDto.setEmail(user.getEmail());
        profileDto.setPhone(user.getPhone());
        profileDto.setThemePreference(user.getThemePreference() != null ? user.getThemePreference() : "system");
        profileDto.setNotifyRentDue(user.isNotifyRentDue());
        profileDto.setNotifyPaymentReceived(user.isNotifyPaymentReceived());
        profileDto.setNotifyMaintenance(user.isNotifyMaintenance());
        profileDto.setNotifyLeaseExpiry(user.isNotifyLeaseExpiry());

        model.addAttribute("currentUser", user);
        model.addAttribute("profileDto", profileDto);
        model.addAttribute("passwordDto", new ChangePasswordDto());
        model.addAttribute("adminSettingsDto", systemSettingService.getAdminSettings());
        model.addAttribute("activeTab", activeTab);
        model.addAttribute("activeNav", "settings");
        model.addAttribute("pageTitle", "Preferences & Settings");

        return "settings/index";
    }

    @PostMapping("/profile")
    public String updateProfile(
            @Valid @ModelAttribute("profileDto") UpdateProfileDto dto,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please correct the highlighted profile errors.");
            return "redirect:/settings?tab=profile";
        }

        try {
            userService.updateProfile(userDetails.getId(), dto);
            redirectAttributes.addFlashAttribute("successMessage", "Profile details and photo updated successfully.");
        } catch (Exception ex) {
            log.error("Profile update failed for user #{}: {}", userDetails.getId(), ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/settings?tab=profile";
    }

    @PostMapping("/password")
    public String changePassword(
            @Valid @ModelAttribute("passwordDto") ChangePasswordDto dto,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/settings?tab=security";
        }

        try {
            userService.changePassword(userDetails.getId(), dto);
            redirectAttributes.addFlashAttribute("successMessage", "Password updated successfully with BCrypt security.");
        } catch (Exception ex) {
            log.warn("Password change failed for user #{}: {}", userDetails.getId(), ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/settings?tab=security";
    }

    @PostMapping("/preferences")
    public String updatePreferences(
            @ModelAttribute("profileDto") UpdateProfileDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        try {
            userService.updatePreferences(userDetails.getId(), dto);
            redirectAttributes.addFlashAttribute("successMessage", "Display theme and notification preferences saved.");
        } catch (Exception ex) {
            log.error("Failed saving preferences for user #{}: {}", userDetails.getId(), ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/settings?tab=preferences";
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String updateAdminSettings(
            @Valid @ModelAttribute("adminSettingsDto") AdminSettingsDto dto,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/settings?tab=system";
        }

        try {
            systemSettingService.saveAdminSettings(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Billing cycle and notification parameters saved.");
        } catch (Exception ex) {
            log.error("Failed saving admin settings: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/settings?tab=system";
    }
}
