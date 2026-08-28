"use client";

import { useEffect, useRef, useCallback } from "react";
import { toast } from "@/hooks/use-toast";

import { OrderStatusResponse, PollingCallbacks, PollingConfig } from "@/types/payment";
import { PaymentService } from "@/lib/payment-service";

interface PaymentStatusPollerProps {
  orderNo?: string;
  enabled?: boolean;
  config?: Partial<PollingConfig>;
  callbacks?: PollingCallbacks;
}

const DEFAULT_POLLING_CONFIG: PollingConfig = {
  maxDuration: 300000,
  interval: 3000,
};

export default function PaymentStatusPoller({
  orderNo,
  enabled = false,
  config = {},
  callbacks = {}
}: PaymentStatusPollerProps) {
  
  const stopPollingRef = useRef<(() => void) | null>(null);
  const lastOrderNoRef = useRef<string>("");
  const callbacksRef = useRef(callbacks);
  const configRef = useRef(config);
  const pollingRunRef = useRef(0);
  const terminalRunRef = useRef<number | null>(null);
  const completionTimeoutsRef = useRef(new Set<ReturnType<typeof setTimeout>>());
  
  // 更新 refs
  callbacksRef.current = callbacks;
  configRef.current = config;
  
  const clearCompletionTimeouts = useCallback(() => {
    for (const timeout of completionTimeoutsRef.current) {
      clearTimeout(timeout);
    }
    completionTimeoutsRef.current.clear();
  }, []);

  const stopActivePolling = useCallback(() => {
    if (stopPollingRef.current) {
      stopPollingRef.current();
      stopPollingRef.current = null;
    }
  }, []);

  const cancelPolling = useCallback(() => {
    pollingRunRef.current += 1;
    terminalRunRef.current = null;
    lastOrderNoRef.current = "";
    stopActivePolling();
    clearCompletionTimeouts();
  }, [clearCompletionTimeouts, stopActivePolling]);

  const startPolling = useCallback(async () => {
    if (!orderNo || !enabled) {
      cancelPolling();
      return;
    }

    if (lastOrderNoRef.current === orderNo && stopPollingRef.current) {
      return;
    }

    cancelPolling();
    const runId = pollingRunRef.current;
    const isCurrentRun = () => pollingRunRef.current === runId;
    lastOrderNoRef.current = orderNo;

    const scheduleForCurrentRun = (callback: () => void, delay: number) => {
      const timeout = setTimeout(() => {
        completionTimeoutsRef.current.delete(timeout);
        if (isCurrentRun()) {
          callback();
        }
      }, delay);
      completionTimeoutsRef.current.add(timeout);
    };

    const stopTerminalRun = () => {
      terminalRunRef.current = runId;
      stopActivePolling();
    };

    try {
      const stopPolling = await PaymentService.pollOrderStatus(
        orderNo,
        {
          onStatusChange: (status: OrderStatusResponse) => {
            if (isCurrentRun()) {
              callbacksRef.current.onStatusChange?.(status);
            }
          },
          onSuccess: (orderNo: string) => {
            if (!isCurrentRun()) {
              return;
            }
            stopTerminalRun();
            toast({
              title: "支付成功",
              description: "您的充值已完成，余额正在更新...",
              variant: "default"
            });
            
            scheduleForCurrentRun(() => {
              callbacksRef.current.onSuccess?.(orderNo);
              scheduleForCurrentRun(() => {
                toast({
                  title: "余额已更新",
                  description: "账户余额已成功更新",
                  variant: "default"
                });
              }, 1000);
            }, 2000);
          },
          onFailed: (reason: string) => {
            if (!isCurrentRun()) {
              return;
            }
            stopTerminalRun();
            toast({
              title: "支付失败",
              description: reason,
              variant: "destructive"
            });
            callbacksRef.current.onFailed?.(reason);
          },
          onExpired: () => {
            if (!isCurrentRun()) {
              return;
            }
            stopTerminalRun();
            toast({
              title: "支付超时",
              description: "支付二维码已过期，请重新发起支付",
              variant: "destructive"
            });
            callbacksRef.current.onExpired?.();
          },
          onError: (error: string) => {
            if (isCurrentRun()) {
              callbacksRef.current.onError?.(error);
            }
          }
        },
        { ...DEFAULT_POLLING_CONFIG, ...configRef.current }
      );

      if (!isCurrentRun() || terminalRunRef.current === runId) {
        stopPolling();
        return;
      }
      stopPollingRef.current = stopPolling;
    } catch (error) {
      if (isCurrentRun()) {
        toast({
          title: "查询支付状态失败",
          description: "网络连接异常，请检查网络后重试",
          variant: "destructive"
        });
      }
    }
  }, [cancelPolling, enabled, orderNo, stopActivePolling]);

  useEffect(() => {
    void startPolling();
    return () => {
      cancelPolling();
    };
  }, [cancelPolling, startPolling]);

  return null;
}
