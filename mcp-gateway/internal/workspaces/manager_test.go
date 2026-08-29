package workspaces

import (
	"sync"
	"testing"

	"github.com/lucky-aeon/agentx/plugin-helper/internal/platform/config"
	"github.com/lucky-aeon/agentx/plugin-helper/internal/platform/xlog"
	"github.com/lucky-aeon/agentx/plugin-helper/internal/runtime"
)

func TestWorkspaceManagerDoesNotCreateWorkspaceForReadOnlyLookup(t *testing.T) {
	manager := NewWorkspaceManager(config.Config{}, runtime.NewPortManager())

	workspace, ok := manager.GetWorkspace(xlog.NewLogger("workspace-test"), DefaultWorkspace, false)

	if ok || workspace != nil {
		t.Fatal("read-only lookup unexpectedly created a workspace")
	}
	if len(manager.GetWorkspaces()) != 0 {
		t.Fatal("read-only lookup changed workspace state")
	}
}

func TestWorkspaceManagerCreatesOneWorkspaceForConcurrentRequests(t *testing.T) {
	manager := NewWorkspaceManager(config.Config{}, runtime.NewPortManager())
	logger := xlog.NewLogger("workspace-test")

	const requests = 16
	results := make(chan *WorkSpace, requests)
	var waitGroup sync.WaitGroup
	for i := 0; i < requests; i++ {
		waitGroup.Add(1)
		go func() {
			defer waitGroup.Done()
			workspace, ok := manager.GetWorkspace(logger, DefaultWorkspace, true)
			if !ok || workspace == nil {
				t.Error("workspace creation failed")
				return
			}
			results <- workspace
		}()
	}
	waitGroup.Wait()
	close(results)

	var first *WorkSpace
	for workspace := range results {
		if first == nil {
			first = workspace
			continue
		}
		if workspace != first {
			t.Fatal("concurrent requests created different workspace instances")
		}
	}
	if len(manager.GetWorkspaces()) != 1 {
		t.Fatal("expected exactly one workspace")
	}
}
