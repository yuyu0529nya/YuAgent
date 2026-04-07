"use client";

import React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import {
  Users,
  Wrench,
  Bot,
  Server,
  Settings,
  Home,
  Shield,
  Container,
  CreditCard,
  BookOpen,
  Database,
  Package,
} from "lucide-react";

interface MenuItemProps {
  href: string;
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  isActive: boolean;
}

function MenuItem({ href, icon: Icon, label, isActive }: MenuItemProps) {
  return (
    <Link
      href={href}
      className={cn(
        "group flex items-center rounded-xl border px-4 py-3 text-sm font-medium transition-all",
        isActive
          ? "border-white/20 bg-white/10 text-white"
          : "border-transparent text-gray-400 hover:border-white/10 hover:bg-white/5 hover:text-white",
      )}
    >
      <Icon className={cn("mr-3 h-5 w-5", isActive ? "text-white" : "text-gray-500 group-hover:text-gray-300")} />
      {label}
    </Link>
  );
}

export function AdminSidebar() {
  const pathname = usePathname();

  const menuItems = [
    { href: "/admin", icon: Home, label: "管理首页" },
    { href: "/admin/users", icon: Users, label: "用户列表" },
    { href: "/admin/tools", icon: Wrench, label: "工具列表" },
    { href: "/admin/agents", icon: Bot, label: "Agent 列表" },
    { href: "/admin/rags", icon: Database, label: "RAG 管理" },
    { href: "/admin/providers", icon: Server, label: "服务商管理" },
    { href: "/admin/containers", icon: Container, label: "容器管理" },
    { href: "/admin/products", icon: CreditCard, label: "商品管理" },
    { href: "/admin/orders", icon: Package, label: "订单管理" },
    { href: "/admin/rules", icon: BookOpen, label: "规则管理" },
    { href: "/admin/auth-settings", icon: Shield, label: "认证配置" },
  ];

  return (
    <div className="flex h-screen w-64 flex-col border-r border-white/10 bg-[#0b0f17] shadow-2xl">
      <div className="flex shrink-0 items-center border-b border-white/10 p-6">
        <div className="flex h-8 w-8 items-center justify-center rounded-lg border border-white/15 bg-white/10">
          <Settings className="h-5 w-5 text-white" />
        </div>
        <span className="ml-3 text-lg font-semibold text-white">YuAgent Admin</span>
      </div>

      <nav className="flex-1 space-y-1 overflow-y-auto p-4">
        {menuItems.map((item) => (
          <MenuItem
            key={item.href}
            href={item.href}
            icon={item.icon}
            label={item.label}
            isActive={
              pathname === item.href ||
              (item.href === "/admin" && pathname === "/admin") ||
              (item.href !== "/admin" && pathname.startsWith(item.href))
            }
          />
        ))}
      </nav>

      <div className="shrink-0 border-t border-white/10 p-4 text-center text-xs text-gray-400">
        YuAgent 管理后台
        <br />
        v1.0.0
      </div>
    </div>
  );
}
