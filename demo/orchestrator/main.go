package main

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

const cookieName = "loremind-demo-session"

func main() {
	cfg := loadConfig()

	docker, err := newDockerClient()
	if err != nil {
		log.Fatalf("docker init: %v", err)
	}

	mgr := newManager(docker, cfg)
	limiter := newRateLimiter(cfg.RateLimitWindow)

	// Nettoyage des sessions residuelles au boot (redemarrage orchestrateur).
	cleanCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	mgr.CleanupOrphans(cleanCtx)
	cancel()

	go mgr.RunGC(context.Background())

	mux := http.NewServeMux()
	mux.HandleFunc("/_demo/ready", readyHandler(mgr))
	mux.HandleFunc("/api/", apiHandler(mgr, cfg))
	mux.HandleFunc("/", rootHandler(mgr, limiter, cfg))

	srv := &http.Server{
		Addr:    ":80",
		Handler: mux,
		// Timeouts anti-slowloris. WriteTimeout laisse de la marge pour le
		// streaming SSE (ai/chat/stream) qui peut durer plusieurs minutes.
		ReadHeaderTimeout: 10 * time.Second,
		ReadTimeout:       60 * time.Second,
		WriteTimeout:      10 * time.Minute,
		IdleTimeout:       120 * time.Second,
		// Headers max : 1 Mo (defaut Go), suffisant.
	}

	log.Printf("orchestrator listening on :80 (max sessions=%d, ttl=%s, rate window=%s)",
		cfg.MaxSessions, cfg.SessionTTL, cfg.RateLimitWindow)
	if err := srv.ListenAndServe(); err != nil {
		log.Fatalf("http server: %v", err)
	}
}

// rootHandler gere toutes les routes non-API : sert l'Angular statique si le
// visiteur a deja une session prete, sinon cree une session (sous rate limit)
// et renvoie la page de preparation.
func rootHandler(mgr *Manager, limiter *rateLimiter, cfg *Config) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		sess := currentSession(r, mgr)

		// Visiteur connu et session prete -> sert l'app normalement.
		if sess != nil && sess.Status == StatusReady {
			serveStatic(w, r, cfg.StaticDir)
			return
		}

		// On ne spawn qu'a la navigation initiale (GET d'un document HTML).
		// Les assets secondaires (JS/CSS/favicon) ne doivent pas declencher
		// de nouvelle session.
		if r.Method != http.MethodGet {
			http.Error(w, "No session", http.StatusUnauthorized)
			return
		}
		if !acceptsHTML(r) {
			http.Error(w, "No session", http.StatusUnauthorized)
			return
		}

		// Session inexistante (ou expiree) -> en creer une, sous rate limit.
		if sess == nil {
			ip := clientIP(r)
			if !limiter.Allow(ip) {
				http.Error(w, "Trop de tentatives. Merci d'attendre "+
					strconv.Itoa(int(cfg.RateLimitWindow.Seconds()))+"s.",
					http.StatusTooManyRequests)
				return
			}
			newSess, err := mgr.Create(r.Context())
			if err != nil {
				if errors.Is(err, ErrCapacity) {
					http.Error(w, "La demo est pleine (max "+
						strconv.Itoa(cfg.MaxSessions)+
						" sessions simultanees). Merci de reessayer plus tard.",
						http.StatusServiceUnavailable)
					return
				}
				http.Error(w, "Impossible de creer la session : "+err.Error(),
					http.StatusInternalServerError)
				return
			}
			sess = newSess
			setCookie(w, sess.ID, cfg.SessionTTL)
		}

		servePreparingPage(w, cfg.PreparingPage)
	}
}

// apiHandler proxifie /api/* vers le core de la session.
// Bride la taille des bodies a MaxBodyBytes pour limiter les DoS memoire.
func apiHandler(mgr *Manager, cfg *Config) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		sess := currentSession(r, mgr)
		if sess == nil {
			http.Error(w, "No session", http.StatusUnauthorized)
			return
		}
		if sess.Status != StatusReady {
			http.Error(w, "Session not ready", http.StatusServiceUnavailable)
			return
		}
		if r.Body != nil {
			r.Body = http.MaxBytesReader(w, r.Body, cfg.MaxBodyBytes)
		}
		proxy := sessionProxy(sess)
		proxy.ServeHTTP(w, r)
	}
}

// sessionProxy renvoie (et cree si besoin) un reverse proxy cache dans la
// session via sync.Once : garantit une seule creation meme sous requetes
// concurrentes, sans mutex explicite.
func sessionProxy(sess *Session) *httputil.ReverseProxy {
	sess.proxyOnce.Do(func() {
		target, _ := url.Parse("http://" + sess.CoreHost + ":8080")
		p := httputil.NewSingleHostReverseProxy(target)
		p.ErrorHandler = func(w http.ResponseWriter, r *http.Request, err error) {
			log.Printf("proxy error session=%s: %v", sess.ID, err)
			http.Error(w, "Upstream error", http.StatusBadGateway)
		}
		sess.proxy = p
	})
	return sess.proxy.(*httputil.ReverseProxy)
}

// readyHandler renvoie l'etat de la session en JSON pour le polling client.
// N'expose aucun ID de session ni d'information sur les autres sessions.
func readyHandler(mgr *Manager) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		sess := currentSession(r, mgr)
		w.Header().Set("Content-Type", "application/json")
		if sess == nil {
			json.NewEncoder(w).Encode(map[string]any{"status": "none"})
			return
		}
		json.NewEncoder(w).Encode(map[string]any{
			"status": string(sess.Status),
			"error":  sess.Err,
		})
	}
}

// currentSession lit le cookie et retrouve la session en memoire.
// Si le cookie pointe vers une session disparue (redemarrage orchestrateur ou
// TTL expire), retourne nil -> le handler traitera comme un nouveau visiteur.
func currentSession(r *http.Request, mgr *Manager) *Session {
	c, err := r.Cookie(cookieName)
	if err != nil || c.Value == "" {
		return nil
	}
	return mgr.Get(c.Value)
}

func setCookie(w http.ResponseWriter, id string, ttl time.Duration) {
	http.SetCookie(w, &http.Cookie{
		Name:     cookieName,
		Value:    id,
		Path:     "/",
		HttpOnly: true,
		Secure:   true, // Traefik termine le TLS ; le browser ne doit envoyer ce cookie qu'en HTTPS.
		SameSite: http.SameSiteLaxMode,
		MaxAge:   int(ttl.Seconds()),
	})
}

// serveStatic sert les fichiers de l'Angular build avec fallback sur index.html
// pour que le routeur cote client fonctionne (SPA).
// Le check HasPrefix apres Join + Clean empeche les path traversals (..).
func serveStatic(w http.ResponseWriter, r *http.Request, dir string) {
	reqPath := r.URL.Path
	if reqPath == "/" || reqPath == "" {
		reqPath = "/index.html"
	}
	fullPath := filepath.Join(dir, filepath.Clean(reqPath))
	if !strings.HasPrefix(fullPath, dir) {
		http.Error(w, "bad path", http.StatusBadRequest)
		return
	}
	if info, err := os.Stat(fullPath); err == nil && !info.IsDir() {
		http.ServeFile(w, r, fullPath)
		return
	}
	http.ServeFile(w, r, filepath.Join(dir, "index.html"))
}

// servePreparingPage sert la page de chargement statique. Le cookie vient
// d'etre pose, le JS de la page utilisera sessionId implicitement via le
// cookie pour poller /_demo/ready.
func servePreparingPage(w http.ResponseWriter, path string) {
	data, err := os.ReadFile(path)
	if err != nil {
		http.Error(w, "Preparing page not found", http.StatusInternalServerError)
		return
	}
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(data)
}

func acceptsHTML(r *http.Request) bool {
	accept := r.Header.Get("Accept")
	return strings.Contains(accept, "text/html") || accept == "" || accept == "*/*"
}
