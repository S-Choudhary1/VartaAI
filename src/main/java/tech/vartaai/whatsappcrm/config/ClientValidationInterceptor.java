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
        
        // Skip auth endpoints, webhooks, and swagger
        if (requestUri.startsWith("/api/v1/auth") || 
            requestUri.startsWith("/api/v1/webhooks") ||
            requestUri.startsWith("/v3/api-docs") ||
            requestUri.startsWith("/swagger-ui")) {
            return true;
        }

        // Check for X-Client-Id header
        String headerClientId = request.getHeader("X-Client-Id");
        if (headerClientId == null) {
            // Also check query param
            headerClientId = request.getParameter("clientId");
        }

        if (headerClientId == null) {
             // For GET requests, maybe it's optional? But user said mandatory.
             // If we enforce it strictly:
             response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing X-Client-Id header or clientId parameter");
             return false;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal) {
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            
            // Allow SuperAdmin to impersonate/access any client?
            if ("SUPER_ADMIN".equals(principal.getRole())) {
                return true;
            }

            if (principal.getClientId() == null) {
                // User has no client? Should not happen for normal users.
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

