package admin

import (
	"net/http"
	"strings"
	"time"

	"github.com/labstack/echo/v4"
	"github.com/lucky-aeon/agentx/plugin-helper/internal/platform/identity"
)

func (h *Handler) v1AuthMiddleware(next echo.HandlerFunc) echo.HandlerFunc {
	return func(c echo.Context) error {
		if !h.cfg.GetAuthConfig().IsEnabled() {
			return next(c)
		}

		token := extractBearerToken(c.Request().Header.Get("Authorization"))
		if token == "" {
			return respondError(c, http.StatusUnauthorized, "UNAUTHORIZED", "missing bearer token", nil)
		}
		if h.auth == nil {
			if token != h.cfg.GetAuthConfig().GetApiKey() {
				return respondError(c, http.StatusUnauthorized, "UNAUTHORIZED", "invalid api key", nil)
			}
			c.Set("auth.principal", adminPrincipal())
			return next(c)
		}
		principal, err := h.auth.ValidateBearer(c.Request().Context(), token)
		if err != nil {
			return respondError(c, http.StatusUnauthorized, "UNAUTHORIZED", "invalid bearer token", nil)
		}
		c.Set("auth.principal", principal)
		return next(c)
	}
}

func extractBearerToken(header string) string {
	header = strings.TrimSpace(header)
	if header == "" || !strings.HasPrefix(strings.ToLower(header), "bearer ") {
		return ""
	}
	return strings.TrimSpace(header[7:])
}

func (h *Handler) authMode() string {
	if h.auth != nil {
		return h.auth.Mode()
	}
	return h.cfg.GetAuthConfig().GetMode()
}

func (h *Handler) currentUser() map[string]interface{} {
	if h.auth != nil && h.auth.IsSaaS() {
		return map[string]interface{}{}
	}
	return map[string]interface{}{
		"id":           "admin",
		"email":        "",
		"display_name": "Administrator",
		"role":         "owner",
		"status":       "active",
		"builtin":      true,
		"created_at":   time.Now().UTC().Format(time.RFC3339),
	}
}

func adminPrincipal() *identity.Principal {
	return &identity.Principal{
		AccountID:     "admin",
		DisplayName:   "Administrator",
		Role:          identity.RoleSystemAdmin,
		IsSystemAdmin: true,
		TokenType:     "system_api_key",
	}
}

func (h *Handler) currentPrincipal(c echo.Context) *identity.Principal {
	if v := c.Get("auth.principal"); v != nil {
		if principal, ok := v.(*identity.Principal); ok {
			return principal
		}
	}
	return adminPrincipal()
}

func (h *Handler) workspaceRole(c echo.Context, wsID string) (string, error) {
	if h.auth == nil {
		return identity.RoleSystemAdmin, nil
	}
	return h.auth.WorkspaceRole(c.Request().Context(), wsID, h.currentPrincipal(c))
}

func (h *Handler) requireWorkspaceRole(c echo.Context, wsID, required string) error {
	role, err := h.workspaceRole(c, wsID)
	if err != nil {
		return respondError(c, http.StatusForbidden, "FORBIDDEN", "workspace access denied", nil)
	}
	if !identity.RoleAllows(role, required) {
		return respondError(c, http.StatusForbidden, "FORBIDDEN", "insufficient workspace permissions", nil)
	}
	return nil
}

func (h *Handler) visibleWorkspaceMap(c echo.Context) (map[string]bool, error) {
	if h.auth == nil {
		return nil, nil
	}
	return h.auth.VisibleWorkspaceIDs(c.Request().Context(), h.currentPrincipal(c))
}
