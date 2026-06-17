package com.loremind.infrastructure.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Service du front Angular en statique par le Core, en mode local-first.
 * <p>
 * En deploiement Docker, le front est servi par un conteneur nginx dedie
 * (service {@code web}) et le Core reste une API pure. En mode local
 * (application de bureau empaquetee), il n'y a pas de nginx : le build Angular
 * est copie dans {@code classpath:/static/} (cf. profil Maven de packaging) et
 * le Core le sert lui-meme sur la meme origine que l'API.
 * <p>
 * Active uniquement sous le profil {@code local} pour ne RIEN changer au
 * comportement du conteneur Core en production.
 * <p>
 * Fallback SPA : toute route qui ne correspond pas a un fichier statique reel
 * (et qui n'est pas une route d'API) renvoie {@code index.html}, afin que le
 * routing cote Angular (deep links, rechargement de page) fonctionne.
 */
@Configuration
@Profile("local")
public class LocalWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Fichiers de traduction (assets/i18n/*.json) : nom STABLE (non hashe),
        // donc jamais mis en cache, sinon le navigateur ressert un JSON perime
        // (voire un 304 sur un corps en cache obsolete) apres une mise a jour.
        // Symptome observe : les libelles restent en cles brutes pour une langue.
        // Motif plus specifique que "/**" => prioritaire pour ces chemins.
        registry.addResourceHandler("/assets/i18n/**")
                .addResourceLocations("classpath:/static/assets/i18n/")
                .setCacheControl(CacheControl.noStore());

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Ne JAMAIS rabattre les routes techniques sur index.html :
                        // une API inexistante doit rester un 404, pas du HTML.
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        // Un ASSET manquant (chemin avec extension : .json, .js, .css,
                        // .png...) doit renvoyer 404 — surtout PAS index.html. Sinon un
                        // loader JSON (ex. ngx-translate chargeant assets/i18n/fr.json)
                        // recevrait du HTML en 200 et echouerait au parse, donnant des
                        // cles brutes a l'ecran. On ne rabat sur la coquille SPA que les
                        // vraies routes applicatives (sans extension de fichier).
                        if (hasFileExtension(resourcePath)) {
                            return null;
                        }
                        // Route applicative Angular -> on sert la coquille SPA.
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }

    /**
     * Vrai si le dernier segment du chemin contient un point (donc une extension
     * de fichier : {@code assets/i18n/fr.json}, {@code main.js}...). Les routes
     * applicatives Angular ({@code settings}, {@code campaigns/42}) n'en ont pas.
     */
    private static boolean hasFileExtension(String resourcePath) {
        int lastSlash = resourcePath.lastIndexOf('/');
        String lastSegment = resourcePath.substring(lastSlash + 1);
        return lastSegment.contains(".");
    }
}
