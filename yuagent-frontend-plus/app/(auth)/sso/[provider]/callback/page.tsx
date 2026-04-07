"use client"

import { use, useEffect, useState } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { toast } from "@/hooks/use-toast"
import { handleSsoCallbackApi } from "@/lib/api-services"
import { AccountService } from "@/lib/account-service"
import { setCookie } from "@/lib/utils"

interface SsoCallbackPageProps {
  params: Promise<{ provider: string }>
}

export default function SsoCallbackPage({ params }: SsoCallbackPageProps) {
  const router = useRouter()
  const searchParams = useSearchParams()
  const { provider } = use(params)
  const [status, setStatus] = useState("处理中...")
  const [isProcessing, setIsProcessing] = useState(false)

  const getProviderName = (providerCode: string) => {
    if (providerCode === "github") {
      return "GitHub"
    }
    return providerCode.toUpperCase()
  }

  useEffect(() => {
    if (isProcessing) {
      return
    }

    const handleCallback = async () => {
      setIsProcessing(true)

      try {
        const code = searchParams.get("code")
        if (!code) {
          toast({
            variant: "destructive",
            title: "登录失败",
            description: "未获取到授权码",
          })
          router.push("/login")
          return
        }

        setStatus("验证授权码...")
        const res = await handleSsoCallbackApi(provider, code)

        if (res.code === 200 && res.data?.token) {
          setStatus("保存认证信息...")
          localStorage.setItem("auth_token", res.data.token)
          setCookie("token", res.data.token, 30)
          await new Promise((resolve) => setTimeout(resolve, 300))

          setStatus("验证登录状态...")
          try {
            const accountRes = await AccountService.getCurrentUserAccount()
            if (accountRes.code === 200 || accountRes.code === 401) {
              toast({
                title: "登录成功",
                description: `使用 ${getProviderName(provider)} 登录成功`,
              })
              router.push("/")
              return
            }
          } catch {
            // token 已拿到，继续跳首页
          }

          toast({
            title: "登录成功",
            description: `使用 ${getProviderName(provider)} 登录成功`,
          })
          router.push("/")
          return
        }

        toast({
          variant: "destructive",
          title: "登录失败",
          description: res.message || "登录过程中出现错误",
        })
        router.push("/login")
      } catch (error) {
        let errorMessage = "登录过程中出现错误，请重试"
        if (error instanceof Error) {
          errorMessage = error.message.includes("401") ? "认证失败，请重新登录" : error.message
        }

        toast({
          variant: "destructive",
          title: "登录失败",
          description: errorMessage,
        })
        localStorage.removeItem("auth_token")
        document.cookie = "token=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;"
        router.push("/login")
      } finally {
        setIsProcessing(false)
      }
    }

    handleCallback()
  }, [isProcessing, provider, router, searchParams])

  return (
    <div className="flex items-center justify-center min-h-screen">
      <div className="text-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary mx-auto mb-4" />
        <p className="text-muted-foreground">正在处理登录...</p>
        <p className="text-sm text-muted-foreground/70 mt-2">{status}</p>
      </div>
    </div>
  )
}
