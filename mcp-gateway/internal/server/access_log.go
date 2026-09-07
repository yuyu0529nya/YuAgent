package server

import (
	"net/url"
	"strings"
	"time"

	"github.com/labstack/echo/v4"
)

// AccessLogMiddleware records request metadata without Echo's default URI field,
// which would persist credentials supplied through query parameters.
func AccessLogMiddleware() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) error {
			startedAt := time.Now()
			err := next(c)
			status := c.Response().Status
			if status == 0 {
				status = 200
			}
			c.Logger().Infof("request method=%s uri=%s status=%d latency=%s",
				c.Request().Method, sanitizeRequestURI(c.Request().URL.RequestURI()), status, time.Since(startedAt))
			return err
		}
	}
}

func sanitizeRequestURI(rawURI string) string {
	parsed, err := url.ParseRequestURI(rawURI)
	if err != nil {
		if path, _, found := strings.Cut(rawURI, "?"); found {
			return path
		}
		return rawURI
	}

	query := parsed.Query()
	for key := range query {
		if isSensitiveQueryKey(key) {
			query[key] = []string{"[REDACTED]"}
		}
	}
	parsed.RawQuery = query.Encode()
	return parsed.RequestURI()
}

func isSensitiveQueryKey(key string) bool {
	normalized := strings.ToLower(strings.TrimSpace(key))
	for _, marker := range []string{"key", "token", "secret", "password", "credential", "auth"} {
		if strings.Contains(normalized, marker) {
			return true
		}
	}
	return false
}
