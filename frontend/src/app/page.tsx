"use client";
import { useEffect, useState } from "react";
import { Activity, CheckCircle2, CircleAlert, ExternalLink, RefreshCw, TrendingUp, Video as VideoIcon, Wrench } from "lucide-react";
import { api, type Action, type Channel, type Overview, type Video, type SyncJob, type AnalyticsCoverage, type Diagnostic } from "@/lib/api";

// Micronaut may serialize LocalDate as YYYY-M-D or YYYYMMDD without separators.
// Parse calendar components explicitly to avoid browser-dependent Date parsing.
function formatAnalyticsDate(value: string | null | undefined): string {
  if (!value) return "none";
  const compact = value.replace(/-/g, "");
  const match = /^(\\d{4})(\\d{1,2})(\\d{1,2})$/.exec(compact);
  if (!match) return value;
  const year = Number(match[1]), month = Number(match[2]), day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day));
  if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== day) return value;
  return new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "short", year: "numeric", timeZone: "UTC" }).format(date);
}

type View = "overview" | "videos" | "actions";
export default function Home() {
  const [view, setView] = useState<View>("overview");
  const [overview, setOverview] = useState<Overview | null>(null);
  const [channel, setChannel] = useState<Channel | null>(null);
  const [actions, setActions] = useState<Action[]>([]);
  const [videos, setVideos] = useState<Video[]>([]);
  const [jobs, setJobs] = useState<SyncJob[]>([]);
  const [diagnostics, setDiagnostics] = useState<Diagnostic[]>([]);
  const [coverage, setCoverage] = useState<AnalyticsCoverage | null>(null);
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [unauthorized, setUnauthorized] = useState(false);

  async function load() {
    setLoading(true); setError("");
    try {
      const summary = await api<Overview>("/dashboard/overview");
      setOverview(summary);
      const first = summary.channels[0] ?? null;
      setChannel(first);
      if (first) {
        const [nextActions, nextVideos, nextJobs, nextCoverage, nextDiagnostics] = await Promise.all([
          api<Action[]>(`/channels/${first.id}/recommendation-actions`),
          api<Video[]>(`/channels/${first.id}/dashboard-videos`),
          api<SyncJob[]>(`/channels/${first.id}/sync-status`),
          api<AnalyticsCoverage>(`/channels/${first.id}/analytics-coverage`),
          api<Diagnostic[]>(`/channels/${first.id}/recommendation-diagnostics`),
        ]);
        setActions(nextActions); setVideos(nextVideos); setJobs(nextJobs); setCoverage(nextCoverage); setDiagnostics(nextDiagnostics);
      } else { setActions([]); setVideos([]); setJobs([]); setCoverage(null); setDiagnostics([]); }
      setUnauthorized(false);
    } catch (e) {
      const message = e instanceof Error ? e.message : "Could not load dashboard";
      setUnauthorized(message.startsWith("Sign in"));
      setError(message);
    } finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, []);

  async function changeStatus(videoId: string, status: Action["status"]) {
    if (!channel) return;
    setBusy(true); setError("");
    try {
      await api<void>(`/channels/${channel.id}/recommendation-actions/${videoId}`, {
        method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status }),
      });
      await load();
    } catch (e) { setError(e instanceof Error ? e.message : "Action failed"); }
    finally { setBusy(false); }
  }
  async function refreshRecommendations() {
    if (!channel) return;
    setBusy(true); setError(""); setNotice("");
    try {
      await api<Action[]>(`/channels/${channel.id}/recommendations/refresh`, { method: "POST" });
      await load();
    } catch (e) { setError(e instanceof Error ? e.message : "Refresh failed"); }
    finally { setBusy(false); }
  }
  async function sync(kind: "videos" | "analytics" | "retention" = "videos") {
    setBusy(true); setError(""); setNotice("");
    try {
      const endpoint = kind === "videos" ? "/channels/sync" : kind === "analytics" ? "/channels/analytics-sync" : "/channels/retention-sync";
      await api(endpoint, { method: "POST" });
      setNotice(`${kind === "videos" ? "Video" : kind === "analytics" ? "Analytics" : "Retention"} sync queued. Reload to check job progress.`);
      if (channel) {
        const nextJobs = await api<SyncJob[]>(`/channels/${channel.id}/sync-status`);
        setJobs(nextJobs);
      }
    } catch (e) { setError(e instanceof Error ? e.message : "Sync failed"); }
    finally { setBusy(false); }
  }

  const openActions = actions.filter(a => a.status === "OPEN");
  const fix = openActions.filter(a => a.decision === "FIX").length;
  const revive = openActions.filter(a => a.decision === "REVIVE").length;
  return <div className="min-h-screen lg:flex">
    <aside className="border-b border-slate-800 bg-[#11182b] p-6 lg:min-h-screen lg:w-64 lg:border-b-0 lg:border-r">
      <div className="mb-9 flex items-center gap-3"><Activity className="text-cyan-400" /><div><h1 className="text-xl font-bold">CreatorRx</h1><p className="text-xs text-slate-400">Your YouTube Growth Doctor</p></div></div>
      <nav className="flex gap-2 lg:flex-col">
        {(["overview","videos","actions"] as const).map(v => <button key={v} onClick={() => setView(v)} className={`rounded-lg px-4 py-3 text-left capitalize ${view===v ? "bg-cyan-500/15 text-cyan-300" : "text-slate-300 hover:bg-slate-800"}`}>{v === "actions" ? "Recommendations" : v}</button>)}
      </nav>
    </aside>
    <main className="mx-auto w-full max-w-6xl p-5 lg:p-10">
      <header className="mb-8 flex flex-wrap items-center justify-between gap-4">
        <div><p className="mb-1 text-sm text-slate-400">Creator intelligence</p><h2 className="text-3xl font-bold capitalize">{view === "actions" ? "Recommendations" : view}</h2><p className="mt-2 text-sm text-slate-400">{channel?.title || "Connect a YouTube channel to get started"}</p></div>
        <button onClick={() => void load()} disabled={loading || busy} className="flex items-center gap-2 rounded-lg border border-slate-700 px-4 py-2 text-sm hover:bg-slate-800"><RefreshCw size={16}/> Reload</button>
      </header>
      {notice && <div role="status" className="mb-6 rounded-xl border border-cyan-700/50 bg-cyan-900/20 p-4 text-sm text-cyan-200">{notice}</div>}
      {error && <div role="alert" className="mb-6 rounded-xl border border-amber-700/50 bg-amber-900/20 p-4 text-sm text-amber-200">{error}</div>}
      {loading ? <p className="text-slate-400">Loading your channel data…</p> :
        unauthorized ? <div className="rounded-xl border border-slate-700 bg-slate-900 p-8"><h3 className="mb-3 text-xl font-semibold">Connect your YouTube account</h3><p className="mb-6 text-slate-400">CreatorRx uses your Google account to securely access your channel analytics.</p><a href="/auth/google" className="inline-block rounded-lg bg-cyan-500 px-5 py-3 font-semibold text-slate-950">Connect YouTube</a></div> :
        !channel ? <div className="rounded-xl border border-slate-700 bg-slate-900 p-8"><p className="mb-5 text-slate-300">No synced channel yet.</p><button onClick={() => void sync()} disabled={busy} className="rounded-lg bg-cyan-500 px-5 py-3 font-semibold text-slate-950">Discover and sync channel</button></div> :
        <>
          {view === "overview" && <>
            <section className="mb-8 grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
              {[
                { title:"Videos synced",value:overview?.totalVideos ?? 0,Icon:VideoIcon },
                { title:"Needs fixing",value:fix,Icon:Wrench },
                { title:"Revival opportunities",value:revive,Icon:TrendingUp },
                { title:"Actions applied",value:overview?.applied ?? 0,Icon:CheckCircle2 },
              ].map(({title,value,Icon}) => <div key={title} className="rounded-xl border border-slate-800 bg-slate-900 p-5"><div className="mb-4 flex justify-between text-sm text-slate-400"><span>{title}</span><Icon size={18}/></div><div className="text-3xl font-bold">{value}</div></div>)}
            </section>
            <div className="mb-6 flex flex-wrap gap-3">
              <button onClick={() => void sync("videos")} disabled={busy} className="rounded-lg border border-slate-700 px-5 py-3">Sync videos</button>
              <button onClick={() => void sync("analytics")} disabled={busy || videos.length === 0} className="rounded-lg border border-slate-700 px-5 py-3 disabled:opacity-50">Sync analytics</button>
              <button onClick={() => void sync("retention")} disabled={busy || videos.length === 0} className="rounded-lg border border-slate-700 px-5 py-3 disabled:opacity-50">Sync retention</button>
              <button onClick={() => void refreshRecommendations()} disabled={busy || !coverage || coverage.videosWithDailyMetrics === 0} className="rounded-lg bg-cyan-500 px-5 py-3 font-semibold text-slate-950 disabled:opacity-50">Analyze recommendations</button>
            </div>
            <section className="mb-8 rounded-xl border border-slate-800 bg-slate-900 p-5">
              <h3 className="mb-3 font-semibold">Data readiness</h3>
              <p className="mb-3 text-sm text-slate-400">Analytics: {coverage?.videosWithDailyMetrics ?? 0}/{coverage?.videos ?? 0} videos · Retention: {coverage?.videosWithRetention ?? 0}/{coverage?.videos ?? 0} videos</p>
              <p className="mb-3 text-xs text-slate-400">Historical daily data: {formatAnalyticsDate(coverage?.earliestDailyMetric)} to {formatAnalyticsDate(coverage?.latestDailyMetric)} · Completed historical ranges: {coverage?.fetchedHistoricalRanges ?? 0} (including empty responses)</p>
              <div className="grid gap-2 sm:grid-cols-3">
                {(["CHANNEL_SYNC", "ANALYTICS_SYNC", "RETENTION_SYNC"] as const).map(type => {
                  const job = jobs.find(j => j.syncType === type);
                  return <div key={type} className="rounded-lg border border-slate-700 p-3 text-sm">
                    <p className="text-slate-400">{type === "CHANNEL_SYNC" ? "Video sync" : type === "ANALYTICS_SYNC" ? "Analytics sync" : "Retention sync"}</p>
                    <p className="mt-1 font-semibold">{job?.status.replace("_", " ") ?? "Not started"}</p>
                    {job && <p className="mt-1 text-xs text-slate-400">{job.progressItems} items processed</p>}
                    {job?.errorCode && <p className="mt-1 text-xs text-amber-300">{job.errorCode}{job.errorMessage ? `: ${job.errorMessage}` : ""}</p>}
                  </div>;
                })}
              </div>
              <p className="mt-3 text-xs text-slate-400">Reload to refresh sync progress. Recommendations require analytics data; retention improves signal quality.</p>
            </section>
            <h3 className="mb-4 text-xl font-semibold">Priority actions</h3>
            <ActionList actions={openActions.slice(0,5)} videos={videos} busy={busy} changeStatus={changeStatus}/>
          </>}
          {view === "actions" && <><div className="mb-6 flex flex-wrap items-center justify-between gap-3"><p className="text-sm text-slate-400">Recommendations are ranked by estimated impact.</p><button onClick={() => void refreshRecommendations()} disabled={busy} className="rounded-lg bg-cyan-500 px-4 py-2 font-semibold text-slate-950">Refresh analysis</button></div><ActionList actions={actions} videos={videos} busy={busy} changeStatus={changeStatus}/>
            <section className="mt-8 rounded-xl border border-slate-800 bg-slate-900 p-5">
              <h3 className="mb-2 text-lg font-semibold">Analysis diagnostics</h3>
              <p className="mb-4 text-sm text-slate-400">Every synced video's current decision, including cases without an actionable recommendation. These are live evaluations; Refresh analysis persists actionable recommendations.</p>
              {diagnostics.length === 0 ? <p className="text-slate-400">No videos available to evaluate.</p> : <div className="space-y-3">{diagnostics.map(d => {
                const video = videos.find(v => v.id === d.videoId);
                return <div key={d.videoId} className="rounded-lg border border-slate-700 p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2"><span className="font-medium">{video?.title ?? d.videoId}</span><span className="rounded bg-slate-800 px-2 py-1 text-xs">{d.decision.replace("_", " ")}</span></div>
                  <p className="mt-1 text-xs text-slate-400">Confidence: {Math.round(d.confidence * 100)}%</p>
                  <ul className="mt-2 list-disc pl-5 text-sm text-slate-400">{d.reasons.map((reason,i) => <li key={i}>{reason}</li>)}</ul>
                </div>;
              })}</div>}
            </section></>}
          {view === "videos" && <div className="overflow-x-auto rounded-xl border border-slate-800"><table className="w-full text-left text-sm"><thead className="bg-slate-900 text-slate-400"><tr><th className="p-4">Video</th><th className="p-4">28-day views</th><th className="p-4">Avg. viewed</th></tr></thead><tbody>{videos.map(v=><tr key={v.id} className="border-t border-slate-800"><td className="p-4"><a className="inline-flex items-center gap-2 text-cyan-300 hover:underline" href={`https://www.youtube.com/watch?v=${encodeURIComponent(v.youtubeVideoId)}`} target="_blank" rel="noreferrer">{v.title}<ExternalLink size={14}/></a></td><td className="p-4">{v.views28d.toLocaleString()}</td><td className="p-4">{v.averageViewPercentage == null ? "—" : `${v.averageViewPercentage.toFixed(1)}%`}</td></tr>)}</tbody></table>{videos.length===0 && <p className="p-6 text-slate-400">No videos ingested yet. Sync your channel first.</p>}</div>}
        </>}
    </main>
  </div>;
}
function ActionList({actions,videos,busy,changeStatus}:{actions:Action[];videos:Video[];busy:boolean;changeStatus:(id:string,status:Action["status"])=>Promise<void>}) {
  if (actions.length===0) return <div className="rounded-xl border border-slate-800 bg-slate-900 p-8 text-slate-400">No actionable recommendations right now. See Analysis diagnostics for per-video reasons.</div>;
  return <div className="space-y-3">{actions.map(a => { const video=videos.find(v=>v.id===a.videoId); return <article key={a.videoId} className="rounded-xl border border-slate-800 bg-slate-900 p-5"><div className="flex flex-wrap items-start justify-between gap-4"><div className="min-w-0 flex-1"><div className="mb-2 flex flex-wrap items-center gap-2"><span className={`rounded px-2 py-1 text-xs font-bold ${a.decision==="FIX" ? "bg-amber-500/20 text-amber-300" : "bg-emerald-500/20 text-emerald-300"}`}>{a.decision}</span><span className="text-xs text-slate-400">{a.status} · Confidence {Math.round(a.confidence*100)}%</span></div><h4 className="font-semibold">{video?.title ?? a.videoId}</h4><ul className="mt-2 space-y-1 text-sm text-slate-400">{a.reasons.map((reason,i)=><li key={i} className="flex items-start gap-2"><CircleAlert size={14} className="mt-1 shrink-0"/>{reason}</li>)}</ul></div>{a.status==="OPEN" && <div className="flex gap-2"><button disabled={busy} onClick={()=>void changeStatus(a.videoId,"APPLIED")} className="rounded-lg bg-cyan-500 px-3 py-2 text-sm font-semibold text-slate-950">Mark applied</button><button disabled={busy} onClick={()=>void changeStatus(a.videoId,"DISMISSED")} className="rounded-lg border border-slate-700 px-3 py-2 text-sm">Dismiss</button></div>}</div></article>; })}</div>;
}
