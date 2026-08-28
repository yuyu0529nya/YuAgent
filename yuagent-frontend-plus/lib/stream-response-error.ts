function isErrorPayload(value: unknown): value is { message?: unknown; error?: unknown } {
  return typeof value === "object" && value !== null
}

/**
 * Creates a stable user-facing error for stream endpoints, including proxy pages
 * and other non-JSON failures that cannot be parsed as an API response.
 */
export async function createStreamResponseError(response: Response): Promise<Error> {
  const fallbackMessage = `请求失败（HTTP ${response.status}）`

  try {
    const payload: unknown = await response.json()
    if (!isErrorPayload(payload)) {
      return new Error(fallbackMessage)
    }

    const message = typeof payload.message === "string"
      ? payload.message
      : typeof payload.error === "string"
        ? payload.error
        : fallbackMessage
    return new Error(message)
  } catch {
    return new Error(fallbackMessage)
  }
}
