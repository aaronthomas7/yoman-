import type { Entry } from './types';

export const BACKEND_URL = 'https://yoman-production.up.railway.app';

// Shared app key the backend requires on every request except /health
// (temporary, until Supabase Auth). Set it in mobile/.env.local, which is
// git-ignored, and restart Expo with `npx expo start -c`. Expo bakes
// EXPO_PUBLIC_* values into the app bundle, so anyone holding the app could
// extract it: fine for personal use, not for a public release.
const API_KEY = process.env.EXPO_PUBLIC_YOMAN_API_KEY ?? '';

if (!API_KEY) {
  console.warn('EXPO_PUBLIC_YOMAN_API_KEY is not set: the backend will reject every request.');
}

function authHeaders(): Record<string, string> {
  return API_KEY ? { 'X-Yoman-Key': API_KEY } : {};
}

// Server-side budget for POST /entries is ~3 min (Whisper 120s + Claude 60s
// plus headroom). Client gives up at 200s so the phone never hangs forever
// even if the server somehow does.
const CREATE_TIMEOUT_MS = 200_000;
const LIST_TIMEOUT_MS = 15_000;

export type PipelineErrorBody = {
  stage?: 'transcription' | 'structuring' | 'persistence';
  timeout?: boolean;
  message?: string;
};

export class PipelineError extends Error {
  status: number;
  stage?: PipelineErrorBody['stage'];
  timeout?: boolean;
  serverMessage?: string;

  constructor(status: number, body: PipelineErrorBody | string) {
    const parsed: PipelineErrorBody = typeof body === 'string' ? { message: body } : body;
    super(parsed.message || `HTTP ${status}`);
    this.status = status;
    this.stage = parsed.stage;
    this.timeout = parsed.timeout;
    this.serverMessage = parsed.message;
  }
}

function withTimeout(ms: number): { signal: AbortSignal; cancel: () => void; didTimeout: () => boolean } {
  const controller = new AbortController();
  let timedOut = false;
  const handle = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, ms);
  return {
    signal: controller.signal,
    cancel: () => clearTimeout(handle),
    didTimeout: () => timedOut,
  };
}

/** Turns a non-2xx response into a PipelineError, reading a JSON body if there is one. */
async function errorFrom(res: Response): Promise<PipelineError> {
  const text = await res.text();
  let body: PipelineErrorBody | string = text;
  try {
    const parsed: unknown = JSON.parse(text);
    if (parsed && typeof parsed === 'object') body = parsed as PipelineErrorBody;
  } catch {
    // Plain text body — leave as-is.
  }
  return new PipelineError(res.status, body);
}

export async function fetchEntries(): Promise<Entry[]> {
  const t = withTimeout(LIST_TIMEOUT_MS);
  try {
    const res = await fetch(`${BACKEND_URL}/entries`, { headers: authHeaders(), signal: t.signal });
    if (!res.ok) throw await errorFrom(res);
    return (await res.json()) as Entry[];
  } catch (err) {
    if (t.didTimeout()) throw new PipelineError(0, { timeout: true, message: 'Network timed out' });
    throw err;
  } finally {
    t.cancel();
  }
}

export async function fetchEntry(id: string): Promise<Entry> {
  const t = withTimeout(LIST_TIMEOUT_MS);
  try {
    const res = await fetch(`${BACKEND_URL}/entries/${id}`, { headers: authHeaders(), signal: t.signal });
    if (!res.ok) throw await errorFrom(res);
    return (await res.json()) as Entry;
  } catch (err) {
    if (t.didTimeout()) throw new PipelineError(0, { timeout: true, message: 'Network timed out' });
    throw err;
  } finally {
    t.cancel();
  }
}

export async function createEntry(audioUri: string, durationSeconds: number): Promise<Entry> {
  const form = new FormData();
  form.append('audio', {
    uri: audioUri,
    name: 'recording.m4a',
    type: 'audio/m4a',
  } as unknown as Blob);
  form.append('durationSeconds', String(durationSeconds));

  const t = withTimeout(CREATE_TIMEOUT_MS);
  try {
    // Don't set Content-Type here: fetch adds the multipart boundary itself.
    const res = await fetch(`${BACKEND_URL}/entries`, {
      method: 'POST',
      headers: authHeaders(),
      body: form,
      signal: t.signal,
    });
    if (!res.ok) throw await errorFrom(res);
    return (await res.json()) as Entry;
  } catch (err) {
    if (t.didTimeout()) {
      throw new PipelineError(0, {
        timeout: true,
        message: 'Upload or processing timed out',
      });
    }
    throw err;
  } finally {
    t.cancel();
  }
}

/** Human-readable copy for an error from createEntry / fetchEntry / fetchEntries. */
export function describeError(err: unknown): string {
  if (err instanceof PipelineError) {
    if (err.timeout) {
      return err.stage
        ? `${capitalize(err.stage)} took too long. Try again — your recording is still on the phone.`
        : 'Network timed out. Check your connection and try again.';
    }
    if (err.stage) {
      return `Couldn't ${stageVerb(err.stage)} this entry. Tap Record to retry.`;
    }
    if (err.status === 401) {
      return 'The server rejected this app’s key. Check EXPO_PUBLIC_YOMAN_API_KEY in mobile/.env.local.';
    }
    if (err.status === 503 && err.serverMessage) return err.serverMessage;
    if (err.status >= 500) return `Server error (${err.status}). Try again in a moment.`;
    if (err.status > 0) return `Request failed (${err.status}).`;
    return err.message || 'Network error.';
  }
  if (err instanceof Error) return err.message;
  return String(err);
}

function stageVerb(stage: NonNullable<PipelineErrorBody['stage']>): string {
  switch (stage) {
    case 'transcription':
      return 'transcribe';
    case 'structuring':
      return 'structure';
    case 'persistence':
      return 'save';
  }
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}
