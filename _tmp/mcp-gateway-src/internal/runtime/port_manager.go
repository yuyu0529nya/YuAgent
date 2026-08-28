package runtime

import (
	"net"
	"sync"
)

type PortManagerI interface {
	GetNextAvailablePort() int
	ReleasePort(port int)
}

type portManager struct {
	mu        sync.Mutex
	allocated map[int]struct{}
}

func NewPortManager() PortManagerI {
	return &portManager{allocated: make(map[int]struct{})}
}

func (pm *portManager) GetNextAvailablePort() int {
	pm.mu.Lock()
	defer pm.mu.Unlock()

	for attempts := 0; attempts < 256; attempts++ {
		listener, err := net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			return 0
		}

		port := listener.Addr().(*net.TCPAddr).Port
		listener.Close()
		if _, reserved := pm.allocated[port]; reserved {
			continue
		}

		pm.allocated[port] = struct{}{}
		return port
	}

	return 0
}

func (pm *portManager) ReleasePort(port int) {
	if port == 0 {
		return
	}
	pm.mu.Lock()
	delete(pm.allocated, port)
	pm.mu.Unlock()
}
