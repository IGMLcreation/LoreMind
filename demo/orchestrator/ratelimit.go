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

// clientIP extrait l'IP reelle en prenant la derniere entree de X-Forwarded-For.
// Justification : Traefik APPEND l'IP du peer au header existant, donc la
// derniere valeur est celle que Traefik a observe directement (le vrai client).
// Prendre la premiere serait une faille : un attaquant peut preremplir le header.
func clientIP(r *http.Request) string {
	if xff := r.Header.Get("X-Forwarded-For"); xff != "" {
		parts := strings.Split(xff, ",")
		return strings.TrimSpace(parts[len(parts)-1])
	}
	if host, _, err := net.SplitHostPort(r.RemoteAddr); err == nil {
		return host
	}
	return r.RemoteAddr
}
