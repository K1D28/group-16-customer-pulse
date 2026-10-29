package com.customerpulse.web;

import com.customerpulse.domain.AppUser;
import com.customerpulse.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;

/** Someone deactivated while logged in is signed out on their next click. */
@Configuration
public class ActiveUserInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final CurrentUser currentUser;

    public ActiveUserInterceptor(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).excludePathPatterns("/login", "/logout", "/css/**", "/js/**", "/favicon.svg", "/error");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Optional<AppUser> me = currentUser.find();
        if (me.isPresent() && !me.get().isActive()) {
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            response.sendRedirect(request.getContextPath() + "/login?error");
            return false;
        }
        return true;
    }
}
