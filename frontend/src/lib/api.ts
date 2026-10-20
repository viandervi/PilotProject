export type Channel = { id: string; title: string; youtubeChannelId: string; videoCount: number };
export type Overview = { channels: Channel[]; totalVideos: number; totalRecommendations: number; openFix: number; openRevive: number; applied: number };
export type Action = { videoId: string; decision: "FIX" | "REVIVE"; score: number; confidence: number; reasons: string[]; status: "OPEN" | "APPLIED" | "DISMISSED" };
export type Diagnostic = { videoId: string; decision: "FIX" | "REVIVE" | "IGNORE" | "INSUFFICIENT_DATA"; score: number; confidence: number; reasons: string[] };
export type Video = { id: string; youtubeVideoId: string; title: string; publishedAt: string | null; views28d: number; averageViewPercentage: number | null };
export type SyncJob = { id: string; syncType: "CHANNEL_SYNC" | "ANALYTICS_SYNC" | "RETENTION_SYNC"; status: "PENDING" | "RUNNING" | "RETRY_WAIT" | "COMPLETED" | "FAILED"; progressItems: number; attempts: number; errorCode: string | null; errorMessage: string | null };
export type AnalyticsCoverage = { videos: number; videosWithDailyMetrics: number; videosWithRetention: number; dailyMetricRows: number; retentionPoints: number; earliestDailyMetric: string | number[] | null; latestDailyMetric: string | number[] | null; fetchedHistoricalRanges: number };
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/v1${path}`, { credentials: "include", cache: "no-store", ...init });
  if (!response.ok) throw new Error(response.status === 401 ? "Sign in to connect your YouTube channel." : `Request failed (${response.status})`);
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
