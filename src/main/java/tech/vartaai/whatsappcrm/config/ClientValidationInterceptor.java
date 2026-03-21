package tech.vartaai.whatsappcrm.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tech.vartaai.whatsappcrm.security.UserPrincipal;

import java.util.UUID;

@Component
public class ClientValidationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestUri = request.getRequestURI();
        
        // Skip auth endpoints, webhooks, admin endpoints, and swagger
        if (requestUri.startsWith("/api/v1/auth") ||
            requestUri.startsWith("/api/v1/webhooks") ||
            requestUri.startsWith("/api/v1/admin") ||
            requestUri.startsWith("/v3/api-docs") ||
            requestUri.startsWith("/swagger-ui")) {
            return true;
        }

        // Allow SuperAdmin to bypass client validation entirely
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            if ("SUPER_ADMIN".equals(principal.getRole())) {
                return true;
            }
        }

        // Check for X-Client-Id header
        String headerClientId = request.getHeader("X-Client-Id");
        if (headerClientId == null) {
            headerClientId = request.getParameter("clientId");
        }

        if (headerClientId == null) {
             response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing X-Client-Id header or clientId parameter");
             return false;
        }

        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

            if (principal.getClientId() == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "User does not belong to any client");
                return false;
            }

            if (!principal.getClientId().toString().equals(headerClientId)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Client ID mismatch");
                return false;
            }
        }
        
        return true;
    }
}

