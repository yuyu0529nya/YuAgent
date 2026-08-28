import { API_CONFIG } from "@/lib/api-config"
import { fetchApi, isFormData, toJsonBody } from "@/lib/http-request-core"
import type { ApiResponse, RequestConfig, RequestOptions } from "@/lib/http-request-core"

export type { ApiResponse, RequestConfig, RequestOptions }

class PublicHttpClient {
  constructor(private readonly baseUrl = API_CONFIG.BASE_URL) {}

  private request<T>(endpoint: string, config: RequestConfig = {}, options?: RequestOptions): Promise<T> {
    return fetchApi<T>(this.baseUrl, endpoint, config, options)
  }

  get<T>(endpoint: string, config: RequestConfig = {}, options?: RequestOptions): Promise<T> {
    return this.request<T>(endpoint, { ...config, method: "GET" }, options)
  }

  post<T>(endpoint: string, data?: unknown, config: RequestConfig = {}, options?: RequestOptions): Promise<T> {
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
}

export const publicHttpClient = new PublicHttpClient()
