module github.com/loremind/demo-orchestrator

go 1.23

require (
	github.com/docker/docker v27.3.1+incompatible
	// docker/docker v27.3.1 appelle sockets.DialPipe qui n'existe plus dans
	// go-connections >= 0.6.0. Pin a 0.5.0 pour eviter le build break.
	github.com/docker/go-connections v0.5.0
	github.com/google/uuid v1.6.0
)
