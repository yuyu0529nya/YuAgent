export interface RagDataset {
  id: string
  name: string
  icon?: string
  description?: string
  userId: string
  fileCount: number
  createdAt: string
  updatedAt: string
}

export enum FileProcessingStatusEnum {
  UPLOADED = "UPLOADED",
  OCR_PROCESSING = "OCR_PROCESSING",
  OCR_COMPLETED = "OCR_COMPLETED",
  EMBEDDING_PROCESSING = "EMBEDDING_PROCESSING",
  COMPLETED = "COMPLETED",
  OCR_FAILED = "OCR_FAILED",
  EMBEDDING_FAILED = "EMBEDDING_FAILED",
}

export interface FileDetail {
  id: string
  url: string
  size: number
  filename: string
  originalFilename: string
  ext: string
  contentType: string
  dataSetId: string
  filePageSize?: number
  isInitialize: number
  isEmbedding: number
  userId: string
  createdAt: string
  updatedAt: string
}

export interface CreateDatasetRequest {
  name: string
  icon?: string
  description?: string
}

export interface UpdateDatasetRequest {
  name: string
  icon?: string
  description?: string
}

export interface QueryDatasetRequest {
  page?: number
  pageSize?: number
  keyword?: string
}

export interface QueryDatasetFileRequest {
  page?: number
  pageSize?: number
  keyword?: string
}

export interface UploadFileRequest {
  datasetId: string
  file: File
}

export interface ImportFileByUrlRequest {
  datasetId: string
  url: string
  filename?: string
}

export interface PageResponse<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export enum FileInitializeStatus {
  NOT_INITIALIZED = 0,
  INITIALIZED = 1,
  PROCESSING = 2,
}

export enum FileEmbeddingStatus {
  NOT_EMBEDDED = 0,
  EMBEDDED = 1,
  PROCESSING = 2,
}

export interface FileStatusConfig {
  initializeStatus: {
    text: string
    variant: "default" | "secondary" | "destructive" | "outline"
    color: string
  }
  embeddingStatus: {
    text: string
    variant: "default" | "secondary" | "destructive" | "outline"
    color: string
  }
}

export interface ProcessFileRequest {
  fileId: string
  datasetId: string
  processType?: number
}

export interface FileProcessProgressDTO {
  fileId: string
  filename: string
  processingStatusEnum: FileProcessingStatusEnum
  processingStatus: number
  processingStatusDescription: string
  processingStage?: string
  processingStageDescription?: string
  currentOcrPageNumber?: number
  currentEmbeddingPageNumber?: number
  filePageSize?: number
  ocrProcessProgress?: number
  embeddingProcessProgress?: number
  statusDescription?: string
  isInitialize: number
  isEmbedding: number
  initializeStatus?: string
  embeddingStatus?: string
  currentPageNumber?: number
  processProgress?: number
}

export interface RagSearchRequest {
  datasetIds: string[]
  question: string
  maxResults?: number
}

export interface DocumentUnitDTO {
  id: string
  fileId: string
  fileName?: string
  page: number
  sourcePage?: number
  titlePath?: string
  segmentType?: string
  segmentOrder?: number
  metadataJson?: string
  content: string
  isOcr: boolean
  isVector: boolean
  similarityScore?: number
  createdAt: string
  updatedAt: string
}

export enum ProcessType {
  INITIALIZE = 1,
  EMBEDDING = 2,
}

export interface ProcessProgressStatus {
  fileId: string
  filename: string
  isInitialize: FileInitializeStatus
  isEmbedding: FileEmbeddingStatus
  processProgress: number
  statusDescription: string
}

export interface FileDetailInfoDTO {
  id: string
  url: string
  size: number
  filename: string
  originalFilename: string
  ext: string
  contentType: string
  dataSetId: string
  filePageSize?: number
  isInitialize: number
  isEmbedding: number
  userId: string
  createdAt: string
  updatedAt: string
  filePath?: string
}

export interface QueryDocumentUnitsRequest {
  fileId: string
  page?: number
  pageSize?: number
  keyword?: string
}

export interface UpdateDocumentUnitRequest {
  id: string
  content: string
}

export interface RagStreamChatRequest {
  datasetIds: string[]
  question: string
  stream?: boolean
}

export interface SSEMessage {
  type: "thinking" | "content" | "error" | "done"
  data?: any
  content?: string
  error?: string
}

export interface RagThinkingData {
  type: "retrieval" | "thinking" | "answer"
  status: "start" | "progress" | "end"
  message?: string
  retrievedCount?: number
  documents?: Array<{
    fileId: string
    fileName: string
    documentId: string
    score: number
  }>
  content?: string
}

export interface RetrievedFileInfo {
  fileId: string
  fileName: string
  documentId?: string
  score?: number
  filePath?: string
  isInstalledRag?: boolean
  userRagId?: string
}

export interface DocumentSegment {
  fileId: string
  fileName: string
  documentId: string
  score: number
  index: number
  contentPreview?: string
}

export interface GetFileDetailRequest {
  fileId: string
  documentId?: string
}

export interface FileDetailResponse {
  fileId: string
  fileName: string
  content: string
  pageCount: number
  fileSize: number
  fileType: string
  createdAt: string
  updatedAt: string
}

export interface FileContentData {
  fileId: string
  documentId?: string
  fileName: string
  content: string
  pageCount: number
  fileSize: number
  fileType: string
  createdAt: string
  updatedAt: string
}

export type ChatLayout = "single" | "split"

export interface ChatUIState {
  layout: ChatLayout
  selectedFile: RetrievedFileInfo | null
  selectedSegment: DocumentSegment | null
  showFileDetail: boolean
  fileDetailData: FileContentData | null
  fileDetailLoading: boolean
  fileDetailError: string | null
}
