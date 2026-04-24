module github.com/loremind/demo-orchestrator

go 1.23

// Aucune dependance externe : on parle a Docker Engine en HTTP brut
// (cf. docker.go) plutot que d'utiliser github.com/docker/docker, dont le
// graphe transitif est instable d'une version a l'autre (sockets.DialPipe,
// errors.As/Is, otelhttp...).
