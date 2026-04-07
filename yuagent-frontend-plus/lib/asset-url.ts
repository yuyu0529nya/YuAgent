import { buildApiUrl } from "@/lib/api-config"

export function resolveAssetUrl(url?: string | null): string | undefined {
  if (!url || url.trim() === "") {
    return undefined
  }

  const trimmedUrl = url.trim()

  if (trimmedUrl.includes(".aliyuncs.com/")) {
    return buildApiUrl("/files/image-proxy", { url: trimmedUrl })
  }

  return trimmedUrl
}
