import { streamChat as streamChatService } from "@/lib/stream-service";

export async function streamChat(
  message: string,
  sessionId?: string,
  fileUrls?: string[],
  signal?: AbortSignal,
) {
  if (!sessionId) {
    throw new Error("Session ID is required");
  }

  return streamChatService(sessionId, message, fileUrls, signal);
}

