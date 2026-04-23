package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"log"
	"sync"
	"time"
)

// SessionStatus reflete l'etat du cycle de vie d'un trio de session.
type SessionStatus string

const (
	StatusStarting SessionStatus = "starting"
	StatusReady    SessionStatus = "ready"
	StatusFailed   SessionStatus = "failed"
)

// Session represente une demo isolee pour un visiteur.
// CoreHost est le hostname Docker interne du conteneur core de cette session
// (ex: "demo-abc123-core"), vers lequel l'orchestrateur proxifie les /api/*.
type Session struct {
	ID        string
	CreatedAt time.Time
	Status    SessionStatus
	CoreHost  string
	Err       string
	// proxy et proxyOnce : reverse-proxy cache, cree au plus une fois via
	// sync.Once (evite la race entre deux requetes concurrentes sur la meme
	// session). proxy est typee any pour ne pas contraindre sessions.go a
	// importer net/http/httputil.
	proxy     any
	proxyOnce sync.Once
}

// Manager gere le cycle de vie des sessions (creation, acces, cleanup).
// Thread-safe : le mutex protege la map contre les acces concurrents (HTTP
// handlers + goroutine de GC).
type Manager struct {
	mu       sync.Mutex
	sessions map[string]*Session
	docker   *DockerClient
	cfg      *Config
}

func newManager(docker *DockerClient, cfg *Config) *Manager {
	return &Manager{
		sessions: make(map[string]*Session),
		docker:   docker,
		cfg:      cfg,
	}
}

// ErrCapacity est retournee quand MAX_SESSIONS est atteint.
var ErrCapacity = errors.New("demo at capacity")

// Create reserve un slot et lance le spawn des conteneurs en arriere-plan.
// Retourne immediatement avec Status=starting. L'etat bascule a "ready" quand
// les conteneurs sont up et que core repond a /api/config.
func (m *Manager) Create(ctx context.Context) (*Session, error) {
	m.mu.Lock()
	if len(m.sessions) >= m.cfg.MaxSessions {
		m.mu.Unlock()
		return nil, ErrCapacity
	}
	id := newShortID()
	sess := &Session{
		ID:        id,
		CreatedAt: time.Now(),
		Status:    StatusStarting,
		CoreHost:  "demo-" + id + "-core",
	}
	m.sessions[id] = sess
	m.mu.Unlock()

	// Spawn asynchrone : l'utilisateur voit immediatement la page "preparation".
	go func() {
		spawnCtx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
		defer cancel()
		if err := m.docker.SpawnTrio(spawnCtx, id, m.cfg); err != nil {
			log.Printf("session %s spawn failed: %v", id, err)
			m.mu.Lock()
			sess.Status = StatusFailed
			sess.Err = err.Error()
			m.mu.Unlock()
			// Nettoyage best-effort des conteneurs partiellement crees.
			_ = m.docker.KillTrio(context.Background(), id)
			return
		}
		// Attente que core reponde (sinon proxy retourne 502 aux premieres requetes).
		if m.docker.WaitReady(spawnCtx, id, 90*time.Second) {
			m.mu.Lock()
			sess.Status = StatusReady
			m.mu.Unlock()
			log.Printf("session %s ready", id)
		} else {
			log.Printf("session %s never became ready", id)
			m.mu.Lock()
			sess.Status = StatusFailed
			sess.Err = "timeout waiting for core"
			m.mu.Unlock()
			_ = m.docker.KillTrio(context.Background(), id)
		}
	}()

	return sess, nil
}

// Get renvoie la session associee a un ID, ou nil si elle n'existe plus.
func (m *Manager) Get(id string) *Session {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.sessions[id]
}

// RunGC boucle toutes les minutes pour supprimer les sessions expirees.
// A lancer en goroutine au demarrage.
func (m *Manager) RunGC(ctx context.Context) {
	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			m.gcOnce()
		}
	}
}

func (m *Manager) gcOnce() {
	cutoff := time.Now().Add(-m.cfg.SessionTTL)
	m.mu.Lock()
	var expired []string
	for id, s := range m.sessions {
		if s.CreatedAt.Before(cutoff) {
			expired = append(expired, id)
		}
	}
	for _, id := range expired {
		delete(m.sessions, id)
	}
	m.mu.Unlock()

	for _, id := range expired {
		log.Printf("session %s expired, killing containers", id)
		if err := m.docker.KillTrio(context.Background(), id); err != nil {
			log.Printf("kill %s: %v", id, err)
		}
	}
}

// CleanupOrphans tue les conteneurs demo-* qui ne correspondent a aucune
// session en memoire. Appele au demarrage pour gerer un redemarrage brutal.
func (m *Manager) CleanupOrphans(ctx context.Context) {
	ids, err := m.docker.ListSessionIDs(ctx)
	if err != nil {
		log.Printf("list orphans: %v", err)
		return
	}
	for _, id := range ids {
		log.Printf("cleaning orphan session %s", id)
		_ = m.docker.KillTrio(ctx, id)
	}
}

// newShortID genere un identifiant hexadecimal de 32 caracteres (128 bits).
// 128 bits d'entropie rendent les collisions et le brute-force statistiquement
// impossibles, meme si un attaquant pouvait tenter des millions de cookies.
func newShortID() string {
	b := make([]byte, 16)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}
