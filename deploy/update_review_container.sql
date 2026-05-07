UPDATE user_containers
SET ip_address = 'host.docker.internal',
    internal_port = 8080,
    external_port = 32801,
    docker_container_id = 'ba3c06f5c08d4015ca0dbf3e5055998ca410a8128aba409496bff7741c26e35d',
    error_message = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE type = 'REVIEW';
