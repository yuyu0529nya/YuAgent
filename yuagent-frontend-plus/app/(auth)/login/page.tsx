"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"

import { Toaster } from "@/components/ui/toaster"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { toast } from "@/hooks/use-toast"
import { getAuthConfigWithToast } from "@/lib/auth-config-service"
import { loginApi } from "@/lib/api-services"
import type { AuthConfig } from "@/lib/types/auth-config"
import { AUTH_FEATURE_KEY } from "@/lib/types/auth-config"
import { setCookie } from "@/lib/utils"

export default function LoginPage() {
  const router = useRouter()
  const [formData, setFormData] = useState({ account: "", password: "" })
  const [loading, setLoading] = useState(false)
  const [authConfig, setAuthConfig] = useState<AuthConfig | null>(null)
  const [configLoading, setConfigLoading] = useState(true)
  const [configError, setConfigError] = useState<string | null>(null)

  useEffect(() => {
    async function fetchAuthConfig() {
      try {
        const response = await getAuthConfigWithToast()
        if (response.code === 200) {
          setAuthConfig(response.data)
        } else {
          setConfigError(response.message || "无法加载登录配置")
        }
      } catch (error) {
        setConfigError(error instanceof Error ? error.message : "无法加载登录配置")
      } finally {
        setConfigLoading(false)
      }
    }

    fetchAuthConfig()
  }, [])

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({ ...prev, [name]: value }))
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)

    try {
      const { account, password } = formData

      if (!account || !password) {
        toast({
          variant: "destructive",
          title: "错误",
          description: "请输入账号和密码",
        })
        return
      }

      const res = await loginApi({ account, password }, true)
      if (res.code === 200 && res.data?.token) {
        localStorage.setItem("auth_token", res.data.token)
        setCookie("token", res.data.token, 30)
        router.push("/")
      }
    } finally {
      setLoading(false)
    }
  }

  if (configLoading) {
    return (
      <div className="container flex h-screen max-w-[400px] flex-col justify-center py-10">
        <div className="mb-8 space-y-2 text-center">
          <div className="h-8 animate-pulse rounded bg-gray-200" />
          <div className="h-4 animate-pulse rounded bg-gray-200" />
        </div>
        <div className="space-y-4">
          <div className="h-20 animate-pulse rounded bg-gray-200" />
          <div className="h-20 animate-pulse rounded bg-gray-200" />
          <div className="h-10 animate-pulse rounded bg-gray-200" />
        </div>
      </div>
    )
  }

  const availableLoginMethods = authConfig?.loginMethods || {}
  const hasNormalLogin = availableLoginMethods[AUTH_FEATURE_KEY.NORMAL_LOGIN]?.enabled

  if (configError) {
    return (
      <div className="container flex h-screen max-w-[400px] flex-col justify-center py-10">
        <div className="space-y-4 text-center">
          <h1 className="text-2xl font-semibold tracking-tight">登录服务暂不可用</h1>
          <p className="text-sm text-muted-foreground">{configError}</p>
        </div>
      </div>
    )
  }

  if (!hasNormalLogin) {
    return (
      <div className="container flex h-screen max-w-[400px] flex-col justify-center py-10">
        <div className="space-y-4 text-center">
          <h1 className="text-2xl font-semibold tracking-tight">暂时无法登录</h1>
          <p className="text-sm text-muted-foreground">
            系统暂时关闭了账号密码登录，请稍后再试或联系管理员。
          </p>
        </div>
      </div>
    )
  }

  return (
    <>
      <div className="container flex h-screen max-w-[400px] flex-col justify-center py-10">
        <div className="mb-8 space-y-2 text-center">
          <h1 className="text-2xl font-semibold tracking-tight">登录</h1>
          <p className="text-sm text-muted-foreground">欢迎回来，请输入您的账号信息。</p>
        </div>

        <div className="space-y-4">
          <form onSubmit={handleSubmit}>
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="account">
                  账号 <span className="text-red-500">*</span>
                </Label>
                <Input
                  id="account"
                  name="account"
                  type="text"
                  placeholder="请输入账号、邮箱或手机号"
                  value={formData.account}
                  onChange={handleChange}
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="password">
                  密码 <span className="text-red-500">*</span>
                </Label>
                <Input
                  id="password"
                  name="password"
                  type="password"
                  placeholder="请输入密码"
                  value={formData.password}
                  onChange={handleChange}
                  required
                />
              </div>

              <Button
                type="submit"
                className="w-full bg-primary text-primary-foreground hover:bg-primary/90"
                disabled={loading}
              >
                {loading ? "登录中..." : "登录"}
              </Button>
            </div>
          </form>

          <div className="mb-2 mt-2 flex justify-between text-sm text-muted-foreground">
            <div>
              {authConfig?.registerEnabled && (
                <>
                  还没有账号？{" "}
                  <Link href="/register" className="text-primary hover:underline">
                    立即注册
                  </Link>
                </>
              )}
            </div>

            <div>
              <Link href="/reset-password" className="text-primary hover:underline">
                忘记密码
              </Link>
            </div>
          </div>
        </div>
      </div>
      <Toaster />
    </>
  )
}
