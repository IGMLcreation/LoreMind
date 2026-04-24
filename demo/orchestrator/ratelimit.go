package main

import (
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
)

// rateLimiter autorise au plus une action par IP dans une fenetre glissante.
// Pas de token bucket : pour un endpoint de creation de session, "1 par
// fenetre" est largement suffisant et plus simple a raisonner.
type rateLimiter struct {
	mu       sync.Mutex
	lastSeen map[string]time.Time
	window   time.Duration
}

func newRateLimiter(window time.Duration) *rateLimiter {
	rl := &rateLimiter{
		lastSeen: make(map[string]time.Time),
		window:   window,
	}
	go rl.cleanupLoop()
	return rl
}

// Allow renvoie true si l'IP n'a pas deja declenche d'action dans la fenetre.
// Sur true, l'horloge de l'IP est reinitialisee.
func (rl *rateLimiter) Allow(ip string) bool {
	rl.mu.Lock()
	defer rl.mu.Unlock()
	now := time.Now()
	if last, ok := rl.lastSeen[ip]; ok && now.Sub(last) < rl.window {
		return false
	}
	rl.lastSeen[ip] = now
	return true
}

// cleanupLoop purge les entrees plus anciennes que 2x la fenetre pour eviter
// la croissance non bornee de la map sous trafic varie.
func (rl *rateLimiter) cleanupLoop() {
	ticker := time.NewTicker(5 * time.Minute)
	defer ticker.Stop()
	for range ticker.C {
		cutoff := time.Now().Add(-2 * rl.window)
		rl.mu.Lock()
		for ip, t := range rl.lastSeen {
			if t.Before(cutoff) {
				delete(rl.lastSeen, ip)
			}
		}
		rl.mu.Unlock()
	}
}

// clientIP extrait l'IP reelle du visiteur en tenant compte du setup reverse-proxy.
// Ordre de priorite :
//  1. CF-Connecting-IP : defini par Cloudflare sur la base de SA propre vue du
//     peer TCP, non-forgeable par le client, ecrase toute valeur entrante.
//  2. X-Forwarded-For, derniere entree : quand seul Traefik est en front (pas
//     de Cloudflare), Traefik append l'IP qu'il observe. Prendre la premiere
//     serait une faille (header forgeable).
//  3. RemoteAddr : fallback si aucun header de proxy n'est present.
func clientIP(r *http.Request) string {
	if cfIP := r.Header.Get("CF-Connecting-IP"); cfIP != "" {
		return strings.TrimSpace(cfIP)
	}
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		parts := strings.Split(xff, ",")
		return strings.TrimSpace(parts[len(parts)-1])
	}
	if host, _, err := net.SplitHostPort(r.RemoteAddr); err == nil {
		return host
	}
	return r.RemoteAddr
}
