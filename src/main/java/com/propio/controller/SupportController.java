package com.propio.controller;

import com.propio.config.CustomUserDetails;
import com.propio.dto.SupportMessageDto;
import com.propio.model.Role;
import com.propio.model.SupportMessage;
import com.propio.model.User;
import com.propio.service.SupportService;
import com.propio.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/support")
@RequiredArgsConstructor
@Slf4j
public class SupportController {

    private final SupportService supportService;
    private final UserService userService;

    @GetMapping
    public String showSupportCenter(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        User user = null;
        SupportMessageDto formDto = new SupportMessageDto();

        if (userDetails != null) {
            user = userService.getUserById(userDetails.getId());
            formDto.setName(user.getFullName());
            formDto.setEmail(user.getEmail());
        }

        if (user != null && user.getRole() == Role.ROLE_ADMIN) {
            List<SupportMessage> adminInbox = supportService.getAllMessages();
            model.addAttribute("adminInbox", adminInbox);
        } else if (user != null) {
            List<SupportMessage> userTickets = supportService.getMessagesForUser(user);
            model.addAttribute("userTickets", userTickets);
        }

        model.addAttribute("supportForm", formDto);
        model.addAttribute("activeNav", "support");
        model.addAttribute("pageTitle", "Help & Support Center");

        return "support/index";
    }

    @PostMapping("/contact")
    public String submitContactForm(
            @Valid @ModelAttribute("supportForm") SupportMessageDto dto,
            BindingResult bindingResult,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/support";
        }

        try {
            User user = (userDetails != null) ? userService.getUserById(userDetails.getId()) : null;
            SupportMessage message = supportService.submitMessage(dto, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Support ticket #SUP-" + message.getId() + " received! Our property team will follow up at " + dto.getEmail() + ".");
        } catch (Exception ex) {
            log.error("Failed to submit support contact inquiry: {}", ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Error submitting inquiry: " + ex.getMessage());
        }

        return "redirect:/support";
    }
}
