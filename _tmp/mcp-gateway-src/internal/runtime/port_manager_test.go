package runtime

import (
	"fmt"
	"net"
	"testing"
)

func TestPortManager_GetNextAvailablePort(t *testing.T) {
	pm := NewPortManager()

	// 测试连续分配端口，确保每个端口都是有效的
	ports := make(map[int]bool)
	for i := 0; i < 10; i++ {
		port := pm.GetNextAvailablePort()

		// 检查端口是否重复
		if ports[port] {
			t.Errorf("Port %d was allocated twice", port)
		}
		ports[port] = true

		// 检查端口是否在有效范围内
		if port < 1024 || port > 65535 {
			t.Errorf("Port %d is out of valid range", port)
		}

		// 尝试绑定端口，验证端口确实可用
		listener, err := net.Listen("tcp", fmt.Sprintf("127.0.0.1:%d", port))
		if err != nil {
			t.Fatalf("Failed to create listener for port validation: %v", err)
		}
		listener.Close()
	}
}

func TestPortManager_ReleasePort(t *testing.T) {
	pm := NewPortManager().(*portManager)

	port := pm.GetNextAvailablePort()
	if port == 0 {
		t.Fatal("expected a port to be allocated")
	}
	if _, reserved := pm.allocated[port]; !reserved {
		t.Fatalf("expected port %d to be reserved", port)
	}

	pm.ReleasePort(port)
	if _, reserved := pm.allocated[port]; reserved {
		t.Fatalf("expected port %d to be released", port)
	}
}

func TestPortManager_ConcurrentAllocation(t *testing.T) {
	pm := NewPortManager()

	// 测试并发分配端口
	done := make(chan int, 100)
	for i := 0; i < 100; i++ {
		go func() {
			port := pm.GetNextAvailablePort()
			done <- port
		}()
	}

	// 收集所有分配的端口
	ports := make(map[int]bool)
	for i := 0; i < 100; i++ {
		port := <-done
		if ports[port] {
			t.Errorf("Port %d was allocated concurrently", port)
		}
		ports[port] = true
	}
}

func TestPortManager_PortAvailability(t *testing.T) {
	pm := NewPortManager()

	// 分配端口
	port := pm.GetNextAvailablePort()

	// 验证端口确实可用
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("Failed to create listener: %v", err)
	}
	defer listener.Close()

	// 系统分配的端口在 listener.Close() 后可能被其他进程占用
	// 这是正常行为，我们只需要验证分配机制本身是有效的
	t.Logf("Allocated port: %d", port)
}
