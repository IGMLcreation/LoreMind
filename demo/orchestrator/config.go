package main

import (
	"log"
	"os"
	"strconv"
	"time"
)

// Config centralise les parametres lus depuis les variables d'env au boot.
type Config struct {
	Registry           string
	Tag                string
	MaxSessions        int
	SessionTTL         time.Duration
	CoreMemoryBytes    int64
	BrainMemoryBytes   int64
	PostgresMemoryBytes int64
	SessionsNetwork    string
	BrainSecretDefault string
	StaticDir          string
	PreparingPage      string
	RateLimitWindow    time.Duration
	MaxBodyBytes       int64
	DemoHost           string
}

func loadConfig() *Config {
	return &Config{
		Registry:           envStr("REGISTRY", "git.igmlcreation.fr"),
		Tag:                envStr("TAG", "latest"),
		MaxSessions:        envInt("MAX_SESSIONS", 10),
		SessionTTL:         time.Duration(envInt("SESSION_TTL_MINUTES", 20)) * time.Minute,
		CoreMemoryBytes:    int64(envInt("CORE_MEMORY_MB", 700)) * 1024 * 1024,
		BrainMemoryBytes:   int64(envInt("BRAIN_MEMORY_MB", 300)) * 1024 * 1024,
		PostgresMemoryBytes: int64(envInt("POSTGRES_MEMORY_MB", 200)) * 1024 * 1024,
		SessionsNetwork:    envStr("SESSIONS_NETWORK", "loremind-demo-sessions"),
		BrainSecretDefault: envStr("BRAIN_INTERNAL_SECRET_DEFAULT", "change-me"),
		StaticDir:          envStr("STATIC_DIR", "/app/static"),
		PreparingPage:      envStr("PREPARING_PAGE", "/app/preparing.html"),
		RateLimitWindow:    time.Duration(envInt("RATE_LIMIT_WINDOW_SECONDS", 60)) * time.Second,
		// 10 Mo : aligne avec la limite d'upload d'image cote core.
		MaxBodyBytes: int64(envInt("MAX_BODY_MB", 10)) * 1024 * 1024,
		// Utilise pour injecter APP_CORS_ALLOWED_ORIGINS dans les cores spawnes :
		// sans ca, Spring bloque les POST avec 403 (origine rejetee).
		DemoHost: envStr("DEMO_HOST", "loremind-demo.igmlcreation.fr"),
	}
}

func envStr(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func envInt(key string, def int) int {
	v := os.Getenv(key)
	if v == "" {
		return def
	}
	i, err := strconv.Atoi(v)
	if err != nil {
		log.Printf("warning: env %s=%q not a number, using default %d", key, v, def)
		return def
	}
	return i
}
