package workspaces

import (
	"sync"

	"github.com/google/uuid"
	"github.com/lucky-aeon/agentx/plugin-helper/internal/platform/config"
	"github.com/lucky-aeon/agentx/plugin-helper/internal/platform/xlog"
	"github.com/lucky-aeon/agentx/plugin-helper/internal/runtime"
	"github.com/lucky-aeon/agentx/plugin-helper/internal/sessions"
)

type WorkspaceManager struct {
	workspaces     map[string]*WorkSpace
	workspacesLock sync.RWMutex

	cfg         config.Config
	portManager runtime.PortManagerI
}

func NewWorkspaceManager(cfg config.Config, portManager runtime.PortManagerI) *WorkspaceManager {
	return &WorkspaceManager{workspaces: make(map[string]*WorkSpace), cfg: cfg, portManager: portManager}
}

// GetWorkspace returns a workspace by id. Creation is serialized so concurrent
// requests for the same id always receive the same workspace instance.
func (m *WorkspaceManager) GetWorkspace(xl xlog.Logger, workId string, createIfNotExists bool) (*WorkSpace, bool) {
	if workId == "" {
		workId = uuid.New().String()
	}

	m.workspacesLock.RLock()
	workspace, ok := m.workspaces[workId]
	m.workspacesLock.RUnlock()
	if ok {
		return workspace, true
	}

	if !createIfNotExists {
		return nil, false
	}

	m.workspacesLock.Lock()
	defer m.workspacesLock.Unlock()

	if workspace, ok = m.workspaces[workId]; ok {
		return workspace, true
	}

	xl.Infof("Creating new workspace, id: %s", workId)
	workspace = NewWorkSpace(workId, config.WorkspaceConfig{
		LogConfig: config.LogConfig{
			Level: m.cfg.LogLevel,
			Path:  m.cfg.WorkspacePath,
		},
		McpServiceMgrConfig: m.cfg.McpServiceMgrConfig,
		Servers:             make(map[string]config.MCPServerConfig),
	}, m.portManager, sessions.CleanupConfig{
		InactivityCheckInterval: m.cfg.SessionGCInterval,
		NoConnectionTTL:         m.cfg.ProxySessionTimeout,
	})
	m.workspaces[workspace.Id] = workspace
	return workspace, true
}

func (m *WorkspaceManager) GetWorkspaces() map[string]*WorkSpace {
	m.workspacesLock.RLock()
	defer m.workspacesLock.RUnlock()
	workspaces := make(map[string]*WorkSpace, len(m.workspaces))
	for id, workspace := range m.workspaces {
		workspaces[id] = workspace
	}
	return workspaces
}

func (m *WorkspaceManager) DeleteWorkspace(xl xlog.Logger, workId string) {
	m.workspacesLock.Lock()
	workspace, ok := m.workspaces[workId]
	if ok {
		delete(m.workspaces, workId)
	}
	m.workspacesLock.Unlock()

	if ok {
		workspace.Close(xl)
	}
}
