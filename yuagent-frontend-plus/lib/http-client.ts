import { toast } from "@/hooks/use-toast"
import { API_CONFIG } from "@/lib/api-config"
import { DEFAULT_REQUEST_TIMEOUT, fetchApi, isFormData, toJsonBody } from "@/lib/http-request-core"
import type { ApiResponse, RequestConfig, RequestOptions as CoreRequestOptions } from "@/lib/http-request-core"

export type { ApiResponse, RequestConfig }

export interface RequestOptions extends CoreRequestOptions {
  raw?: boolean
  showToast?: boolean
}

export interface Interceptor {
  request?: (config: RequestConfig) => RequestConfig
}

const AUTH_TOKEN_KEY = "auth_token"
const AUTH_COOKIE_NAME = "token"
const AUTH_PAGES = ["/login", "/register", "/sso/"]

function clearAuthentication() {
  if (typeof window === "undefined") {
    return
  }

  localStorage.removeItem(AUTH_TOKEN_KEY)
  document.cookie = `${AUTH_COOKIE_NAME}=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;`
}

function isAuthenticationPage(pathname: string) {
  return AUTH_PAGES.some((path) => pathname.includes(path))
}

export function handleUnauthorized() {
  if (typeof window === "undefined") {
    return
  }

  clearAuthentication()
  if (!isAuthenticationPage(window.location.pathname)) {
    window.location.assign("/login")
  }
}

function getCookie(name: string) {
  if (typeof document === "undefined") {
    return null
  }

  const cookie = document.cookie
    .split(";")
    .map((item) => item.trim())
    .find((item) => item.startsWith(`${name}=`))

  if (!cookie) {
    return null
  }

  try {
    return decodeURIComponent(cookie.slice(name.length + 1))
  } catch {
    return null
  }
}

export function getAuthenticationToken() {
  if (typeof window === "undefined") {
    return null
  }

  const token = localStorage.getItem(AUTH_TOKEN_KEY) ?? getCookie(AUTH_COOKIE_NAME)
  if (!token) {
    return null
  }

  if (token.split(".").length !== 3) {
    clearAuthentication()
    return null
  }

  if (isExpiredJwt(token)) {
    clearAuthentication()
    return null
  }

  localStorage.setItem(AUTH_TOKEN_KEY, token)
  return token
}

export function getAuthenticatedHeaders(): HeadersInit {
  const token = getAuthenticationToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

function parseJwtPayload(token: string) {
  const payload = token.split(".")[1]
    .replace(/-/g, "+")
    .replace(/_/g, "/")
  return JSON.parse(atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, "=")))
}

function isExpiredJwt(token: string) {
  try {
    const payload = parseJwtPayload(token) as { exp?: unknown }
    return typeof payload.exp === "number" && payload.exp <= Math.floor(Date.now() / 1000)
  } catch {
    return true
  }
}

function showToast(response: ApiResponse<unknown>) {
  toast({
    variant: response.code === 200 ? "default" : "destructive",
    title: response.code === 200 ? "成功" : "错误",
    description: response.message,
  })
}

class HttpClient {
  private readonly interceptors: Interceptor[] = []

  constructor(
    private readonly baseUrl = API_CONFIG.BASE_URL,
    private readonly defaultTimeout = DEFAULT_REQUEST_TIMEOUT,
  ) {}

  addInterceptor(interceptor: Interceptor) {
    this.interceptors.push(interceptor)
  }

  private applyRequestInterceptors(config: RequestConfig) {
    return this.interceptors.reduce(
      (currentConfig, interceptor) => interceptor.request?.(currentConfig) ?? currentConfig,
      config,
    )
  }

  private async request<T>(
    endpoint: string,
    config: RequestConfig = {},
    options?: RequestOptions,
  ): Promise<T> {
    const interceptedConfig = this.applyRequestInterceptors({ ...config })
    const result = await fetchApi<T>(this.baseUrl, endpoint, interceptedConfig, {
      raw: options?.raw,
      defaultTimeout: this.defaultTimeout,
      onUnauthorized: handleUnauthorized,
    })

    if (options?.showToast) {
      showToast(result as ApiResponse<unknown>)
    }

    return result
  }

  get<T>(endpoint: string, config: RequestConfig = {}, options?: RequestOptions) {
    return this.request<T>(endpoint, { ...config, method: "GET" }, options)
  }

  post<T>(endpoint: string, data?: unknown, config: RequestConfig = {}, options?: RequestOptions) {
    const body = isFormData(data) ? data : toJsonBody(data)
    const headers = new Headers(config.headers)
    if (isFormData(body)) {
      headers.delete("Content-Type")
    }

    return this.request<T>(endpoint, {
      ...config,
      method: "POST",
      body,
      headers,
    }, options)
  }

  put<T>(endpoint: string, data?: unknown, config: RequestConfig = {}, options?: RequestOptions) {
    const body = isFormData(data) ? data : toJsonBody(data)
    const headers = new Headers(config.headers)
    if (isFormData(body)) {
      headers.delete("Content-Type")
    }

    return this.request<T>(endpoint, {
      ...config,
      method: "PUT",
      body,
      headers,
    }, options)
  }

  delete<T>(endpoint: string, config: RequestConfig = {}, options?: RequestOptions) {
    return this.request<T>(endpoint, { ...config, method: "DELETE" }, options)
  }
}

export const httpClient = new HttpClient()

httpClient.addInterceptor({
  request: (config) => {
    const token = getAuthenticationToken()
    if (!token) {
      return config
    }

    const headers = new Headers(config.headers)
    headers.set("Authorization", `Bearer ${token}`)
    return { ...config, headers }
  },
})

export function checkAuthStatus() {
  return getAuthenticationToken() !== null
}
