package com.propio.controller;

import com.propio.config.CustomUserDetails;
import com.propio.dto.SearchResultDto;
import com.propio.model.User;
import com.propio.service.SearchService;
import com.propio.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Collections;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class SearchController {

    private final SearchService searchService;
    private final UserService userService;

    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<List<SearchResultDto>> apiSearch(
            @RequestParam(name = "q", required = false) String query,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null || query == null || query.trim().length() < 2) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        User user = userService.getUserById(userDetails.getId());
        List<SearchResultDto> results = searchService.search(query, user);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search")
    public String viewSearchResults(
            @RequestParam(name = "q", required = false) String query,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        List<SearchResultDto> results = Collections.emptyList();
        if (userDetails != null && query != null && !query.trim().isBlank()) {
            User user = userService.getUserById(userDetails.getId());
            results = searchService.search(query, user);
        }

        model.addAttribute("query", query != null ? query.trim() : "");
        model.addAttribute("results", results);
        model.addAttribute("resultsCount", results.size());
        model.addAttribute("activeNav", "search");
        model.addAttribute("pageTitle", "Search Results for '" + (query != null ? query : "") + "'");

        return "search/results";
    }
}
