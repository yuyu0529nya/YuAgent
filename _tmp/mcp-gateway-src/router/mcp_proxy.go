package router

import (
	"bufio"
	"fmt"
	"io"
	"net/http"
	"crypto/tls"
	"strings"
	"time"

	"github.com/labstack/echo/v4"
	"github.com/lucky-aeon/agentx/plugin-helper/service"
	"github.com/lucky-aeon/agentx/plugin-helper/utils"
	"github.com/lucky-aeon/agentx/plugin-helper/xlog"
)

// proxyHandler forwards local gateway requests to the backing MCP service.
func (m *ServerManager) proxyHandler() echo.HandlerFunc {
	return func(c echo.Context) error {
		xl := xlog.NewLogger("PROXY")
		path := c.Request().URL.Path

		parts := strings.Split(strings.TrimPrefix(path, "/"), "/")
		if len(parts) < 2 {
			return c.String(http.StatusNotFound, "Invalid path")
		}

		serviceName := parts[0]
		lastRoute := parts[len(parts)-1]
		remainingPath := "/" + strings.Join(parts[1:], "/")
		workspace := utils.GetWorkspace(c, service.DefaultWorkspace)

		m.RLock()
		instance, err := m.mcpServiceMgr.GetMcpService(xl, service.NameArg{
			Server:    serviceName,
			Workspace: workspace,
		})
		m.RUnlock()
		if err != nil {
			return c.String(http.StatusNotFound, "Service not found")
		}

		originalQuery := c.Request().URL.RawQuery

		var baseURL string
		switch lastRoute {
		case "sse":
			baseURL = instance.GetSSEUrl()
		case "message":
			baseURL = instance.GetMessageUrl()
			c.Logger().Infof("Message URL: %s", baseURL)
		default:
			if url := instance.GetUrl(); url != "" {
				baseURL = strings.TrimRight(url, "/")
				if remainingPath != "/" {
					baseURL += remainingPath
				}
			} else {
				return c.String(http.StatusNotFound, "Service not available")
			}
		}

		targetURL := baseURL
		if originalQuery != "" {
			if strings.Contains(baseURL, "?") {
				targetURL = baseURL + "&" + originalQuery
			} else {
				targetURL = baseURL + "?" + originalQuery
			}
		}

		c.Logger().Infof("Proxy request: %s, target URL: %s, lastRoute: %s, query: %s",
			c.Request().URL, targetURL, lastRoute, originalQuery)

		req, err := http.NewRequest(c.Request().Method, targetURL, c.Request().Body)
		if err != nil {
			return err
		}

		for k, v := range c.Request().Header {
			req.Header[k] = v
		}
		for k, v := range instance.Info().Config.Headers {
			req.Header.Set(k, v)
		}

		client := &http.Client{
			Transport: &http.Transport{
				Proxy:                 http.ProxyFromEnvironment,
				ForceAttemptHTTP2:     false,
				MaxIdleConns:          100,
				MaxIdleConnsPerHost:   20,
				IdleConnTimeout:       90 * time.Second,
				TLSHandshakeTimeout:   20 * time.Second,
				ResponseHeaderTimeout: 20 * time.Second,
				ExpectContinueTimeout: 5 * time.Second,
				TLSClientConfig: &tls.Config{
					MinVersion: tls.VersionTLS12,
				},
			},
		}
		resp, err := client.Do(req)
		if err != nil {
			return err
		}
		defer resp.Body.Close()

		for k, v := range resp.Header {
			c.Response().Header()[k] = v
		}

		if utils.IsSSE(resp.Header) {
			c.Response().Header().Set("Content-Type", "text/event-stream")
			c.Response().Header().Set("Cache-Control", "no-cache")
			c.Response().Header().Set("Connection", "keep-alive")
			c.Response().WriteHeader(resp.StatusCode)
			c.Response().Flush()

			reader := bufio.NewReader(resp.Body)
			var currentEvent string

			for {
				line, err := reader.ReadString('\n')
				if err != nil {
					if err == io.EOF {
						break
					}
					return err
				}

				line = strings.TrimSpace(line)
				if line == "" {
					continue
				}

				if strings.HasPrefix(line, "event: ") {
					currentEvent = strings.TrimPrefix(line, "event: ")
					fmt.Fprintf(c.Response(), "event: %s\n", currentEvent)
				} else if strings.HasPrefix(line, "data: ") {
					data := strings.TrimPrefix(line, "data: ")

					if currentEvent == "endpoint" && strings.HasPrefix(data, "/message") {
						data = fmt.Sprintf("/%s%s", serviceName, data)
					}

					fmt.Fprintf(c.Response(), "data: %s\n\n", data)
				} else {
					fmt.Fprintf(c.Response(), "%s\n", line)
				}
				c.Response().Flush()
			}
			return nil
		}

		c.Response().WriteHeader(resp.StatusCode)
		_, err = io.Copy(c.Response().Writer, resp.Body)
		return err
	}
}
