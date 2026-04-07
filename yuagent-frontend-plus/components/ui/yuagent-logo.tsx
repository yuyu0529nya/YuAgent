"use client"

import { cn } from "@/lib/utils"

interface YuAgentLogoProps {
  className?: string
}

export function YuAgentLogo({ className }: YuAgentLogoProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      aria-hidden="true"
      className={cn("shrink-0", className)}
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
    >
      <defs>
        <linearGradient id="yuagent-logo-bg" x1="2.5" y1="2.5" x2="21.5" y2="21.5" gradientUnits="userSpaceOnUse">
          <stop stopColor="#815DFF" />
          <stop offset="0.55" stopColor="#6D79FF" />
          <stop offset="1" stopColor="#48C4FF" />
        </linearGradient>
        <linearGradient id="yuagent-logo-orbit" x1="5" y1="5" x2="19" y2="19" gradientUnits="userSpaceOnUse">
          <stop stopColor="#FFFFFF" stopOpacity="0.85" />
          <stop offset="1" stopColor="#C8DAFF" stopOpacity="0.55" />
        </linearGradient>
        <linearGradient id="yuagent-logo-y" x1="9" y1="8" x2="15.5" y2="17" gradientUnits="userSpaceOnUse">
          <stop stopColor="#FFFFFF" />
          <stop offset="1" stopColor="#E2ECFF" />
        </linearGradient>
        <linearGradient id="yuagent-logo-u" x1="8.3" y1="12.4" x2="15.2" y2="16.8" gradientUnits="userSpaceOnUse">
          <stop stopColor="#DDF0FF" />
          <stop offset="1" stopColor="#FFFFFF" />
        </linearGradient>
      </defs>

      <rect x="2.5" y="2.5" width="19" height="19" rx="6" fill="url(#yuagent-logo-bg)" />
      <circle cx="12" cy="12" r="5.9" stroke="url(#yuagent-logo-orbit)" strokeWidth="1.2" />
      <path d="M7.1 14.4C8.7 16.5 11.1 17.8 13.8 17.8C16.3 17.8 18.5 16.7 20.1 14.9" stroke="url(#yuagent-logo-orbit)" strokeWidth="1.1" strokeLinecap="round" />
      <circle cx="18.65" cy="14.75" r="1.05" fill="#FF7FCF" />
      <path d="M9.1 8.3L12 12.2L14.9 8.3" stroke="url(#yuagent-logo-y)" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M12 12.2V15.8" stroke="url(#yuagent-logo-y)" strokeWidth="1.7" strokeLinecap="round" />
      <path d="M8.6 12.9V14.1C8.6 15.3 9.5 16.1 10.7 16.1C11.8 16.1 12.7 15.3 12.7 14.1V12.9" stroke="url(#yuagent-logo-u)" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
