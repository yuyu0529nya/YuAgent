import { FileDetail, FileProcessProgressDTO, FileProcessingStatusEnum } from "@/types/rag-dataset"

export interface FileStatusDisplayConfig {
  text: string
  variant: "default" | "secondary" | "destructive" | "outline"
  color: string
  iconType: "check" | "clock" | "alert" | "loading"
}

const PROCESSING_STAGE_LABELS: Record<string, string> = {
  PDF_NATIVE_EXTRACTING: "快速解析中",
  PDF_OCR_FALLBACK: "OCR补全中",
  PDF_SEGMENTING: "整理分段中",
}

export function getFileStatusConfig(
  file: FileDetail,
  progressInfo?: FileProcessProgressDTO
): {
  status: FileStatusDisplayConfig
  canStartOcr: boolean
  canStartEmbedding: boolean
  progress: number
} {
  const statusEnum = progressInfo?.processingStatusEnum
  const statusCode = progressInfo?.processingStatus

  let statusConfig: FileStatusDisplayConfig
  let canStartOcr = false
  let canStartEmbedding = false
  let progress = progressInfo?.processProgress || 0

  if (statusEnum) {
    switch (statusEnum) {
      case FileProcessingStatusEnum.UPLOADED:
        statusConfig = {
          text: "已上传",
          variant: "outline",
          color: "text-yellow-600 border-yellow-300",
          iconType: "clock",
        }
        canStartOcr = true
        break

      case FileProcessingStatusEnum.OCR_PROCESSING:
        statusConfig = {
          text: getProcessingBadgeText(progressInfo),
          variant: "outline",
          color: "text-blue-600 border-blue-300",
          iconType: "loading",
        }
        progress = progressInfo?.ocrProcessProgress || 0
        break

      case FileProcessingStatusEnum.OCR_COMPLETED:
        statusConfig = {
          text: "解析完成",
          variant: "secondary",
          color: "text-green-600 bg-green-50 border-green-300",
          iconType: "check",
        }
        canStartEmbedding = statusCode === 2
        break

      case FileProcessingStatusEnum.EMBEDDING_PROCESSING:
        statusConfig = {
          text: "向量化中",
          variant: "outline",
          color: "text-blue-600 border-blue-300",
          iconType: "loading",
        }
        progress = progressInfo?.embeddingProcessProgress || 0
        break

      case FileProcessingStatusEnum.COMPLETED:
        statusConfig = {
          text: "处理完成",
          variant: "default",
          color: "text-green-600 bg-green-50 border-green-300",
          iconType: "check",
        }
        progress = 100
        break

      case FileProcessingStatusEnum.OCR_FAILED:
        statusConfig = {
          text: "解析失败",
          variant: "destructive",
          color: "text-red-600 border-red-300",
          iconType: "alert",
        }
        canStartOcr = true
        break

      case FileProcessingStatusEnum.EMBEDDING_FAILED:
        statusConfig = {
          text: "向量化失败",
          variant: "destructive",
          color: "text-red-600 border-red-300",
          iconType: "alert",
        }
        canStartEmbedding = statusCode === 2
        break

      default:
        statusConfig = {
          text: "未知状态",
          variant: "destructive",
          color: "text-red-600",
          iconType: "alert",
        }
        break
    }
  } else {
    if (file.isInitialize === 0) {
      statusConfig = {
        text: "待初始化",
        variant: "outline",
        color: "text-yellow-600 border-yellow-300",
        iconType: "clock",
      }
      canStartOcr = true
    } else if (file.isInitialize === 1) {
      statusConfig = {
        text: "已初始化",
        variant: "secondary",
        color: "text-green-600 bg-green-50 border-green-300",
        iconType: "check",
      }
      canStartEmbedding = file.isEmbedding === 0
    } else {
      statusConfig = {
        text: "处理中",
        variant: "outline",
        color: "text-blue-600 border-blue-300",
        iconType: "loading",
      }
    }
  }

  return {
    status: statusConfig,
    canStartOcr,
    canStartEmbedding,
    progress,
  }
}

export function getStatusDescription(
  statusEnum?: FileProcessingStatusEnum,
  statusDescription?: string,
  processingStageDescription?: string
): string {
  if (processingStageDescription) {
    return processingStageDescription
  }

  if (statusDescription) {
    return statusDescription
  }

  if (!statusEnum) {
    return "未知状态"
  }

  const statusMap = {
    [FileProcessingStatusEnum.UPLOADED]: "已上传，等待开始处理",
    [FileProcessingStatusEnum.OCR_PROCESSING]: "正在解析文档内容",
    [FileProcessingStatusEnum.OCR_COMPLETED]: "文本解析完成，等待向量化",
    [FileProcessingStatusEnum.EMBEDDING_PROCESSING]: "正在生成向量",
    [FileProcessingStatusEnum.COMPLETED]: "全部处理完成",
    [FileProcessingStatusEnum.OCR_FAILED]: "文本解析失败",
    [FileProcessingStatusEnum.EMBEDDING_FAILED]: "向量化失败",
  }

  return statusMap[statusEnum] || "未知状态"
}

function getProcessingBadgeText(progressInfo?: FileProcessProgressDTO): string {
  if (!progressInfo?.processingStage) {
    return "解析中"
  }
  return PROCESSING_STAGE_LABELS[progressInfo.processingStage] || "解析中"
}
