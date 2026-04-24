package main

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"strings"
	"time"
)

// DockerClient parle a l'API Engine Docker en HTTP brut via le dockerproxy.
// Pas de SDK externe : evite les conflits de versions transitives qui
// rendaient github.com/docker/docker v27/v28 ininstallable proprement.
//
// L'API Engine v1.43 est exposee par Docker Engine 24+ (et le dockerproxy
// la supporte sans config supplementaire).
type DockerClient struct {
	baseURL string
	http    *http.Client
}

func newDockerClient() (*DockerClient, error) {
	base := os.Getenv("DOCKER_HOST")
	if base == "" {
		return nil, fmt.Errorf("DOCKER_HOST non defini (attendu : tcp://dockerproxy:2375)")
	}
	// tcp://host:port -> http://host:port (le dockerproxy parle HTTP en clair).
	base = strings.Replace(base, "tcp://", "http://", 1)
	return &DockerClient{
		baseURL: strings.TrimRight(base, "/") + "/v1.43",
		http:    &http.Client{Timeout: 60 * time.Second},
	}, nil
}

// --- Types serialises vers l'API Engine ---

type containerSpec struct {
	Image            string            `json:"Image"`
	Env              []string          `json:"Env,omitempty"`
	Labels           map[string]string `json:"Labels,omitempty"`
	HostConfig       hostConfig        `json:"HostConfig"`
	NetworkingConfig networkingConfig  `json:"NetworkingConfig"`
}

type hostConfig struct {
	Memory        int64             `json:"Memory,omitempty"`
	NanoCPUs      int64             `json:"NanoCPUs,omitempty"`
	PidsLimit     int64             `json:"PidsLimit,omitempty"`
	Tmpfs         map[string]string `json:"Tmpfs,omitempty"`
	SecurityOpt   []string          `json:"SecurityOpt,omitempty"`
	RestartPolicy restartPolicy     `json:"RestartPolicy"`
}

type restartPolicy struct {
	Name string `json:"Name"`
}

type networkingConfig struct {
	EndpointsConfig map[string]endpointSettings `json:"EndpointsConfig,omitempty"`
}

type endpointSettings struct {
	Aliases []string `json:"Aliases,omitempty"`
}

// runSpec : forme intermediate cote orchestrateur, mappee sur containerSpec
// au moment d'envoyer la requete.
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

// --- Operations de haut niveau ---

// SpawnTrio cree postgres + brain + core pour une session.
func (d *DockerClient) SpawnTrio(ctx context.Context, sessionID string, cfg *Config) error {
	pgName := "demo-" + sessionID + "-postgres"
	brainName := "demo-" + sessionID + "-brain"
	coreName := "demo-" + sessionID + "-core"
	pgPassword := randomHex(16)
	brainSecret := randomHex(32)
	adminPassword := randomHex(16)

	labels := map[string]string{"demo-session": sessionID}

	if err := d.runContainer(ctx, runSpec{
		Name:   pgName,
		Image:  "postgres:16-alpine",
		Env:    []string{"POSTGRES_DB=loremind", "POSTGRES_USER=loremind", "POSTGRES_PASSWORD=" + pgPassword},
		Labels: copyLabels(labels, "postgres"),
		Memory: cfg.PostgresMemoryBytes,
		Tmpfs:  map[string]string{"/var/lib/postgresql/data": "rw,size=200m"},
		Net:    cfg.SessionsNetwork,
		Alias:  pgName,
	}); err != nil {
		return fmt.Errorf("spawn postgres: %w", err)
	}

	if err := d.runContainer(ctx, runSpec{
		Name:  brainName,
		Image: cfg.Registry + "/ietm64/brain:" + cfg.Tag,
		Env: []string{
			"INTERNAL_SHARED_SECRET=" + brainSecret,
			// Pas de provider LLM configure en demo : les features IA echoueront
			// proprement, la demo sert principalement a explorer l'edition.
			"LLM_PROVIDER=ollama",
			"OLLAMA_BASE_URL=http://localhost:1",
		},
		Labels: copyLabels(labels, "brain"),
		Memory: cfg.BrainMemoryBytes,
		Net:    cfg.SessionsNetwork,
		Alias:  brainName,
	}); err != nil {
		return fmt.Errorf("spawn brain: %w", err)
	}

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
		},
		Labels: copyLabels(labels, "core"),
		Memory: cfg.CoreMemoryBytes,
		Net:    cfg.SessionsNetwork,
		Alias:  coreName,
	}); err != nil {
		return fmt.Errorf("spawn core: %w", err)
	}

	return nil
}

func (d *DockerClient) runContainer(ctx context.Context, s runSpec) error {
	// Pull best-effort : si l'image est deja locale, ContainerCreate la reprendra.
	_ = d.pullImage(ctx, s.Image)

	spec := containerSpec{
		Image:  s.Image,
		Env:    s.Env,
		Labels: s.Labels,
		HostConfig: hostConfig{
			Memory:        s.Memory,
			NanoCPUs:      1_000_000_000, // 1 vCPU par conteneur
			PidsLimit:     200,           // anti fork-bomb
			Tmpfs:         s.Tmpfs,
			SecurityOpt:   []string{"no-new-privileges:true"},
			RestartPolicy: restartPolicy{Name: "no"},
		},
		NetworkingConfig: networkingConfig{
			EndpointsConfig: map[string]endpointSettings{
				s.Net: {Aliases: []string{s.Alias}},
			},
		},
	}
	body, err := json.Marshal(spec)
	if err != nil {
		return err
	}

	createResp, err := d.do(ctx, "POST", "/containers/create?name="+url.QueryEscape(s.Name), body)
	if err != nil {
		return fmt.Errorf("create %s: %w", s.Name, err)
	}
	var created struct {
		ID string `json:"Id"`
	}
	if err := json.Unmarshal(createResp, &created); err != nil {
		return fmt.Errorf("parse create %s: %w", s.Name, err)
	}
	if _, err := d.do(ctx, "POST", "/containers/"+created.ID+"/start", nil); err != nil {
		return fmt.Errorf("start %s: %w", s.Name, err)
	}
	return nil
}

// pullImage drain le flux de progression. Erreur silencieuse : si le pull
// echoue (registre prive sans auth, image deja locale), runContainer aura un
// retour clair via ContainerCreate.
func (d *DockerClient) pullImage(ctx context.Context, img string) error {
	req, err := http.NewRequestWithContext(ctx, "POST",
		d.baseURL+"/images/create?fromImage="+url.QueryEscape(img), nil)
	if err != nil {
		return err
	}
	resp, err := d.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	_, _ = io.Copy(io.Discard, resp.Body)
	if resp.StatusCode >= 400 {
		return fmt.Errorf("pull %s: status %d", img, resp.StatusCode)
	}
	return nil
}

// WaitReady poll l'endpoint /api/config du core jusqu'a 200 ou timeout.
func (d *DockerClient) WaitReady(ctx context.Context, sessionID string, timeout time.Duration) bool {
	deadline := time.Now().Add(timeout)
	target := "http://demo-" + sessionID + "-core:8080/api/config"
	c := &http.Client{Timeout: 2 * time.Second}
	for time.Now().Before(deadline) {
		resp, err := c.Get(target)
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

// KillTrio supprime tous les conteneurs labellises demo-session=<id>.
func (d *DockerClient) KillTrio(ctx context.Context, sessionID string) error {
	containers, err := d.listContainersWithLabel(ctx, "demo-session="+sessionID)
	if err != nil {
		return err
	}
	for _, c := range containers {
		_, _ = d.do(ctx, "DELETE", "/containers/"+c.ID+"?force=true", nil)
	}
	return nil
}

// ListSessionIDs : utilise au boot pour retrouver les conteneurs orphelins.
func (d *DockerClient) ListSessionIDs(ctx context.Context) ([]string, error) {
	containers, err := d.listContainersWithLabel(ctx, "demo-session")
	if err != nil {
		return nil, err
	}
	seen := map[string]bool{}
	for _, c := range containers {
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

type containerInfo struct {
	ID     string            `json:"Id"`
	Labels map[string]string `json:"Labels"`
}

func (d *DockerClient) listContainersWithLabel(ctx context.Context, label string) ([]containerInfo, error) {
	filters := map[string][]string{"label": {label}}
	filtersJSON, _ := json.Marshal(filters)
	q := url.Values{}
	q.Set("all", "true")
	q.Set("filters", string(filtersJSON))
	body, err := d.do(ctx, "GET", "/containers/json?"+q.Encode(), nil)
	if err != nil {
		return nil, err
	}
	var list []containerInfo
	if err := json.Unmarshal(body, &list); err != nil {
		return nil, err
	}
	return list, nil
}

// do envoie une requete et renvoie le body. Une reponse 4xx/5xx est convertie
// en erreur avec le contenu pour faciliter le debug.
func (d *DockerClient) do(ctx context.Context, method, path string, body []byte) ([]byte, error) {
	var rdr io.Reader
	if body != nil {
		rdr = bytes.NewReader(body)
	}
	req, err := http.NewRequestWithContext(ctx, method, d.baseURL+path, rdr)
	if err != nil {
		return nil, err
	}
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	resp, err := d.http.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	out, _ := io.ReadAll(resp.Body)
	if resp.StatusCode >= 400 {
		return nil, fmt.Errorf("%s %s: HTTP %d %s", method, path, resp.StatusCode, out)
	}
	return out, nil
}

// --- helpers ---

func copyLabels(base map[string]string, role string) map[string]string {
	out := make(map[string]string, len(base)+1)
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
