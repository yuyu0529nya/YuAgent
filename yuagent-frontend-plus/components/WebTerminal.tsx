"use client";

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Terminal } from '@xterm/xterm';
import { FitAddon } from '@xterm/addon-fit';
import { WebLinksAddon } from '@xterm/addon-web-links';
import '@xterm/xterm/css/xterm.css';
import { buildApiUrl } from '@/lib/api-config';

interface WebTerminalProps {
  containerId: string;
  containerName: string;
  onClose?: () => void;
}

export default function WebTerminal({ containerId, containerName, onClose }: WebTerminalProps) {
  const terminalRef = useRef<HTMLDivElement>(null);
  const terminal = useRef<Terminal | null>(null);
  const websocket = useRef<WebSocket | null>(null);
  const fitAddon = useRef<FitAddon | null>(null);
  const inputListener = useRef<{ dispose: () => void } | null>(null);
  const initializationTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const fitTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [isConnected, setIsConnected] = useState(false);
  const [connectionStatus, setConnectionStatus] = useState('正在连接...');

  const connectWebSocket = useCallback(() => {
    const hasAuthCookie = window.document.cookie
      .split(';')
      .some((cookie) => cookie.trim().startsWith('token='));
    if (!hasAuthCookie) {
      setConnectionStatus('未登录，无法连接终端');
      return;
    }

    const query = new URLSearchParams({ containerId }).toString();
    const terminalUrl = new URL(buildApiUrl('/ws/terminal'), window.location.origin);
    terminalUrl.protocol = terminalUrl.protocol === 'https:' ? 'wss:' : 'ws:';
    terminalUrl.search = query;

    websocket.current?.close();
    const socket = new WebSocket(terminalUrl.toString());
    websocket.current = socket;

    socket.onopen = () => {
      if (websocket.current !== socket) return;
      setIsConnected(true);
      setConnectionStatus('已连接');
      if (!terminal.current) return;

      terminal.current.clear();
      terminal.current.writeln('\x1b[32m✓ 终端连接成功\x1b[0m');
      terminal.current.writeln(`容器: ${containerName} (${containerId})`);
      terminal.current.writeln('');
      inputListener.current?.dispose();
      inputListener.current = terminal.current.onData((data) => {
        if (socket.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ type: 'input', data }));
        }
      });
    };

    socket.onmessage = (event) => {
      if (websocket.current === socket) terminal.current?.write(event.data);
    };
    socket.onclose = (event) => {
      if (websocket.current !== socket) return;
      websocket.current = null;
      inputListener.current?.dispose();
      inputListener.current = null;
      setIsConnected(false);
      setConnectionStatus('连接已断开');
      terminal.current?.writeln('\r\n\x1b[31m✗ 终端连接已断开\x1b[0m');
      if (event.code !== 1000) {
        terminal.current?.writeln(`错误代码: ${event.code}, 原因: ${event.reason || '未知'}`);
      }
    };
    socket.onerror = () => {
      if (websocket.current !== socket) return;
      setIsConnected(false);
      setConnectionStatus('连接失败');
      terminal.current?.writeln('\r\n\x1b[31m✗ 终端连接失败\x1b[0m');
      terminal.current?.writeln('请检查容器是否正在运行');
    };
  }, [containerId, containerName]);

  useEffect(() => {
    if (!terminalRef.current) return;

    const initializeTerminal = () => {
      // 确保容器有有效的尺寸
      const container = terminalRef.current;
      if (!container || container.offsetWidth === 0 || container.offsetHeight === 0) {
        // 如果容器还没有尺寸，稍后重试
        initializationTimer.current = setTimeout(initializeTerminal, 50);
        return;
      }

      // 初始化终端
      terminal.current = new Terminal({
        cursorBlink: true,
        fontSize: 14,
        fontFamily: 'Monaco, Menlo, "Ubuntu Mono", monospace',
        theme: {
          background: '#1e1e1e',
          foreground: '#d4d4d4',
          cursor: '#ffffff',
          black: '#000000',
          red: '#cd3131',
          green: '#0dbc79',
          yellow: '#e5e510',
          blue: '#2472c8',
          magenta: '#bc3fbc',
          cyan: '#11a8cd',
          white: '#e5e5e5',
          brightBlack: '#666666',
          brightRed: '#f14c4c',
          brightGreen: '#23d18b',
          brightYellow: '#f5f543',
          brightBlue: '#3b8eea',
          brightMagenta: '#d670d6',
          brightCyan: '#29b8db',
          brightWhite: '#ffffff'
        },
        cols: 80,
        rows: 24
      });

      // 添加插件
      fitAddon.current = new FitAddon();
      terminal.current.loadAddon(fitAddon.current);
      terminal.current.loadAddon(new WebLinksAddon());

      // 挂载到DOM
      terminal.current.open(container);
      
      // 延迟执行fit以确保DOM已完全渲染
      fitTimer.current = setTimeout(() => {
        if (fitAddon.current && terminal.current) {
          try {
            fitAddon.current.fit();
          } catch (error) {
 
          }
        }
      }, 200);

      connectWebSocket();
    };

    // 开始初始化
    initializeTerminal();

    // 处理窗口大小调整
    const handleResize = () => {
      if (fitAddon.current && terminal.current) {
        try {
          fitAddon.current.fit();
        } catch (error) {
 
        }
      }
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      if (initializationTimer.current) clearTimeout(initializationTimer.current);
      if (fitTimer.current) clearTimeout(fitTimer.current);
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current);
      inputListener.current?.dispose();
      inputListener.current = null;
      websocket.current?.close();
      websocket.current = null;
      if (terminal.current) {
        terminal.current.dispose();
        terminal.current = null;
      }
    };
  }, [connectWebSocket]);

  const handleReconnect = () => {
    websocket.current?.close();
    setConnectionStatus('正在重连...');
    if (reconnectTimer.current) clearTimeout(reconnectTimer.current);
    reconnectTimer.current = setTimeout(connectWebSocket, 1000);
  };

  const handleFullscreen = () => {
    if (terminalRef.current) {
      if (document.fullscreenElement) {
        document.exitFullscreen();
      } else {
        terminalRef.current.requestFullscreen();
      }
    }
  };

  return (
    <div className="flex flex-col h-full bg-gray-900">
      {/* 终端工具栏 */}
      <div className="flex items-center justify-between p-3 bg-gray-800 border-b border-gray-700">
        <div className="flex items-center space-x-3">
          <div className="flex items-center space-x-2">
            <div className={`w-3 h-3 rounded-full ${isConnected ? 'bg-green-500' : 'bg-red-500'}`}></div>
            <span className="text-sm text-gray-300">{connectionStatus}</span>
          </div>
          <div className="text-sm text-gray-400">
            {containerName} ({containerId.substring(0, 12)})
          </div>
        </div>
        
        <div className="flex items-center space-x-2">
          {!isConnected && (
            <button
              onClick={handleReconnect}
              className="px-3 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
            >
              重连
            </button>
          )}
          <button
            onClick={handleFullscreen}
            className="px-3 py-1 text-xs bg-gray-600 text-white rounded hover:bg-gray-700 transition-colors"
          >
            全屏
          </button>
          {onClose && (
            <button
              onClick={onClose}
              className="px-3 py-1 text-xs bg-red-600 text-white rounded hover:bg-red-700 transition-colors"
            >
              关闭
            </button>
          )}
        </div>
      </div>

      {/* 终端区域 */}
      <div className="flex-1 p-4 bg-gray-900">
        <div 
          ref={terminalRef} 
          className="w-full h-full"
          style={{ 
            minHeight: '400px',
            width: '100%',
            height: '100%'
          }}
        />
      </div>

      {/* 终端提示 */}
      <div className="p-2 bg-gray-800 border-t border-gray-700">
        <div className="text-xs text-gray-400 flex flex-wrap gap-4">
          <span>Ctrl+C: 中断命令</span>
          <span>Ctrl+D: 退出</span>
          <span>clear: 清屏</span>
          <span>exit: 退出终端</span>
        </div>
      </div>
    </div>
  );
}
