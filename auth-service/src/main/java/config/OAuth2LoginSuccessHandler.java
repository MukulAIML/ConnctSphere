package com.connectsphere.auth.config;

import com.connectsphere.auth.dto.AuthDto.AuthResponse;
import com.connectsphere.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * OAuth2LoginSuccessHandler — called after a successful GitHub/Google
 * OAuth2 flow. Extracts user attributes, runs the find-or-create flow,
 * and writes an {@link AuthResponse} JSON back to the client.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService    authService;
    private final ObjectMapper   objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest  request,
                                        HttpServletResponse response,
                                        Authentication      authentication) throws IOException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        // Extract common attributes (differs slightly between providers)
        String provider   = resolveProvider(request);
        String providerId = String.valueOf(oauthUser.getAttribute("sub") != null
                ? oauthUser.getAttribute("sub")
                : oauthUser.getAttribute("id"));
        String email      = oauthUser.getAttribute("email");
        String name       = oauthUser.getAttribute("name");
        String avatarUrl  = oauthUser.getAttribute("avatar_url") != null
                ? oauthUser.getAttribute("avatar_url")
                : oauthUser.getAttribute("picture");

        AuthResponse authResponse = authService.handleOAuthLogin(
                provider, providerId, email, name, avatarUrl);

        log.info("OAuth2 login success for {} via {}", email, provider);

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        objectMapper.writeValue(response.getOutputStream(), authResponse);
    }

    private String resolveProvider(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.contains("github")) return "GITHUB";
        if (uri.contains("google")) return "GOOGLE";
        return "OAUTH2";
    }
}
