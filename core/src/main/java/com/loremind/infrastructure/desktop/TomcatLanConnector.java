package com.loremind.infrastructure.desktop;

import com.loremind.domain.remoteaccess.ports.LanConnector;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.AbstractHttp11Protocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Second connecteur HTTP de Tomcat, ouvert À CHAUD sur toutes les interfaces quand le MJ
 * active l'accès distant (mode bureau uniquement).
 * <p>
 * Pourquoi un second connecteur plutôt que d'ouvrir le principal : le connecteur habituel
 * reste sur 127.0.0.1 (aucun changement de comportement, pas d'alerte du pare-feu Windows
 * chez qui n'active rien), et le port d'arrivée suffit à distinguer une requête locale d'une
 * requête venue du réseau (cf. {@code RemoteAccessFilter}). Ouverture et fermeture sans
 * redémarrer l'application.
 */
@Component
@Profile("local")
public class TomcatLanConnector implements LanConnector {

    private static final Logger log = LoggerFactory.getLogger(TomcatLanConnector.class);

    /** Interfaces à ignorer pour composer l'URL : réseaux virtuels (VM, WSL, Docker, VPN…). */
    private static final List<String> VIRTUAL_HINTS = List.of(
            "virtual", "vmware", "vbox", "hyper-v", "vethernet", "docker", "wsl", "loopback", "tap-", "tun");

    private final ApplicationContext context;
    private Connector connector;

    public TomcatLanConnector(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public synchronized void start(int port) {
        if (connector != null && connector.getPort() == port) return;
        stop();
        ensurePortFree(port);

        Tomcat tomcat = tomcat();
        Connector main = tomcat.getConnector();
        Connector lan = new Connector(main.getProtocolHandlerClassName());
        lan.setPort(port);
        lan.setProperty("address", "0.0.0.0");
        // Mêmes limites d'upload que le connecteur principal (imports PDF volumineux).
        lan.setMaxPostSize(main.getMaxPostSize());
        if (main.getProtocolHandler() instanceof AbstractHttp11Protocol<?> mainProtocol
                && lan.getProtocolHandler() instanceof AbstractHttp11Protocol<?> lanProtocol) {
            lanProtocol.setMaxSwallowSize(mainProtocol.getMaxSwallowSize());
        }
        try {
            tomcat.getService().addConnector(lan); // démarre le connecteur : le service tourne déjà
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Impossible d'ouvrir le port " + port + " : " + e.getMessage(), e);
        }
        connector = lan;
        log.info("Accès distant : connecteur réseau ouvert sur le port {}", port);
    }

    @Override
    public synchronized void stop() {
        if (connector == null) return;
        try {
            tomcat().getService().removeConnector(connector);
            connector.destroy();
        } catch (Exception e) {
            log.warn("Fermeture du connecteur réseau incomplète : {}", e.getMessage());
        }
        log.info("Accès distant : connecteur réseau fermé (port {})", connector.getPort());
        connector = null;
    }

    @Override
    public synchronized int runningPort() {
        return connector != null ? connector.getPort() : -1;
    }

    @Override
    public List<String> lanAddresses() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                    .filter(TomcatLanConnector::isPhysicalAndUp)
                    .flatMap(nic -> Collections.list(nic.getInetAddresses()).stream())
                    .filter(a -> a instanceof Inet4Address && a.isSiteLocalAddress())
                    .map(a -> a.getHostAddress())
                    .distinct()
                    // Box domestique d'abord (192.168.x), puis 10.x / 172.16-31.x.
                    .sorted(Comparator.comparing((String ip) -> !ip.startsWith("192.168.")).thenComparing(ip -> ip))
                    .toList();
        } catch (SocketException e) {
            log.warn("Lecture des interfaces réseau impossible : {}", e.getMessage());
            return List.of();
        }
    }

    private static boolean isPhysicalAndUp(NetworkInterface nic) {
        try {
            if (!nic.isUp() || nic.isLoopback() || nic.isVirtual() || nic.isPointToPoint()) return false;
        } catch (SocketException e) {
            return false;
        }
        String label = (nic.getName() + " " + nic.getDisplayName()).toLowerCase(Locale.ROOT);
        return VIRTUAL_HINTS.stream().noneMatch(label::contains);
    }

    /** Vérification préalable pour un message clair (sinon Tomcat ne log qu'une trace obscure). */
    private static void ensurePortFree(int port) {
        try (ServerSocket probe = new ServerSocket()) {
            probe.setReuseAddress(false);
            probe.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            throw new IllegalStateException("Le port " + port + " est déjà utilisé par une autre application.", e);
        }
    }

    private Tomcat tomcat() {
        if (context instanceof ServletWebServerApplicationContext web) {
            WebServer server = web.getWebServer();
            if (server instanceof TomcatWebServer tomcatServer) {
                return tomcatServer.getTomcat();
            }
        }
        throw new IllegalStateException("Serveur web embarqué indisponible.");
    }
}
