import { useCallback, useEffect, useRef, useState } from 'react';
import { toMarketTool } from '@/lib/market-tool-mapper';
import { Tool } from '@/types/tool';
import { getRecommendToolsWithToast } from '@/lib/tool-service';

export function useRecommendTools(limit?: number) {
  const [tools, setTools] = useState<Tool[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const latestRequestIdRef = useRef(0);

  const fetchRecommendTools = useCallback(async () => {
    const requestId = ++latestRequestIdRef.current;
    setLoading(true);
    setError(null);

    try {
      const response = await getRecommendToolsWithToast();
      if (requestId !== latestRequestIdRef.current) {
        return;
      }

      if (response.code === 200) {
        const toolsList = response.data
          .map(toMarketTool)
          .slice(0, limit && limit > 0 ? limit : undefined);
        setTools(toolsList);
      } else {
        setError(response.message);
      }
    } catch (error) {
      if (requestId === latestRequestIdRef.current) {
        setError(error instanceof Error ? error.message : "未知错误");
      }
    } finally {
      if (requestId === latestRequestIdRef.current) {
        setLoading(false);
      }
    }
  }, [limit]);

  useEffect(() => {
    void fetchRecommendTools();
  }, [fetchRecommendTools]);

  return {
    tools,
    loading,
    error,
    fetchRecommendTools,
  };
} 
