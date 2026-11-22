package com.propio.controller;

import com.propio.config.CustomUserDetails;
import com.propio.model.Document;
import com.propio.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller managing digital documents archive, contracts, inspection reports,
 * and tenant-isolated document portal.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentRepository documentRepository;

    @GetMapping("/documents")
    @PreAuthorize("hasAnyRole('ADMIN', 'TENANT')")
    public String documents(
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "search", required = false) String search,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        model.addAttribute("activeNav", "documents");
        model.addAttribute("user", userDetails);

        boolean isTenant = userDetails != null && userDetails.isTenant();
        model.addAttribute("isTenant", isTenant);

        List<Document> docs;
        if (isTenant) {
            Long tenantId = userDetails.getTenantId();
            docs = (tenantId != null)
                    ? documentRepository.findByTenantIdWithDetails(tenantId)
                    : Collections.emptyList();
        } else {
            docs = documentRepository.findAllWithDetails();
        }

        // Apply in-memory category filter
        if (category != null && !category.isBlank() && !"ALL".equalsIgnoreCase(category)) {
            docs = docs.stream()
                    .filter(d -> category.equalsIgnoreCase(d.getCategory()))
                    .collect(Collectors.toList());
        }

        // Apply in-memory search filter
        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase();
            docs = docs.stream()
                    .filter(d -> (d.getFileName() != null && d.getFileName().toLowerCase().contains(q)) ||
                                 (d.getCategory() != null && d.getCategory().toLowerCase().contains(q)) ||
                                 (d.getTenant() != null && d.getTenant().getFullName().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }

        model.addAttribute("documents", docs);
        model.addAttribute("category", category != null ? category : "ALL");
        model.addAttribute("search", search);

        return "documents/list";
    }
}
