"use client"

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type Dispatch,
  type ReactNode,
  type SetStateAction,
} from "react"

type WorkspaceContextType = {
  selectedWorkspaceId: string | null
  selectedConversationId: string | null
  setSelectedWorkspaceId: (id: string | null) => void
  setSelectedConversationId: Dispatch<SetStateAction<string | null>>
  refreshWorkspace: () => void
  refreshTrigger: number
}

const WorkspaceContext = createContext<WorkspaceContextType | undefined>(undefined)

export function WorkspaceProvider({ children }: { children: ReactNode }) {
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string | null>(null)
  const [selectedConversationId, setSelectedConversationId] = useState<string | null>(null)
  const [refreshTrigger, setRefreshTrigger] = useState(0)
  const selectedWorkspaceIdRef = useRef(selectedWorkspaceId)

  useEffect(() => {
    selectedWorkspaceIdRef.current = selectedWorkspaceId
  }, [selectedWorkspaceId])

  const selectWorkspace = useCallback((workspaceId: string | null) => {
    if (selectedWorkspaceIdRef.current !== workspaceId) {
      setSelectedConversationId(null)
    }
    setSelectedWorkspaceId(workspaceId)
  }, [])

  const refreshWorkspace = useCallback(() => {
    setRefreshTrigger(prev => prev + 1)
  }, [])

  const value = useMemo(() => ({
    selectedWorkspaceId,
    selectedConversationId,
    setSelectedWorkspaceId: selectWorkspace,
    setSelectedConversationId,
    refreshWorkspace,
    refreshTrigger,
  }), [selectedWorkspaceId, selectedConversationId, selectWorkspace, refreshWorkspace, refreshTrigger])

  return (
    <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>
  )
}

export function useWorkspace() {
  const context = useContext(WorkspaceContext)
  if (context === undefined) {
    throw new Error("useWorkspace must be used within a WorkspaceProvider")
  }
  return context
}

