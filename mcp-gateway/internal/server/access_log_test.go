package server

import (
	"strings"
	"testing"
)

func TestSanitizeRequestURIRedactsCredentialQueries(t *testing.T) {
	uri := sanitizeRequestURI("/stream?api_key=unique-key&workspaceId=default&access_token=unique-token")
	if strings.Contains(uri, "unique-key") || strings.Contains(uri, "unique-token") {
		t.Fatalf("sanitized URI exposed a credential: %s", uri)
	}
	if !strings.Contains(uri, "api_key=%5BREDACTED%5D") || !strings.Contains(uri, "workspaceId=default") {
		t.Fatalf("sanitized URI lost expected metadata: %s", uri)
	}
}
