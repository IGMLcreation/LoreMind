package com.loremind.infrastructure.web;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Intercepteur global d'exceptions pour TOUS les @RestController.
 *
 * <p>Role :
 * <ul>
 *   <li>Logger systematiquement les exceptions non gerees (avec stack trace + path)
 *       — evite d'avoir a creuser dans les logs Docker apres coup.</li>
 *   <li>Renvoyer un JSON propre au client (`{error, type, ...}`) au lieu du 500 nu
 *       par defaut de Spring — utile pour debug cote frontend (visible directement
 *       dans la DevTools reseau).</li>
 *   <li>Mapper les exceptions courantes vers des status HTTP appropries
 *       (IllegalArgumentException -> 400, EntityNotFoundException -> 404).</li>
 * </ul>
 *
 * <p>Important : ne court-circuite PAS les try/catch locaux des controllers
 * (ex: LicenseController.install catche InstallException -> 400 lui-meme).
 * Ce handler n'attrape QUE ce qui a echappe au catch local.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Violation d'invariant domaine (doublons, valeurs invalides, etc.) -> 400.
     * Concentre ici la logique qui etait dupliquee dans GameSystemController.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", safeMessage(ex)));
    }

    /** Entite JPA introuvable -> 404. */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", safeMessage(ex)));
    }

    /** JSON malforme dans le body de la requete -> 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Malformed request body"));
    }

    /** Validation @Valid echouee -> 400 avec liste des erreurs par champ. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                fields.put(e.getField(), e.getDefaultMessage() != null ? e.getDefaultMessage() : "invalid"));
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "fields", fields
        ));
    }

    /**
     * Statut HTTP explicitement choisi par un controller via {@link ResponseStatusException}
     * (ex: {@code NotebookController} -> 404 si notebook introuvable, 502 si Brain injoignable).
     * <p>
     * SANS ce handler, le fallback {@code @ExceptionHandler(Throwable.class)} ci-dessous
     * interceptait ces exceptions et renvoyait 500 — ecrasant le statut voulu (le
     * resolver natif de Spring est court-circuite des qu'un advice gere Throwable).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        String reason = ex.getReason();
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("error", reason != null ? reason : ex.getStatusCode().toString()));
    }

    /**
     * Client HTTP parti pendant une reponse asynchrone (SSE) : le navigateur a ferme
     * la connexion (onglet ferme, proxy coupe...), la reponse n'est plus utilisable.
     * Ce n'est PAS une erreur serveur -> pas de log ERROR + stack trace (bruit),
     * et aucune reponse a renvoyer (le canal est mort).
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnected(HttpServletRequest request, AsyncRequestNotUsableException ex) {
        log.debug("Client deconnecte pendant la reponse asynchrone sur {} {} : {}",
                request.getMethod(), request.getRequestURI(), ex.getMessage());
    }

    /**
     * Fallback : tout ce qui n'a pas ete catche au-dessus -> 500, mais avec
     * un log ERROR explicite (path + stack trace) et un body JSON debuggable
     * cote client. C'est LE filet de securite.
     *
     * Note : on attrape Throwable (pas Exception) pour aussi capturer les
     * Error (NoClassDefFoundError, OutOfMemoryError... — cf. incident Tink).
     * On NE swallow PAS — on log AVANT de renvoyer une reponse.
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(HttpServletRequest request, Throwable ex) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", "Internal server error");
        body.put("type", ex.getClass().getSimpleName());
        String msg = safeMessage(ex);
        if (!msg.isEmpty()) body.put("message", msg);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /** Evite les NPE quand getMessage() est null sur certaines exceptions. */
    private static String safeMessage(Throwable ex) {
        return ex.getMessage() != null ? ex.getMessage() : "";
    }
}
