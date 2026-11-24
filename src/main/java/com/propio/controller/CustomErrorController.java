package com.propio.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Custom Error Controller replacing Spring Boot's Whitelabel Error Page.
 * Routes 403, 404, 500, and unexpected errors to styled Propio SaaS error views.
 */
@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        int statusCode = 500;
        if (status != null) {
            try {
                statusCode = Integer.parseInt(status.toString());
            } catch (NumberFormatException ignored) {}
        }

        model.addAttribute("status", statusCode);
        model.addAttribute("requestedUri", uri != null ? uri.toString() : request.getRequestURI());
        model.addAttribute("errorMessage", message != null && !message.toString().isBlank() ?
                message.toString() : getDefaultMessageForStatus(statusCode));

        if (statusCode == 403) {
            return "error/403";
        } else if (statusCode == 404) {
            return "error/404";
        } else if (statusCode == 500) {
            return "error/500";
        }
        return "error/error";
    }

    @RequestMapping("/error/403")
    public String error403(Model model, HttpServletRequest request) {
        model.addAttribute("status", 403);
        model.addAttribute("requestedUri", request.getRequestURI());
        model.addAttribute("errorMessage", "Access Denied: You do not have permission to access this resource or perform this action.");
        return "error/403";
    }

    @RequestMapping("/error/404")
    public String error404(Model model, HttpServletRequest request) {
        model.addAttribute("status", 404);
        model.addAttribute("requestedUri", request.getRequestURI());
        model.addAttribute("errorMessage", "The requested property, unit, or record could not be found.");
        return "error/404";
    }

    @RequestMapping("/error/500")
    public String error500(Model model, HttpServletRequest request) {
        model.addAttribute("status", 500);
        model.addAttribute("requestedUri", request.getRequestURI());
        model.addAttribute("errorMessage", "An unexpected server error occurred. Please try again later.");
        return "error/500";
    }

    private String getDefaultMessageForStatus(int statusCode) {
        return switch (statusCode) {
            case 403 -> "You do not have permission to access this resource or execute this operation.";
            case 404 -> "The requested page or resource could not be found.";
            case 500 -> "An unexpected internal server error occurred.";
            default -> "An unexpected error occurred while processing your request.";
        };
    }
}
