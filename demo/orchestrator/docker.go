package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/docker/docker/api/types"
	"github.com/docker/docker/api/types/container"
	"github.com/docker/docker/api/types/filters"
	"github.com/docker/docker/api/types/network"
	"github.com/docker/docker/client"
)

// DockerClient encapsule les operations Docker necessaires a la demo.
type DockerClient struct {
	cli *client.Client
}

func newDockerClient() (*DockerClient, error) {
	cli, err := client.NewClientWithOpts(client.FromEnv, client.WithAPIVersionNegotiation())
	if err != nil {
		return nil, fmt.Errorf("docker client: %w", err)
	}
	return &DockerClient{cli: cli}, nil
}

// SpawnTrio cree les 3 conteneurs d'une session (postgres, brain, core) et
// les branche sur le reseau interne. Ils partagent un label "demo-session=<id>"
// pour faciliter le nettoyage.
func (d *DockerClient) SpawnTrio(ctx context.Context, sessionID string, cfg *Config) error {
	pgName := "demo-" + sessionID + "-postgres"
	brainName := "demo-" + sessionID + "-brain"
	coreName := "demo-" + sessionID + "-core"
	pgPassword := randomHex(16)
	brainSecret := randomHex(32)
	adminPassword := randomHex(16)

	labels := map[string]string{
		"demo-session": sessionID,
		"demo-role":    "", // rempli par conteneur
	}

	// --- Postgres (tmpfs => ephemere) ---
	pgLabels := copyLabels(labels, "postgres")
	if err := d.runContainer(ctx, runSpec{
		Name:   pgName,
		Image:  "postgres:16-alpine",
		Env:    []string{"POSTGRES_DB=loremind", "POSTGRES_USER=loremind", "POSTGRES_PASSWORD=" + pgPassword},
		Labels: pgLabels,
		Memory: cfg.PostgresMemoryBytes,
		Tmpfs:  map[string]string{"/var/lib/postgresql/data": "rw,size=200m"},
		Net:    cfg.SessionsNetwork,
		Alias:  pgName,
	}); err != nil {
		return fmt.Errorf("spawn postgres: %w", err)
	}

	// --- Brain ---
	brainLabels := copyLabels(labels, "brain")
	if err := d.runContainer(ctx, runSpec{
		Name:  brainName,
		Image: cfg.Registry + "/ietm64/brain:" + cfg.Tag,
		Env: []string{
			"INTERNAL_SHARED_SECRET=" + brainSecret,
			// Pas de provider LLM configure en demo : les features IA repondront
			// en erreur, la demo sert principalement a explorer l'edition.
			"LLM_PROVIDER=ollama",
			"OLLAMA_BASE_URL=http://localhost:1", // endpoint mort volontairement
		},
		Labels: brainLabels,
		Memory: cfg.BrainMemoryBytes,
		Net:    cfg.SessionsNetwork,
		Alias:  brainName,
	}); err != nil {
		return fmt.Errorf("spawn brain: %w", err)
	}

	// --- Core ---
	coreLabels := copyLabels(labels, "core")
	if err := d.runContainer(ctx, runSpec{
		Name:  coreName,
		Image: cfg.Registry + "/ietm64/core:" + cfg.Tag,
		Env: []string{
			"SPRING_DATASOURCE_URL=jdbc:postgresql://" + pgName + ":5432/loremind",
			"SPRING_DATASOURCE_USERNAME=loremind",
			"SPRING_DATASOURCE_PASSWORD=" + pgPassword,
			"BRAIN_BASE_URL=http://" + brainName + ":8000",
			"BRAIN_INTERNAL_SECRET=" + brainSecret,
			"ADMIN_USERNAME=admin",
			"ADMIN_PASSWORD=" + adminPassword,
			"DEMO_MODE=true",
			"CORS_ALLOWED_ORIGINS=*",
			// MinIO volontairement non fourni : le client init en lazy, seul
			// l'upload d'images echouera (500). A masquer plus tard si besoin.
		},
		Labels: coreLabels,
		Memory: cfg.CoreMemoryBytes,
		Net:    cfg.SessionsNetwork,
		Alias:  coreName,
	}); err != nil {
		return fmt.Errorf("spawn core: %w", err)
	}

	return nil
}

// runSpec regroupe les parametres d'un container pour runContainer.
type runSpec struct {
	Name   string
	Image  string
	Env    []string
	Labels map[string]string
	Memory int64
	Tmpfs  map[string]string
	Net    string
	Alias  string
}

// runContainer pull l'image si absente puis cree et demarre le conteneur.
func (d *DockerClient) runContainer(ctx context.Context, s runSpec) error {
	// Pull silencieux si image absente localement. Ignore les erreurs (l'image
	// peut exister localement sans etre atteignable au registre, ex: builds dev).
	if reader, err := d.cli.ImagePull(ctx, s.Image, types.ImagePullOptions{}); err == nil {
		// Drain le body, sinon le pull n'est pas termine quand on continue.
		_, _ = io.Copy(io.Discard, reader)
		reader.Close()
	}

	pidsLimit := int64(200)
	hostCfg := &container.HostConfig{
		RestartPolicy: container.RestartPolicy{Name: "no"},
		Resources: container.Resources{
			Memory:    s.Memory,
			NanoCPUs:  1_000_000_000, // 1 vCPU par conteneur
			PidsLimit: &pidsLimit,    // Anti fork-bomb : max 200 threads/processus.
		},
		Tmpfs: s.Tmpfs,
		// no-new-privileges : interdit a un processus du conteneur de gagner
		// plus de privileges que son parent (bloque les exploits setuid courants).
		SecurityOpt: []string{"no-new-privileges:true"},
		// CapDrop/CapAdd volontairement non configures : les images core (JVM
		// Spring Boot) et brain (Python) n'ont pas ete auditees pour un
		// fonctionnement avec capabilities restreintes ; un drop trop agressif
		// peut casser le demarrage de maniere non triviale. A revoir si besoin.
	}

	netCfg := &network.NetworkingConfig{
		EndpointsConfig: map[string]*network.EndpointSettings{
			s.Net: {Aliases: []string{s.Alias}},
		},
	}

	resp, err := d.cli.ContainerCreate(ctx, &container.Config{
		Image:  s.Image,
		Env:    s.Env,
		Labels: s.Labels,
	}, hostCfg, netCfg, nil, s.Name)
	if err != nil {
		return fmt.Errorf("create %s: %w", s.Name, err)
	}
	if err := d.cli.ContainerStart(ctx, resp.ID, container.StartOptions{}); err != nil {
		return fmt.Errorf("start %s: %w", s.Name, err)
	}
	return nil
}

// WaitReady poll l'endpoint /api/config du core pendant timeout, retourne true
// des qu'il repond 200. Utilise par le manager pour passer en Status=ready.
func (d *DockerClient) WaitReady(ctx context.Context, sessionID string, timeout time.Duration) bool {
	deadline := time.Now().Add(timeout)
	url := "http://demo-" + sessionID + "-core:8080/api/config"
	httpClient := &http.Client{Timeout: 2 * time.Second}
	for time.Now().Before(deadline) {
		resp, err := httpClient.Get(url)
		if err == nil {
			resp.Body.Close()
			if resp.StatusCode == 200 {
				return true
			}
		}
		select {
		case <-ctx.Done():
			return false
		case <-time.After(2 * time.Second):
		}
	}
	return false
}

// KillTrio arrete et supprime tous les conteneurs avec le label demo-session=<id>.
func (d *DockerClient) KillTrio(ctx context.Context, sessionID string) error {
	f := filters.NewArgs()
	f.Add("label", "demo-session="+sessionID)
	list, err := d.cli.ContainerList(ctx, container.ListOptions{All: true, Filters: f})
	if err != nil {
		return err
	}
	for _, c := range list {
		_ = d.cli.ContainerRemove(ctx, c.ID, container.RemoveOptions{Force: true})
	}
	return nil
}

// ListSessionIDs retourne les IDs de session detectes dans les labels Docker.
// Utile au demarrage pour nettoyer les orphelins (conteneurs d'une vie
// anterieure de l'orchestrateur).
func (d *DockerClient) ListSessionIDs(ctx context.Context) ([]string, error) {
	f := filters.NewArgs()
	f.Add("label", "demo-session")
	list, err := d.cli.ContainerList(ctx, container.ListOptions{All: true, Filters: f})
	if err != nil {
		return nil, err
	}
	seen := make(map[string]bool)
	for _, c := range list {
		if v, ok := c.Labels["demo-session"]; ok && v != "" {
			seen[v] = true
		}
	}
	out := make([]string, 0, len(seen))
	for id := range seen {
		out = append(out, id)
	}
	return out, nil
}

// --- helpers ---

func copyLabels(base map[string]string, role string) map[string]string {
	out := make(map[string]string, len(base))
	for k, v := range base {
		out[k] = v
	}
	out["demo-role"] = role
	return out
}

func randomHex(n int) string {
	b := make([]byte, n)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}
