package com.propio.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({ResourceNotFoundException.class, org.springframework.web.servlet.resource.NoResourceFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(Exception ex, Model model, HttpServletRequest request) {
        log.warn("Resource not found at {}: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("errorMessage", "The requested page or resource '" + request.getRequestURI() + "' could not be found.");
        model.addAttribute("requestedUri", request.getRequestURI());
        model.addAttribute("status", 404);
        return "error/404";
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(org.springframework.security.access.AccessDeniedException ex, Model model, HttpServletRequest request) {
        log.warn("Access denied at {}: {}", request.getRequestURI(), ex.getMessage());
        model.addAttribute("errorMessage", "Access Denied: You do not have permission to access this resource or perform this action.");
        model.addAttribute("requestedUri", request.getRequestURI());
        model.addAttribute("status", 403);
        return "error/403";
    }

    @ExceptionHandler(PaymentFailedException.class)
    public String handlePaymentFailed(PaymentFailedException ex, RedirectAttributes redirectAttributes) {
        log.warn("Payment checkout failed: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/payments/" + ex.getPaymentId() + "/checkout";
    }

    @ExceptionHandler(LeaseOverlapException.class)
    public String handleLeaseOverlap(LeaseOverlapException ex, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        log.warn("Lease overlap error: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        String referer = request.getHeader("Referer");
        return (referer != null && referer.contains("/leasing")) ? "redirect:" + referer : "redirect:/leasing";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgument(IllegalArgumentException ex, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        log.warn("Invalid input argument: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/dashboard";
    }

    @ExceptionHandler(TenantDeletionBlockedException.class)
    public String handleTenantDeletionBlocked(TenantDeletionBlockedException ex, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        log.warn("Blocked tenant deletion: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        String referer = request.getHeader("Referer");
        return (referer != null && referer.contains("/tenants")) ? "redirect:" + referer : "redirect:/tenants";
    }

    @ExceptionHandler(PropertyDeletionBlockedException.class)
    public String handlePropertyDeletionBlocked(PropertyDeletionBlockedException ex, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        log.warn("Blocked deletion: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/properties";
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxSizeException(MaxUploadSizeExceededException ex, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        log.warn("Max upload size exceeded: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", "Uploaded file exceeds the maximum permitted size of 5 MB.");
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/properties";
    }

    @ExceptionHandler(FileStorageException.class)
    public String handleFileStorageException(FileStorageException ex, RedirectAttributes redirectAttributes, HttpServletRequest request) {
        log.error("File storage error: {}", ex.getMessage());
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        String referer = request.getHeader("Referer");
        return referer != null ? "redirect:" + referer : "redirect:/properties";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneralException(Exception ex, Model model, HttpServletRequest request) {
        log.error("Unhandled 500 Internal Server Error at {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        model.addAttribute("errorMessage", ex.getMessage() != null ? ex.getMessage() : "An unexpected server error occurred.");
        model.addAttribute("requestedUri", request.getRequestURI());
        model.addAttribute("status", 500);
        return "error/500";
    }
}
