ARG UV_IMAGE=ghcr.io/astral-sh/uv:latest
FROM ${UV_IMAGE} AS uv

FROM golang:1.23-alpine AS builder

WORKDIR /src

ARG GOPROXY=https://goproxy.cn,direct
ENV GOPROXY=$GOPROXY

COPY go.mod go.sum ./
RUN go mod download

COPY cmd ./cmd
COPY internal ./internal

RUN CGO_ENABLED=0 go build -o /out/proxy-server ./cmd/mcp-gateway

# Test-only target: bridge integration tests launch a temporary MCP server via
# npx, while the production image intentionally contains no Go toolchain.
FROM builder AS test
COPY test ./test
RUN apk add --no-cache nodejs npm

FROM node:20-alpine

RUN apk add --update --no-cache git

# Install python/pip
ENV PYTHONUNBUFFERED=1
RUN apk add --update --no-cache python3 py3-pip

COPY --from=uv /uv /uvx /usr/local/bin/

# Copy the proxy server binary built from this repository
COPY --from=builder /out/proxy-server /usr/local/bin/proxy-server

# Add execute permissions and set root user
USER root
RUN chmod +x /usr/local/bin/proxy-server && \
    chmod +x /usr/local/bin/uvx && \
    chmod +x /usr/local/bin/uv

# Set working directory
WORKDIR /etc/proxy

# Set the proxy server as entrypoint
ENTRYPOINT ["/usr/local/bin/proxy-server"]
