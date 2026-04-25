package com.loremind.infrastructure.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuration Spring Security.
 * <p>
 * Strategie : HTTP Basic sur /api/settings/** uniquement (muter la config
 * = action sensible qui autorise SSRF + vol de cle LLM si non protegee).
 * Le reste de /api/** reste permitAll — l'app est self-hosted mono-utilisateur,
 * l'UI elle-meme n'est pas authentifiee (trust model : reseau local ou
 * reverse-proxy front amont).
 * <p>
 * Fail-closed : refuse de demarrer si admin.password n'est pas defini.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${admin.username}") String username,
            @Value("${admin.password}") String password,
            PasswordEncoder encoder) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                "ADMIN_PASSWORD must be set in environment — refusing to start "
                + "with empty admin credentials. Set ADMIN_PASSWORD in .env "
                + "before launching.");
        }
        UserDetails admin = User.builder()
                .username(username)
                .password(encoder.encode(password))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(admin);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // API stateless : pas de session, pas de cookie => CSRF sans objet.
            // Les credentials HTTP Basic sont envoyes a chaque requete, donc
            // immunise contre CSRF (le browser ne les attache pas cross-origin).
            .cors(cors -> {}) // delegue au CorsFilter bean (CorsConfig)
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Preflight CORS toujours libre (le browser n'envoie pas Authorization sur OPTIONS)
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/settings/**").hasRole("ADMIN")
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .httpBasic(basic -> {});
        return http.build();
    }
}
