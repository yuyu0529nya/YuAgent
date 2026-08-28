import type React from "react"

/** Widget 页面由根布局提供主题与通知，避免嵌套全局 Provider。 */
export default function WidgetLayout({ children }: { children: React.ReactNode }) {
  return children
}
