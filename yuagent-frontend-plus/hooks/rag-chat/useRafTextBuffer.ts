import { useCallback, useEffect, useRef } from "react"

/**
 * Coalesces high-frequency streaming text chunks into at most one UI update per
 * animation frame. Call flush before changing a message to a terminal state so
 * the final buffered chunk is never lost.
 */
export function useRafTextBuffer(onFlush: (content: string) => void) {
  const onFlushRef = useRef(onFlush)
  const pendingContentRef = useRef("")
  const frameRef = useRef<number | null>(null)

  useEffect(() => {
    onFlushRef.current = onFlush
  }, [onFlush])

  const flush = useCallback(() => {
    if (frameRef.current !== null) {
      cancelAnimationFrame(frameRef.current)
      frameRef.current = null
    }

    const content = pendingContentRef.current
    pendingContentRef.current = ""
    if (content) {
      onFlushRef.current(content)
    }
  }, [])

  const append = useCallback((content: string) => {
    if (!content) {
      return
    }

    pendingContentRef.current += content
    if (frameRef.current === null) {
      frameRef.current = requestAnimationFrame(flush)
    }
  }, [flush])

  const reset = useCallback(() => {
    pendingContentRef.current = ""
    if (frameRef.current !== null) {
      cancelAnimationFrame(frameRef.current)
      frameRef.current = null
    }
  }, [])

  useEffect(() => reset, [reset])

  return { append, flush, reset }
}
