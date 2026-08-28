export interface SseStreamOptions<T> {
  parse: (payload: string) => T
  onData: (data: T) => void
  onError: (error: Error) => void
  onComplete: () => void
  isComplete?: (data: T) => boolean
}

function getEventPayload(event: string): string | null {
  const dataLines = event
    .replace(/\r/g, "")
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trim())

  return dataLines.length > 0 ? dataLines.join("\n") : null
}

/**
 * Consumes a standard SSE response while preserving incomplete chunks.
 * A malformed event is surfaced to the caller instead of being silently dropped,
 * so the UI can leave its loading state and present a useful failure.
 */
export async function consumeSseStream<T>(
  stream: ReadableStream<Uint8Array>,
  options: SseStreamOptions<T>
): Promise<void> {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ""
  let completed = false

  const complete = () => {
    if (!completed) {
      completed = true
      options.onComplete()
    }
  }

  const processEvent = (event: string): boolean => {
    const payload = getEventPayload(event)
    if (!payload) {
      return false
    }
    if (payload === "[DONE]") {
      complete()
      return true
    }

    const data = options.parse(payload)
    options.onData(data)
    if (options.isComplete?.(data)) {
      complete()
      return true
    }
    return false
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        buffer += decoder.decode().replace(/\r/g, "")
        if (buffer.trim()) {
          processEvent(buffer)
        }
        complete()
        return
      }

      buffer += decoder.decode(value, { stream: true }).replace(/\r/g, "")
      const events = buffer.split("\n\n")
      buffer = events.pop() ?? ""

      for (const event of events) {
        if (processEvent(event)) {
          await reader.cancel()
          return
        }
      }
    }
  } catch (error) {
    options.onError(error instanceof Error ? error : new Error("流式响应处理失败"))
  } finally {
    reader.releaseLock()
  }
}
