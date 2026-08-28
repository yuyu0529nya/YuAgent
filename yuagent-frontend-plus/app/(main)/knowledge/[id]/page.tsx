"use client"

import { useCallback, useEffect, useRef, useState } from "react"
import dynamic from "next/dynamic"
import { useParams, useRouter } from "next/navigation"
import Link from "next/link"
import { 
  ArrowLeft, 
  Upload, 
  File, 
  FileText, 
  Image as ImageIcon, 
  Video, 
  Trash, 
  Search, 
  RefreshCw, 
  X,
  CheckCircle,
  Clock,
  AlertCircle,
  Download,
  Play,
  Pause,
  Loader2,
  Settings,
  FileSearch,
  BookOpen,
  MessageSquare
} from "lucide-react"

import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Pagination,
  PaginationContent,
  PaginationEllipsis,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination"
import { Badge } from "@/components/ui/badge"
import { Progress } from "@/components/ui/progress"
import { Textarea } from "@/components/ui/textarea"
import { toast } from "@/hooks/use-toast"

import {
  getDatasetDetail,
  getDatasetFiles,
  uploadFile,
  importFileByUrl,
  deleteFileWithToast,
  processFileWithToast,
  reprocessFileWithToast,
  getDatasetFilesProgressWithToast,
  ragSearchWithToast,
} from "@/lib/rag-dataset-service"
import type { 
  RagDataset, 
  FileDetail, 
  PageResponse,
  FileProcessProgressDTO,
  DocumentUnitDTO 
} from "@/types/rag-dataset"
import { FileInitializeStatus, FileEmbeddingStatus, ProcessType } from "@/types/rag-dataset"
import { getFileStatusConfig as getFileStatusInfo } from "@/lib/file-status-utils"
import {
  getFileFingerprint,
  inferRemoteFilename,
  KNOWLEDGE_UPLOAD_MAX_SIZE,
  normalizeUploadError,
  shouldKeepPendingFile,
} from "@/lib/knowledge-file-utils"

const RagChatDialog = dynamic(() => import("@/components/knowledge/RagChatDialog").then(module => module.RagChatDialog), {
  ssr: false,
})
const DocumentUnitsDialog = dynamic(
  () => import("@/components/knowledge/DocumentUnitsDialog").then(module => module.DocumentUnitsDialog),
  { ssr: false },
)

const PROGRESS_SYNC_INTERVAL = 2000
const UPLOAD_FORCE_SYNC_DURATION = 60000
const FILE_LIST_SYNC_INTERVAL = 6000

export default function DatasetDetailPage() {
  const params = useParams()
  const router = useRouter()
  const datasetId = params.id as string

  const [dataset, setDataset] = useState<RagDataset | null>(null)
  const [files, setFiles] = useState<FileDetail[]>([])
  const [pendingFiles, setPendingFiles] = useState<FileDetail[]>([])
  const [optimisticFiles, setOptimisticFiles] = useState<FileDetail[]>([])
  const [loading, setLoading] = useState(true)
  const [filesLoading, setFilesLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState("")
  const [debouncedQuery, setDebouncedQuery] = useState("")
  const [remoteFileUrl, setRemoteFileUrl] = useState("")
  const [remoteFileName, setRemoteFileName] = useState("")
  const [fileToDelete, setFileToDelete] = useState<FileDetail | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [isUploading, setIsUploading] = useState(false)
  
  // 鏂板鐘舵€侊細鏂囦欢澶勭悊杩涘害
  const [filesProgress, setFilesProgress] = useState<FileProcessProgressDTO[]>([])
  const [isProcessing, setIsProcessing] = useState<{ [fileId: string]: boolean }>({})
  const filesProgressRef = useRef<FileProcessProgressDTO[]>([])
  const isProcessingRef = useRef<{ [fileId: string]: boolean }>({})
  const completedFileIdsRef = useRef<Set<string>>(new Set())
  const forceSyncUntilRef = useRef<number>(0)
  const pollInFlightRef = useRef(false)
  const lastFileListSyncAtRef = useRef(0)
  const activeDatasetIdRef = useRef(datasetId)
  const latestDatasetRequestRef = useRef(0)
  const latestFilesRequestRef = useRef(0)
  const latestProgressRequestRef = useRef(0)

  activeDatasetIdRef.current = datasetId
  
  // 鏂板鐘舵€侊細RAG鎼滅储
  const [searchDocuments, setSearchDocuments] = useState<DocumentUnitDTO[]>([])
  const [ragSearchQuery, setRagSearchQuery] = useState("")
  const [isRagSearching, setIsRagSearching] = useState(false)
  const [showRagResults, setShowRagResults] = useState(false)
  
  // RAG鑱婂ぉ瀵硅瘽妗嗙姸鎬?
  const [showRagChat, setShowRagChat] = useState(false)
  
  // 鏂囨。鍗曞厓瀵硅瘽妗嗙姸鎬?
  const [selectedFileForUnits, setSelectedFileForUnits] = useState<FileDetail | null>(null)

  // 鍒嗛〉鐘舵€?
  const [pageData, setPageData] = useState<PageResponse<FileDetail>>({
    records: [],
    total: 0,
    size: 15,
    current: 1,
    pages: 0
  })
  const pageDataRef = useRef(pageData)

  const loadDatasetDetail = useCallback(async () => {
    const requestId = ++latestDatasetRequestRef.current
    try {
      setLoading(true)
      setError(null)

      const response = await getDatasetDetail(datasetId)
      if (requestId !== latestDatasetRequestRef.current || activeDatasetIdRef.current !== datasetId) {
        return
      }

      if (response.code === 200) {
        setDataset(response.data)
      } else {
        setError(response.message)
      }
    } catch (error) {
      if (requestId === latestDatasetRequestRef.current && activeDatasetIdRef.current === datasetId) {
        const errorMessage = error instanceof Error ? error.message : "未知错误"
        setError(errorMessage)
      }
    } finally {
      if (requestId === latestDatasetRequestRef.current && activeDatasetIdRef.current === datasetId) {
        setLoading(false)
      }
    }
  }, [datasetId])

  const loadFiles = useCallback(async (page: number = 1, keyword?: string, silent: boolean = false) => {
    const requestId = ++latestFilesRequestRef.current
    try {
      if (!silent) {
        setFilesLoading(true)
      }

      const response = await getDatasetFiles(datasetId, {
        page,
        pageSize: 15,
        keyword: keyword?.trim() || undefined
      })

      if (requestId !== latestFilesRequestRef.current || activeDatasetIdRef.current !== datasetId) {
        return
      }

      if (response.code === 200) {
        const serverFingerprints = new Set((response.data.records || []).map(file => getFileFingerprint(file)))
        setPageData(response.data)
        setFiles(response.data.records || [])
        setPendingFiles(prev => prev.filter(file => !serverFingerprints.has(getFileFingerprint(file))))
        setOptimisticFiles(prev => prev.filter(file => !serverFingerprints.has(getFileFingerprint(file))))
      }
    } catch {
      // The shared HTTP client already returns a normalized response for request failures.
    } finally {
      if (!silent && requestId === latestFilesRequestRef.current && activeDatasetIdRef.current === datasetId) {
        setFilesLoading(false)
      }
    }
  }, [datasetId])

  const loadFilesProgress = useCallback(async () => {
    const requestId = ++latestProgressRequestRef.current
    try {
      const response = await getDatasetFilesProgressWithToast(datasetId)
      if (requestId !== latestProgressRequestRef.current || activeDatasetIdRef.current !== datasetId) {
        return
      }
      if (response.code === 200) {
        const newProgress = response.data
        const hasChanged = JSON.stringify(newProgress) !== JSON.stringify(filesProgressRef.current)

        if (hasChanged) {
          setFilesProgress(newProgress)
        }
      }
    } catch {
      // Polling failures are retried on the next interval.
    }
  }, [datasetId])

  const displayedFiles = [...pendingFiles, ...optimisticFiles, ...files].filter(
    (file, index, list) => list.findIndex(item => getFileFingerprint(item) === getFileFingerprint(file)) === index
  )
  const displayedFileCount = (dataset?.fileCount || 0) + pendingFiles.length + optimisticFiles.length

  // 闃叉姈澶勭悊鎼滅储鏌ヨ
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(searchQuery)
    }, 500)

    return () => clearTimeout(timer)
  }, [searchQuery])

  // 鑾峰彇鏁版嵁闆嗚鎯?
  useEffect(() => {
    if (datasetId) {
      loadDatasetDetail()
    }
    return () => {
      latestDatasetRequestRef.current += 1
    }
  }, [datasetId, loadDatasetDetail])

  // 鑾峰彇鏂囦欢鍒楄〃
  useEffect(() => {
    if (datasetId) {
      loadFiles(1, debouncedQuery)
      loadFilesProgress() // 鍚屾椂鍔犺浇鏂囦欢澶勭悊杩涘害
    }
    return () => {
      latestFilesRequestRef.current += 1
      latestProgressRequestRef.current += 1
    }
  }, [datasetId, debouncedQuery, loadFiles, loadFilesProgress])

  useEffect(() => {
    filesProgressRef.current = filesProgress
  }, [filesProgress])

  useEffect(() => {
    isProcessingRef.current = isProcessing
  }, [isProcessing])

  useEffect(() => {
    pageDataRef.current = pageData
  }, [pageData])

  // 瀹氭湡鍒锋柊鏂囦欢澶勭悊杩涘害锛堟櫤鑳藉埛鏂帮級
  useEffect(() => {
    if (!datasetId) return

    const interval = setInterval(() => {
      if (pollInFlightRef.current) {
        return
      }
      const hasProcessingFiles = filesProgressRef.current.some(isProgressStillActive)
      const shouldForceSync = Date.now() < forceSyncUntilRef.current
      const hasManualProcessing = Object.keys(isProcessingRef.current).some(key => isProcessingRef.current[key])

      if (hasProcessingFiles || hasManualProcessing || shouldForceSync || isUploading) {
        pollInFlightRef.current = true
        const now = Date.now()
        const shouldSyncFileList = now - lastFileListSyncAtRef.current >= FILE_LIST_SYNC_INTERVAL
        Promise.all([
          loadFilesProgress(),
          shouldSyncFileList ? loadFiles(pageDataRef.current.current, debouncedQuery, true) : Promise.resolve(),
        ]).finally(() => {
          if (shouldSyncFileList) {
            lastFileListSyncAtRef.current = now
          }
          pollInFlightRef.current = false
        })
      }
    }, PROGRESS_SYNC_INTERVAL)

    return () => clearInterval(interval)
  }, [datasetId, debouncedQuery, isUploading, loadFiles, loadFilesProgress])

  // 鐩戞帶杩涘害鍙樺寲锛屾櫤鑳藉埛鏂版枃浠跺垪琛?
  useEffect(() => {
    const previousCompleted = completedFileIdsRef.current
    const currentCompleted = filesProgress.filter(p => p.processProgress === 100)
    const hasNewCompletedFile = currentCompleted.some(p => !previousCompleted.has(p.fileId))
    completedFileIdsRef.current = new Set(currentCompleted.map(p => p.fileId))

    if (hasNewCompletedFile) {
      // 寤惰繜鍒锋柊鏂囦欢鍒楄〃锛岄伩鍏嶉绻佸埛鏂?
      const timeoutId = setTimeout(() => {
        loadFiles(pageDataRef.current.current, debouncedQuery)
      }, 1000)
      
      return () => clearTimeout(timeoutId)
    }
  }, [filesProgress, debouncedQuery, loadFiles])

  // 澶勭悊鏂囦欢涓婁紶
  const handleFileUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const selectedFiles = event.target.files
    if (!selectedFiles || selectedFiles.length === 0) return

    try {
      const filesToUpload = Array.from(selectedFiles)
      const oversizedFile = filesToUpload.find(file => file.size > KNOWLEDGE_UPLOAD_MAX_SIZE)
      if (oversizedFile) {
        toast({
          title: "文件过大",
          description: `${oversizedFile.name} 超过 ${(KNOWLEDGE_UPLOAD_MAX_SIZE / 1024 / 1024).toFixed(0)}MB 上传上限`,
          variant: "destructive",
        })
        return
      }

      setIsUploading(true)

      let hasSuccess = false
      for (const file of filesToUpload) {
        const pendingFile = createPendingFile(file)
        insertPendingFile(pendingFile)

        const response = await uploadFile(datasetId, file)
        if (response.code === 200) {
          hasSuccess = true
          if (response.data) {
            resolvePendingFile(pendingFile.id, response.data)
          }
          toast({
            title: "上传成功",
            description: `${file.name} 已开始处理`,
          })
        } else {
          if (shouldKeepPendingFile(response.message)) {
            keepPendingFileAlive(pendingFile.id)
          } else {
            removePendingFile(pendingFile.id)
          }
          toast({
            title: "上传失败",
            description: normalizeUploadError(response.message, file.name),
            variant: "destructive",
          })
        }
      }

      if (hasSuccess) {
        forceSyncUntilRef.current = Date.now() + UPLOAD_FORCE_SYNC_DURATION
        lastFileListSyncAtRef.current = 0
        await Promise.all([
          loadFiles(pageData.current, debouncedQuery, true),
          loadFilesProgress(),
        ])
      }
    } catch (error) {
 
    } finally {
      setIsUploading(false)
      // 清空文件输入
      event.target.value = ""
    }
  }

  // 澶勭悊鍒犻櫎鏂囦欢
  const handleDeleteFile = async () => {
    if (!fileToDelete) return

    try {
      setIsDeleting(true)
      const response = await deleteFileWithToast(datasetId, fileToDelete.id)

      if (response.code === 200) {
        setPendingFiles(prev => prev.filter(item => item.id !== fileToDelete.id))
        setOptimisticFiles(prev => prev.filter(item => item.id !== fileToDelete.id))
        setFiles(prev => prev.filter(item => item.id !== fileToDelete.id))
        // 閲嶆柊鍔犺浇鏂囦欢鍒楄〃鍜屾暟鎹泦淇℃伅
        loadFiles(pageData.current, debouncedQuery)
        loadDatasetDetail()
      }
    } catch (error) {
 
    } finally {
      setIsDeleting(false)
      setFileToDelete(null)
    }
  }

  // 鍒嗛〉澶勭悊
  const handlePageChange = (page: number) => {
    if (page < 1 || page > pageData.pages) return
    loadFiles(page, debouncedQuery)
  }

  // 鐢熸垚鍒嗛〉鏁板瓧
  const generatePageNumbers = () => {
    const pages: (number | string)[] = []
    const current = pageData.current
    const total = pageData.pages

    if (total <= 7) {
      for (let i = 1; i <= total; i++) {
        pages.push(i)
      }
    } else {
      if (current <= 4) {
        for (let i = 1; i <= 5; i++) {
          pages.push(i)
        }
        pages.push('...')
        pages.push(total)
      } else if (current >= total - 3) {
        pages.push(1)
        pages.push('...')
        for (let i = total - 4; i <= total; i++) {
          pages.push(i)
        }
      } else {
        pages.push(1)
        pages.push('...')
        for (let i = current - 1; i <= current + 1; i++) {
          pages.push(i)
        }
        pages.push('...')
        pages.push(total)
      }
    }

    return pages
  }

  // 鑾峰彇鏂囦欢鍥炬爣
  const getFileIcon = (contentType: string, ext: string) => {
    if (contentType.startsWith('image/')) {
      return <ImageIcon className="h-4 w-4" />
    } else if (contentType.startsWith('video/')) {
      return <Video className="h-4 w-4" />
    } else if (ext === 'pdf' || contentType === 'application/pdf') {
      return <FileText className="h-4 w-4" />
    } else {
      return <File className="h-4 w-4" />
    }
  }

  // 鏍煎紡鍖栨枃浠跺ぇ灏?
  const formatFileSize = (bytes: number) => {
    if (bytes === 0) return '0 B'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  }

  // 鑾峰彇鏂囦欢鐘舵€侀厤缃紙浣跨敤鏂扮殑缁熶竴鐘舵€侀€昏緫锛?
  const getFileStatusDisplay = (file: FileDetail) => {
    const progressInfo = getFileProgressInfo(file.id)
    return getFileStatusInfo(file, progressInfo)
  }

  // 鏍煎紡鍖栨椂闂?
  const formatDate = (dateString: string) => {
    return new Date(dateString).toLocaleString('zh-CN')
  }

  // 娓呴櫎鎼滅储
  const clearSearch = () => {
    setSearchQuery("")
  }

  // ========== 鏂板鏂规硶锛氭枃浠跺鐞嗚繘搴︾浉鍏?==========

  // 鍚姩鏂囦欢棰勫鐞?
  const handleProcessFile = async (fileId: string, processType: ProcessType) => {
    try {
      setIsProcessing(prev => ({ ...prev, [fileId]: true }))
      
      const response = await processFileWithToast({
        fileId,
        datasetId,
        processType
      })
      
      if (response.code === 200) {
        // 绔嬪嵆鍒锋柊杩涘害
        setTimeout(() => {
          loadFilesProgress()
        }, 1000)
        
        // 閲嶆柊鍔犺浇鏂囦欢鍒楄〃浠ユ洿鏂扮姸鎬?
        setTimeout(() => {
          loadFiles(pageData.current, debouncedQuery)
        }, 2000)
      }
    } catch (error) {
 
    } finally {
      setIsProcessing(prev => ({ ...prev, [fileId]: false }))
    }
  }
  
  // 杩欎簺鍑芥暟宸茬粡琚柊鐨勭粺涓€鐘舵€侀€昏緫鏇夸唬锛屼笉鍐嶉渶瑕?

  // 鑾峰彇鏂囦欢澶勭悊杩涘害淇℃伅
  const handleRetryFile = async (fileId: string, processType: ProcessType) => {
    try {
      setIsProcessing(prev => ({ ...prev, [fileId]: true }))

      const response = await reprocessFileWithToast({
        fileId,
        datasetId,
        processType
      })

      if (response.code === 200) {
        forceSyncUntilRef.current = Date.now() + UPLOAD_FORCE_SYNC_DURATION
        lastFileListSyncAtRef.current = 0
        await Promise.all([
          loadFilesProgress(),
          loadFiles(pageData.current, debouncedQuery, true),
        ])
      }
    } catch (error) {
 
    } finally {
      setIsProcessing(prev => ({ ...prev, [fileId]: false }))
    }
  }

  const handleImportByUrl = async () => {
    const trimmedUrl = remoteFileUrl.trim()
    if (!trimmedUrl) {
      toast({
        title: "请输入文件链接",
        variant: "destructive",
      })
      return
    }

    try {
      setIsUploading(true)

      const pendingName = remoteFileName.trim() || inferRemoteFilename(trimmedUrl)
      const pendingFile = createVirtualPendingFile(pendingName)
      insertPendingFile(pendingFile)

      const response = await importFileByUrl({
        datasetId,
        url: trimmedUrl,
        filename: remoteFileName.trim() || undefined,
      })

      if (response.code === 200 && response.data) {
        resolvePendingFile(pendingFile.id, response.data)
        setRemoteFileUrl("")
        setRemoteFileName("")
        forceSyncUntilRef.current = Date.now() + UPLOAD_FORCE_SYNC_DURATION
        lastFileListSyncAtRef.current = 0
        await Promise.all([
          loadFiles(pageData.current, debouncedQuery, true),
          loadFilesProgress(),
        ])
        toast({
          title: "导入成功",
          description: `${response.data.originalFilename} 已开始处理`,
        })
        return
      }

      if (shouldKeepPendingFile(response.message)) {
        keepPendingFileAlive(pendingFile.id)
      } else {
        removePendingFile(pendingFile.id)
      }
      toast({
        title: "导入失败",
        description: normalizeUploadError(response.message, pendingName),
        variant: "destructive",
      })
    } catch (error) {
      toast({
        title: "导入失败",
        description: error instanceof Error ? error.message : "导入失败，请稍后重试",
        variant: "destructive",
      })
    } finally {
      setIsUploading(false)
    }
  }

  const getFileProgressInfo = (fileId: string) => {
    return filesProgress.find(progress => progress.fileId === fileId)
  }

  const isProgressStillActive = (progress: FileProcessProgressDTO) => {
    if (!progress.processingStatusEnum) {
      return true
    }

    if (
      progress.processingStatusEnum === "COMPLETED" ||
      progress.processingStatusEnum === "EMBEDDING_FAILED" ||
      progress.processingStatusEnum === "OCR_FAILED"
    ) {
      return false
    }

    if (progress.processProgress === undefined) {
      return true
    }

    return progress.processProgress < 100
  }

  const createPendingFile = (file: File): FileDetail => {
    const now = new Date().toISOString()
    const ext = file.name.includes(".") ? file.name.split(".").pop() || "" : ""

    return {
      id: `pending-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      url: "",
      size: file.size,
      filename: file.name,
      originalFilename: file.name,
      ext,
      contentType: file.type || "application/octet-stream",
      dataSetId: datasetId,
      filePageSize: 0,
      isInitialize: 0,
      isEmbedding: 0,
      userId: dataset?.userId || "",
      createdAt: now,
      updatedAt: now,
    }
  }

  const createVirtualPendingFile = (filename: string): FileDetail => {
    const now = new Date().toISOString()
    const ext = filename.includes(".") ? filename.split(".").pop() || "" : ""

    return {
      id: `pending-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      url: "",
      size: 0,
      filename,
      originalFilename: filename,
      ext,
      contentType: "application/octet-stream",
      dataSetId: datasetId,
      filePageSize: 0,
      isInitialize: 0,
      isEmbedding: 0,
      userId: dataset?.userId || "",
      createdAt: now,
      updatedAt: now,
    }
  }

  const insertPendingFile = (file: FileDetail) => {
    setPendingFiles(prev => {
      if (prev.some(item => item.id === file.id)) {
        return prev
      }
      return [file, ...prev]
    })
  }

  const resolvePendingFile = (pendingId: string, file: FileDetail) => {
    setPendingFiles(prev => prev.filter(item => item.id !== pendingId))
    setOptimisticFiles(prev => {
      if (prev.some(item => getFileFingerprint(item) === getFileFingerprint(file))) {
        return prev
      }
      return [file, ...prev]
    })
  }

  const keepPendingFileAlive = (fileId: string) => {
    setPendingFiles(prev =>
      prev.map(item => (item.id === fileId ? { ...item, updatedAt: new Date().toISOString() } : item))
    )
  }

  const removePendingFile = (fileId: string) => {
    setPendingFiles(prev => prev.filter(item => item.id !== fileId))
  }

  // 鑾峰彇鐘舵€佸浘鏍?
  const getStatusIcon = (iconType: string) => {
    switch (iconType) {
      case "check":
        return <CheckCircle className="h-3 w-3" />
      case "clock":
        return <Clock className="h-3 w-3" />
      case "alert":
        return <AlertCircle className="h-3 w-3" />
      case "loading":
        return <Loader2 className="h-3 w-3 animate-spin" />
      default:
        return <Clock className="h-3 w-3" />
    }
  }

  // ========== 鏂板鏂规硶锛歊AG鎼滅储鐩稿叧 ==========

  // 鎵цRAG鎼滅储
  const handleRagSearch = async () => {
    if (!ragSearchQuery.trim()) {
      toast({
        title: "请输入搜索内容",
        variant: "destructive",
      })
      return
    }

    try {
      setIsRagSearching(true)
      setShowRagResults(true)
      
      const response = await ragSearchWithToast({
        datasetIds: [datasetId],
        question: ragSearchQuery.trim(),
        maxResults: 15
      })
      
      if (response.code === 200) {
        setSearchDocuments(response.data)
      } else {
        setSearchDocuments([])
      }
    } catch (error) {
 
      setSearchDocuments([])
    } finally {
      setIsRagSearching(false)
    }
  }

  // 娓呴櫎RAG鎼滅储
  const clearRagSearch = () => {
    setRagSearchQuery("")
    setSearchDocuments([])
    setShowRagResults(false)
  }

  if (loading) {
    return (
      <div className="container py-6">
        <div className="flex items-center gap-4 mb-6">
          <Skeleton className="h-10 w-10 rounded-md" />
          <div>
            <Skeleton className="h-8 w-48 mb-2" />
            <Skeleton className="h-4 w-32" />
          </div>
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          <div className="lg:col-span-1">
            <Card>
              <CardHeader>
                <Skeleton className="h-6 w-24" />
              </CardHeader>
              <CardContent className="space-y-4">
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="h-4 w-1/2" />
              </CardContent>
            </Card>
          </div>
          <div className="lg:col-span-3">
            <Card>
              <CardHeader>
                <Skeleton className="h-6 w-32" />
              </CardHeader>
              <CardContent>
                <Skeleton className="h-64 w-full" />
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    )
  }

  if (error || !dataset) {
    return (
      <div className="container py-6">
        <div className="text-center py-10">
          <AlertCircle className="h-12 w-12 mx-auto text-red-500 mb-4" />
          <div className="text-red-500 mb-4">{error || "数据集不存在"}</div>
          <div className="flex gap-2 justify-center">
            <Button variant="outline" onClick={() => router.back()}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              返回
            </Button>
            <Button variant="outline" onClick={loadDatasetDetail}>
              <RefreshCw className="mr-2 h-4 w-4" />
              重试
            </Button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="container py-6">
      {/* 面包屑导航 */}
      <div className="flex items-center gap-4 mb-6">
        <Button variant="ghost" size="icon" onClick={() => router.back()}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{dataset.name}</h1>
          <p className="text-muted-foreground">
            <Link href="/knowledge" className="hover:underline">知识库</Link>
            <span className="mx-2">/</span>
            <span>{dataset.name}</span>
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
        {/* 数据信息 */}
        <div className="lg:col-span-1">
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">数据信息</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <label className="text-sm font-medium text-muted-foreground">名称</label>
                <p className="text-sm">{dataset.name}</p>
              </div>
              
              {dataset.description && (
                <div>
                  <label className="text-sm font-medium text-muted-foreground">描述</label>
                  <p className="text-sm">{dataset.description}</p>
                </div>
              )}
              
              <div>
                <label className="text-sm font-medium text-muted-foreground">文件数量</label>
                <p className="text-sm">{displayedFileCount} 个文件</p>
              </div>
              
              <div>
                <label className="text-sm font-medium text-muted-foreground">创建时间</label>
                <p className="text-sm">{formatDate(dataset.createdAt)}</p>
              </div>
              
              <div>
                <label className="text-sm font-medium text-muted-foreground">更新时间</label>
                <p className="text-sm">{formatDate(dataset.updatedAt)}</p>
              </div>

              {/* RAG功能区 */}
              <div className="pt-4 border-t space-y-4">
                {/* RAG智能问答 */}
                <div>
                  <label className="text-sm font-medium text-muted-foreground mb-2 flex items-center gap-2">
                    <MessageSquare className="h-4 w-4" />
                    智能问答
                  </label>
                  <Button 
                    onClick={() => setShowRagChat(true)}
                    variant="outline"
                    className="w-full"
                  >
                    <MessageSquare className="mr-2 h-4 w-4" />
                    开始对话
                  </Button>
                </div>

                {/* RAG搜索 */}
                <div>
                  <label className="text-sm font-medium text-muted-foreground mb-2 flex items-center gap-2">
                    <FileSearch className="h-4 w-4" />
                    文档搜索
                  </label>
                  <div className="space-y-2">
                    <Textarea
                      placeholder="输入问题进行文档搜索..."
                      value={ragSearchQuery}
                      onChange={(e) => setRagSearchQuery(e.target.value)}
                      className="min-h-[80px] resize-none"
                    />
                    <div className="flex gap-2">
                      <Button 
                        onClick={handleRagSearch}
                        disabled={isRagSearching || !ragSearchQuery.trim()}
                        className="flex-1"
                        size="sm"
                      >
                        {isRagSearching ? (
                          <>
                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                            搜索中...
                          </>
                        ) : (
                          <>
                            <Search className="mr-2 h-4 w-4" />
                            搜索文档
                          </>
                        )}
                      </Button>
                      {ragSearchQuery && (
                        <Button 
                          variant="outline" 
                          size="sm"
                          onClick={clearRagSearch}
                        >
                          <X className="h-4 w-4" />
                        </Button>
                      )}
                    </div>
                  </div>
                </div>
              </div>

              {/* 文件上传 */}
              <div className="pt-4 border-t">
                <label htmlFor="file-upload" className="block">
                  <Button 
                    variant="outline" 
                    className="w-full" 
                    disabled={isUploading}
                    asChild
                  >
                    <span>
                      <Upload className="mr-2 h-4 w-4" />
                      {isUploading ? "上传中..." : "上传文件"}
                    </span>
                  </Button>
                </label>
                <input
                  id="file-upload"
                  type="file"
                  multiple
                  className="hidden"
                  onChange={handleFileUpload}
                  accept=".pdf,.doc,.docx,.txt,.md,.html,.json,.csv,.xlsx,.xls"
                />
                <p className="text-xs text-muted-foreground mt-2">
                  支持 PDF、Word、文本等格式，单文件最高 100MB
                </p>
                <div className="mt-3 space-y-2">
                  <Input
                    type="url"
                    placeholder="粘贴网络文件链接，例如 https://example.com/demo.pdf"
                    value={remoteFileUrl}
                    onChange={(e) => setRemoteFileUrl(e.target.value)}
                    disabled={isUploading}
                  />
                  <Input
                    placeholder="可选：自定义文件名"
                    value={remoteFileName}
                    onChange={(e) => setRemoteFileName(e.target.value)}
                    disabled={isUploading}
                  />
                  <Button
                    variant="secondary"
                    className="w-full"
                    disabled={isUploading || !remoteFileUrl.trim()}
                    onClick={handleImportByUrl}
                  >
                    <Upload className="mr-2 h-4 w-4" />
                    {isUploading ? "导入中..." : "网络导入"}
                  </Button>
                  <p className="text-xs text-muted-foreground">
                    导入后的文件会继续走同一套 OCR、切块和向量化链路
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* 文件列表 */}
        <div className="lg:col-span-3">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="text-lg">文件列表</CardTitle>
                <div className="flex items-center gap-2">
                  <div className="relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                    <Input
                      type="search"
                      placeholder="搜索文件..."
                      className="pl-10 pr-10 w-64"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                    />
                    {searchQuery && (
                      <Button
                        variant="ghost"
                        size="icon"
                        className="absolute right-1 top-1/2 -translate-y-1/2 h-7 w-7"
                        onClick={clearSearch}
                      >
                        <X className="h-4 w-4" />
                      </Button>
                    )}
                  </div>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {filesLoading ? (
                <div className="space-y-3">
                  {Array.from({ length: 5 }).map((_, index) => (
                    <div key={index} className="flex items-center gap-4 p-3 border rounded">
                      <Skeleton className="h-8 w-8" />
                      <div className="flex-1">
                        <Skeleton className="h-4 w-48 mb-2" />
                        <Skeleton className="h-3 w-32" />
                      </div>
                      <Skeleton className="h-6 w-16" />
                      <Skeleton className="h-6 w-16" />
                      <Skeleton className="h-8 w-8" />
                    </div>
                  ))}
                </div>
              ) : displayedFiles.length === 0 ? (
                <div className="text-center py-8">
                  <File className="h-12 w-12 mx-auto text-gray-400 mb-4" />
                  <h3 className="text-lg font-medium mb-2">
                    {searchQuery ? "未找到匹配的文件" : "还没有上传任何文件"}
                  </h3>
                  <p className="text-muted-foreground mb-4">
                    {searchQuery ? "尝试使用不同的搜索词" : "上传文件开始构建您的知识库"}
                  </p>
                </div>
              ) : (
                <>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="w-12"></TableHead>
                        <TableHead>文件名</TableHead>
                        <TableHead>大小</TableHead>
                        <TableHead>处理状态</TableHead>
                        <TableHead>处理进度</TableHead>
                        <TableHead>上传时间</TableHead>
                        <TableHead className="w-20">操作</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {displayedFiles.map((file) => {
                        const fileStatusDisplay = getFileStatusDisplay(file)
                        const progressInfo = getFileProgressInfo(file.id)
                        const processing = isProcessing[file.id]
                        const canRetryOcr = progressInfo?.processingStatusEnum === "OCR_FAILED"
                        const canRetryEmbedding = progressInfo?.processingStatusEnum === "EMBEDDING_FAILED"
                        
                        return (
                          <TableRow key={file.id}>
                            <TableCell>
                              {getFileIcon(file.contentType, file.ext)}
                            </TableCell>
                            <TableCell>
                              <div>
                                <p className="font-medium">{file.originalFilename}</p>
                                <p className="text-xs text-muted-foreground">{file.ext.toUpperCase()}</p>
                              </div>
                            </TableCell>
                            <TableCell className="text-sm">
                              {formatFileSize(file.size)}
                            </TableCell>
                            <TableCell>
                              <div className="flex items-center gap-1">
                                {getStatusIcon(fileStatusDisplay.status.iconType)}
                                <Badge 
                                  variant={fileStatusDisplay.status.variant}
                                  className={`text-xs ${fileStatusDisplay.status.color}`}
                                >
                                  {fileStatusDisplay.status.text}
                                </Badge>
                              </div>
                            </TableCell>
                            <TableCell>
                              {progressInfo && progressInfo.processProgress !== undefined ? (
                                <div className="space-y-1">
                                  <div className="flex items-center justify-between">
                                    <span className="text-xs text-muted-foreground">
                                      {Math.round(progressInfo.processProgress)}%
                                    </span>
                                    {progressInfo.currentPageNumber && progressInfo.filePageSize && (
                                      <span className="text-xs text-muted-foreground">
                                        {progressInfo.currentPageNumber}/{progressInfo.filePageSize}
                                      </span>
                                    )}
                                  </div>
                                  <Progress value={progressInfo.processProgress} className="h-2" />
                                  {progressInfo.statusDescription && (
                                    <p className="text-xs text-muted-foreground">
                                      {progressInfo.statusDescription}
                                    </p>
                                  )}
                                </div>
                              ) : (
                                <span className="text-xs text-muted-foreground">-</span>
                              )}
                            </TableCell>
                            <TableCell className="text-sm">
                              {formatDate(file.createdAt)}
                            </TableCell>
                            <TableCell>
                              <div className="flex items-center gap-1">
                                
                                {(fileStatusDisplay.status.text === "处理完成" || fileStatusDisplay.status.text === "解析完成") && (
                                  <Button
                                    variant="ghost"
                                    size="icon"
                                    className="h-8 w-8"
                                    onClick={() => setSelectedFileForUnits(file)}
                                    title="查看语料"
                                  >
                                    <FileText className="h-4 w-4" />
                                  </Button>
                                )}
                                {(canRetryOcr || canRetryEmbedding) && (
                                  <Button
                                    variant="ghost"
                                    size="icon"
                                    className="h-8 w-8"
                                    disabled={processing}
                                    onClick={() =>
                                      handleRetryFile(
                                        file.id,
                                        canRetryOcr ? ProcessType.INITIALIZE : ProcessType.EMBEDDING
                                      )
                                    }
                                    title={canRetryOcr ? "重试OCR处理" : "重试向量化处理"}
                                  >
                                    {processing ? (
                                      <Loader2 className="h-4 w-4 animate-spin" />
                                    ) : (
                                      <RefreshCw className="h-4 w-4" />
                                    )}
                                  </Button>
                                )}
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  className="h-8 w-8"
                                  disabled={!file.url}
                                  onClick={() => window.open(file.url, '_blank')}
                                  title="下载文件"
                                >
                                  <Download className="h-4 w-4" />
                                </Button>
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  className="h-8 w-8 text-red-600 hover:text-red-700"
                                  onClick={() => setFileToDelete(file)}
                                  title="删除文件"
                                >
                                  <Trash className="h-4 w-4" />
                                </Button>
                              </div>
                            </TableCell>
                          </TableRow>
                        )
                      })}
                    </TableBody>
                  </Table>

                  {/* 分页 */}
                  {pageData.pages > 1 && (
                    <div className="flex justify-center mt-6">
                      <Pagination>
                        <PaginationContent>
                          <PaginationItem>
                            <PaginationPrevious 
                              onClick={() => handlePageChange(pageData.current - 1)}
                              className={pageData.current <= 1 ? "pointer-events-none opacity-50" : "cursor-pointer"}
                            />
                          </PaginationItem>
                          
                          {generatePageNumbers().map((page, index) => (
                            <PaginationItem key={index}>
                              {page === '...' ? (
                                <PaginationEllipsis />
                              ) : (
                                <PaginationLink
                                  onClick={() => handlePageChange(page as number)}
                                  isActive={page === pageData.current}
                                  className="cursor-pointer"
                                >
                                  {page}
                                </PaginationLink>
                              )}
                            </PaginationItem>
                          ))}
                          
                          <PaginationItem>
                            <PaginationNext 
                              onClick={() => handlePageChange(pageData.current + 1)}
                              className={pageData.current >= pageData.pages ? "pointer-events-none opacity-50" : "cursor-pointer"}
                            />
                          </PaginationItem>
                        </PaginationContent>
                      </Pagination>
                    </div>
                  )}
                </>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      {/* RAG搜索结果对话框 */}
      <Dialog open={showRagResults} onOpenChange={(open) => !open && clearRagSearch()}>
        <DialogContent className="max-w-4xl max-h-[80vh]">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <BookOpen className="h-5 w-5" />
              搜索结果
            </DialogTitle>
            <DialogDescription>
              针对问题 &quot;{ragSearchQuery}&quot; 的文档搜索结果
            </DialogDescription>
          </DialogHeader>
          
          <div className="max-h-[60vh] overflow-y-auto">
            {isRagSearching ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-8 w-8 animate-spin" />
                <span className="ml-2">搜索中...</span>
              </div>
            ) : searchDocuments.length === 0 ? (
              <div className="text-center py-8">
                <FileSearch className="h-12 w-12 mx-auto text-gray-400 mb-4" />
                <h3 className="text-lg font-medium mb-2">未找到相关文档</h3>
                <p className="text-muted-foreground">
                  尝试使用不同的关键词或检查文档是否已完成向量化处理
                </p>
              </div>
            ) : (
              <div className="space-y-4">
                {searchDocuments.map((doc, index) => (
                  <Card key={doc.id} className="p-4">
                    <div className="flex items-start justify-between mb-2">
                      <div className="flex items-center gap-2">
                        <Badge variant="outline" className="text-xs">
                          第 {doc.sourcePage ?? doc.page} 页
                        </Badge>
                        <Badge variant={doc.isVector ? "default" : "secondary"} className="text-xs">
                          {doc.isVector ? "已向量化" : "未向量化"}
                        </Badge>
                        {doc.isOcr && (
                          <Badge variant="outline" className="text-xs">
                            OCR处理
                          </Badge>
                        )}
                        {doc.segmentType && (
                          <Badge variant="outline" className="text-xs uppercase">
                            {doc.segmentType}
                          </Badge>
                        )}
                      </div>
                      <div className="text-right text-xs text-muted-foreground">
                        <div>#{index + 1}</div>
                        {typeof doc.similarityScore === "number" && (
                          <div>相关度 {doc.similarityScore.toFixed(3)}</div>
                        )}
                      </div>
                    </div>
                    <div className="mb-2 space-y-1">
                      {doc.fileName && (
                        <div className="text-sm font-medium">{doc.fileName}</div>
                      )}
                      {doc.titlePath && (
                        <div className="text-xs text-muted-foreground">{doc.titlePath}</div>
                      )}
                    </div>
                    <div className="text-sm leading-relaxed">
                      {doc.content.length > 500 
                        ? `${doc.content.substring(0, 500)}...` 
                        : doc.content}
                    </div>
                    <div className="flex items-center justify-between mt-3 pt-3 border-t">
                      <span className="text-xs text-muted-foreground">
                        文档ID: {doc.fileId}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {formatDate(doc.updatedAt)}
                      </span>
                    </div>
                  </Card>
                ))}
              </div>
            )}
          </div>
          
          <DialogFooter>
            <Button variant="outline" onClick={clearRagSearch}>
              关闭
            </Button>
            {searchDocuments.length > 0 && (
              <Button onClick={() => {
                // 可扩展导出功能
                toast({
                  title: "搜索完成",
                  description: `找到 ${searchDocuments.length} 个相关文档片段`,
                })
              }}>
                <Download className="mr-2 h-4 w-4" />
                导出结果
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 删除文件确认对话框 */}
      <Dialog open={!!fileToDelete} onOpenChange={(open) => !open && setFileToDelete(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>确认删除</DialogTitle>
            <DialogDescription>
              您确定要删除文件 &quot;{fileToDelete?.originalFilename}&quot; 吗？此操作无法撤销。
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setFileToDelete(null)}>
              取消
            </Button>
            <Button variant="destructive" onClick={handleDeleteFile} disabled={isDeleting}>
              {isDeleting ? "删除中..." : "确认删除"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* RAG聊天对话框 */}
      {showRagChat && (
        <RagChatDialog
          open
          onOpenChange={setShowRagChat}
          dataset={dataset}
        />
      )}

      {/* 文档单元对话框 */}
      {selectedFileForUnits && (
        <DocumentUnitsDialog
          open={!!selectedFileForUnits}
          onOpenChange={(open) => !open && setSelectedFileForUnits(null)}
          file={selectedFileForUnits}
        />
      )}
    </div>
  )
}
