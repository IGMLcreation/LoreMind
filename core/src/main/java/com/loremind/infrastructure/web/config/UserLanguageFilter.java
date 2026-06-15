package com.loremind.infrastructure.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Capture la langue de l'utilisateur (entête {@code X-User-Language} envoyé par le
 * frontend) dans {@link UserLanguageHolder} pour la durée de la requête, puis la
 * nettoie systématiquement.
 * <p>
 * Les clients du Brain liront ce ThreadLocal au moment de construire leur appel
 * (sur ce même thread servlet) pour relayer la langue au Brain. Indispensable de
 * {@code clear()} en {@code finally} : les threads servlet sont recyclés dans un
 * pool, une valeur oubliée fuiterait sur la requête suivante.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserLanguageFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            UserLanguageHolder.set(request.getHeader(UserLanguageHolder.HEADER));
            filterChain.doFilter(request, response);
        } finally {
            UserLanguageHolder.clear();
        }
    }
}
