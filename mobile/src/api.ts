import type { Entry } from './types';

export const BACKEND_URL = 'http://192.168.0.2:8080';

export async function fetchEntries(): Promise<Entry[]> {
  const res = await fetch(`${BACKEND_URL}/entries`);
  if (!res.ok) {
    throw new Error(`GET /entries failed: ${res.status} ${await res.text()}`);
  }
  return (await res.json()) as Entry[];
}

export async function fetchEntry(id: string): Promise<Entry> {
  const res = await fetch(`${BACKEND_URL}/entries/${id}`);
  if (!res.ok) {
    throw new Error(`GET /entries/${id} failed: ${res.status} ${await res.text()}`);
  }
  return (await res.json()) as Entry;
}

export async function createEntry(audioUri: string, durationSeconds: number): Promise<Entry> {
  const form = new FormData();
  form.append('audio', {
    uri: audioUri,
    name: 'recording.m4a',
    type: 'audio/m4a',
  } as unknown as Blob);
  form.append('durationSeconds', String(durationSeconds));

  const res = await fetch(`${BACKEND_URL}/entries`, { method: 'POST', body: form });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${res.status}: ${text.slice(0, 300)}`);
  }
  return (await res.json()) as Entry;
}
