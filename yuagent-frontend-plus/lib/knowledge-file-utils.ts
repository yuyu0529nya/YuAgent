import type { FileDetail } from "@/types/rag-dataset"

export const KNOWLEDGE_UPLOAD_MAX_SIZE = 100 * 1024 * 1024

export function getFileFingerprint(file: Pick<FileDetail, "id" | "originalFilename" | "filename" | "size">) {
  const name = file.originalFilename || file.filename || file.id
  return `${name}::${file.size}`
}

export function inferRemoteFilename(url: string) {
  try {
    const pathname = new URL(url).pathname
    const rawName = pathname.split("/").filter(Boolean).pop()
    return rawName ? decodeURIComponent(rawName) : "remote-import"
  } catch {
    return "remote-import"
  }
}

export function normalizeUploadError(message?: string, filename?: string) {
  if (!message) {
    return filename ? `${filename} 上传失败，请重试` : "上传失败，请重试"
  }

  const lower = message.toLowerCase()
  if (lower.includes("aborted") || lower.includes("aborterror")) {
    return filename ? `${filename} 上传请求超时，请稍后重试` : "上传请求超时，请稍后重试"
  }
  if (lower.includes("timeout")) {
    return filename ? `${filename} 上传超时，请稍后重试` : "上传超时，请稍后重试"
  }
  return message
}

export function shouldKeepPendingFile(message?: string) {
  if (!message) {
    return false
  }
  const lower = message.toLowerCase()
  return lower.includes("aborted") || lower.includes("aborterror") || lower.includes("timeout")
}
