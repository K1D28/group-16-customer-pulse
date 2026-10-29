package com.customerpulse.web;

import com.customerpulse.service.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;

/** A broken business rule goes back to the previous page with the message, instead of an error page. */
@ControllerAdvice
public class BusinessErrorAdvice {

    @ExceptionHandler(BusinessException.class)
    public String handle(BusinessException e, HttpServletRequest request, RedirectAttributes flash) {
        flash.addFlashAttribute("error", e.getMessage());
        String back = "/dashboard";
        String referer = request.getHeader("Referer");
        if (referer != null) {
            try {
                URI uri = URI.create(referer);
                if (uri.getPath() != null && uri.getPath().startsWith("/") && !uri.getPath().startsWith("//")) {
                    back = uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : "");
                }
            } catch (IllegalArgumentException ignored) {
                // bad header: fall back to the dashboard
            }
        }
        return "redirect:" + back;
    }
}
