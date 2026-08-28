"use client"

import { use, useEffect } from "react"
import { useRouter } from "next/navigation"
import { Skeleton } from "@/components/ui/skeleton"
import { getMarketToolVersionsWithToast } from "@/lib/tool-service"

export default function ToolDetailRedirectPage({ params }: { params: Promise<{ id: string }> }) {
  const router = useRouter()
  const { id } = use(params)

  useEffect(() => {
    let cancelled = false

    async function redirectToNewFormat() {
      try {
        // 市场详情页由 toolId + version 唯一标识。旧链接只包含 toolId，
        // 因此只需查询版本列表并跳转到最新版本。
        const versionsResponse = await getMarketToolVersionsWithToast(id)
        if (!cancelled && versionsResponse.code === 200 && versionsResponse.data.length > 0) {
          router.replace(`/tools/${id}/${versionsResponse.data[0].version}`)
          return
        }

        if (!cancelled) {
          router.replace('/tools-market')
        }
      } catch (error) {
        if (!cancelled) {
          router.replace('/tools-market')
        }
      }
    }

    void redirectToNewFormat()
    return () => {
      cancelled = true
    }
  }, [id, router])

  // 显示加载状态
  return (
    <div className="container py-6">
      <div className="text-center py-20">
        <Skeleton className="h-8 w-64 mx-auto mb-4" />
        <Skeleton className="h-4 w-96 mx-auto mb-2" />
        <Skeleton className="h-4 w-80 mx-auto" />
        <p className="mt-8 text-muted-foreground">正在加载工具详情...</p>
            </div>
    </div>
  )
} 
