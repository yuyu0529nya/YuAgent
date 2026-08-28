export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface RequestConfig extends RequestInit {
  params?: object
  timeout?: number
}

export interface RequestOptions {
  raw?: boolean
}

interface FetchApiOptions extends RequestOptions {
  defaultTimeout?: number
  onUnauthorized?: () => void
}

export const DEFAULT_REQUEST_TIMEOUT = 30_000

export function isFormData(body: unknown): body is FormData {
  return typeof FormData !== "undefined" && body instanceof FormData
}

export function toJsonBody(data: unknown) {
  return data === undefined ? undefined : JSON.stringify(data)
}

function buildUrl(baseUrl: string, endpoint: string, params?: object) {
  const query = new URLSearchParams()

  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null) {
      query.append(key, String(value))
    }
  })

  if (query.size === 0) {
    return `${baseUrl}${endpoint}`
  }

  return `${baseUrl}${endpoint}${endpoint.includes("?") ? "&" : "?"}${query.toString()}`
}

function buildRequestHeaders(config: RequestConfig) {
  const headers = new Headers(config.headers)
  headers.set("Accept", headers.get("Accept") ?? "*/*")

  if (config.body !== undefined && !isFormData(config.body) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json")
  }

  return headers
}

function createErrorResponse(error: unknown, timedOut: boolean): ApiResponse<null> {
  const aborted = error instanceof Error && error.name === "AbortError"

  if (aborted) {
    return {
      code: timedOut ? 408 : 499,
      message: timedOut ? "请求超时，请稍后重试" : "请求已取消",
      data: null,
      timestamp: Date.now(),
    }
  }

  if (error instanceof TypeError) {
    return {
      code: 503,
      message: "网络连接失败，请检查网络后重试",
      data: null,
      timestamp: Date.now(),
    }
  }

  return {
    code: 500,
    message: error instanceof Error ? error.message : "请求失败",
    data: null,
    timestamp: Date.now(),
  }
}

async function parseResponse<T>(response: Response, options: FetchApiOptions): Promise<T> {
  if (response.status === 401 && options.onUnauthorized) {
    options.onUnauthorized()
    return {
      code: 401,
      message: "未登录或登录已过期",
      data: null,
      timestamp: Date.now(),
    } as T
  }

  if (options.raw) {
    return response as T
  }

  if (response.status === 204) {
    return {
      code: 200,
      message: "成功",
      data: null,
      timestamp: Date.now(),
    } as T
  }

  try {
    return await response.json() as T
  } catch {
    return {
      code: response.status,
      message: response.statusText || `请求失败 (${response.status})`,
      data: null,
      timestamp: Date.now(),
    } as T
  }
}

export async function fetchApi<T>(
  baseUrl: string,
  endpoint: string,
  config: RequestConfig = {},
  options: FetchApiOptions = {},
): Promise<T> {
  const { params, timeout = options.defaultTimeout ?? DEFAULT_REQUEST_TIMEOUT, signal, ...requestInit } = config
  const controller = new AbortController()
  const abortFromCaller = () => controller.abort()

  if (signal) {
    signal.addEventListener("abort", abortFromCaller, { once: true })
    if (signal.aborted) {
      controller.abort()
    }
  }

  let timedOut = false
  const timeoutId = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, timeout)

  try {
    const response = await fetch(buildUrl(baseUrl, endpoint, params), {
      ...requestInit,
      headers: buildRequestHeaders(config),
      signal: controller.signal,
    })
    return await parseResponse<T>(response, options)
  } catch (error) {
    return createErrorResponse(error, timedOut) as T
  } finally {
    clearTimeout(timeoutId)
    signal?.removeEventListener("abort", abortFromCaller)
  }
}
