import { useEffect, useState } from 'react';
import { StatusBar } from 'expo-status-bar';
import {
  Alert,
  Button,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import {
  AudioModule,
  RecordingPresets,
  setAudioModeAsync,
  useAudioPlayer,
  useAudioPlayerStatus,
  useAudioRecorder,
  useAudioRecorderState,
} from 'expo-audio';

const BACKEND_URL = 'http://192.168.0.2:8080';

type Entry = {
  id: string;
  userId: string;
  createdAt: string;
  entryDate: string;
  transcript: string;
  summary: string;
  workSection: string | null;
  personalSection: string | null;
  mood: string | null;
  peopleMentioned: string[];
  tags: string[];
  durationSeconds: number | null;
};

function formatMs(ms: number): string {
  const total = Math.floor(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

export default function App() {
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const recorderState = useAudioRecorderState(recorder);

  const [lastUri, setLastUri] = useState<string | null>(null);
  const [entry, setEntry] = useState<Entry | null>(null);
  const [status, setStatus] = useState<string>('Record something to start your first entry.');
  const [busy, setBusy] = useState(false);

  const player = useAudioPlayer(null);
  const playerStatus = useAudioPlayerStatus(player);

  useEffect(() => {
    (async () => {
      const perm = await AudioModule.requestRecordingPermissionsAsync();
      if (!perm.granted) {
        Alert.alert(
          'Microphone needed',
          'Yoman needs microphone access to record journal entries. Enable it in Settings.',
        );
        return;
      }
      await setAudioModeAsync({ allowsRecording: false, playsInSilentMode: true });
    })();
  }, []);

  async function startRecording() {
    try {
      setEntry(null);
      setStatus('Recording...');
      await setAudioModeAsync({ allowsRecording: true, playsInSilentMode: true });
      await recorder.prepareToRecordAsync();
      recorder.record();
    } catch (err) {
      Alert.alert('Recording failed', err instanceof Error ? err.message : String(err));
    }
  }

  async function stopRecording() {
    try {
      await recorder.stop();
      const uri = recorder.uri;
      const durationSeconds = Math.round(recorderState.durationMillis / 1000);
      if (uri) {
        setLastUri(uri);
        player.replace({ uri });
      }
      await setAudioModeAsync({ allowsRecording: false, playsInSilentMode: true });
      if (uri) await processEntry(uri, durationSeconds);
    } catch (err) {
      Alert.alert('Stop failed', err instanceof Error ? err.message : String(err));
    }
  }

  async function processEntry(uri: string, durationSeconds: number) {
    setBusy(true);
    setStatus('Transcribing & structuring (this takes ~10-15s)...');
    try {
      const form = new FormData();
      form.append('audio', {
        uri,
        name: 'recording.m4a',
        type: 'audio/m4a',
      } as unknown as Blob);
      form.append('durationSeconds', String(durationSeconds));

      const res = await fetch(`${BACKEND_URL}/entries`, { method: 'POST', body: form });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(`${res.status}: ${text.slice(0, 300)}`);
      }
      const json = (await res.json()) as Entry;
      setEntry(json);
      setStatus(`Saved entry ${json.id.slice(0, 8)}…`);
    } catch (err) {
      setStatus(`Error: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setBusy(false);
    }
  }

  function togglePlayback() {
    if (!lastUri) return;
    if (playerStatus.playing) {
      player.pause();
    } else {
      if (playerStatus.didJustFinish || playerStatus.currentTime >= playerStatus.duration) {
        player.seekTo(0);
      }
      player.play();
    }
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Yoman</Text>
      <Text style={styles.subtitle}>Weekend 4 — structured entry</Text>

      <View style={styles.controls}>
        <Text style={styles.duration}>
          {recorderState.isRecording ? formatMs(recorderState.durationMillis) : '0:00'}
        </Text>
        <View style={styles.buttonRow}>
          <Button
            title={recorderState.isRecording ? 'Stop' : 'Record'}
            color={recorderState.isRecording ? '#c0392b' : undefined}
            onPress={recorderState.isRecording ? stopRecording : startRecording}
            disabled={busy}
          />
          <View style={{ width: 12 }} />
          <Button
            title={playerStatus.playing ? 'Pause' : 'Play'}
            onPress={togglePlayback}
            disabled={!lastUri || busy}
          />
        </View>
        <Text style={styles.status}>{status}</Text>
      </View>

      <ScrollView style={styles.entryBox} contentContainerStyle={styles.entryInner}>
        {entry ? <EntryView entry={entry} /> : null}
      </ScrollView>

      <StatusBar style="auto" />
    </View>
  );
}

function EntryView({ entry }: { entry: Entry }) {
  return (
    <View>
      <Text style={styles.summary}>{entry.summary}</Text>

      {entry.mood ? (
        <View style={styles.moodBadge}>
          <Text style={styles.moodText}>{entry.mood}</Text>
        </View>
      ) : null}

      {entry.workSection ? <Section title="Work" body={entry.workSection} /> : null}
      {entry.personalSection ? <Section title="Personal" body={entry.personalSection} /> : null}

      <ChipRow label="People" items={entry.peopleMentioned} />
      <ChipRow label="Tags" items={entry.tags} />

      <Text style={styles.transcriptLabel}>Transcript</Text>
      <Text style={styles.transcript}>{entry.transcript}</Text>
    </View>
  );
}

function Section({ title, body }: { title: string; body: string }) {
  return (
    <View style={styles.section}>
      <Text style={styles.sectionLabel}>{title}</Text>
      <Text style={styles.sectionBody}>{body}</Text>
    </View>
  );
}

function ChipRow({ label, items }: { label: string; items: string[] }) {
  if (!items || items.length === 0) return null;
  return (
    <View style={styles.chipRow}>
      <Text style={styles.chipRowLabel}>{label}</Text>
      <View style={styles.chips}>
        {items.map((it) => (
          <View key={it} style={styles.chip}>
            <Text style={styles.chipText}>{it}</Text>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    paddingTop: 64,
    paddingHorizontal: 20,
    paddingBottom: 16,
  },
  title: {
    fontSize: 28,
    fontWeight: '600',
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 12,
    color: '#888',
    textAlign: 'center',
    marginBottom: 16,
  },
  controls: {
    alignItems: 'center',
    paddingBottom: 12,
    borderBottomColor: '#eee',
    borderBottomWidth: 1,
  },
  duration: {
    fontSize: 28,
    fontFamily: 'Courier',
    marginBottom: 8,
  },
  buttonRow: {
    flexDirection: 'row',
  },
  status: {
    marginTop: 10,
    fontSize: 12,
    color: '#666',
    textAlign: 'center',
  },
  entryBox: {
    flex: 1,
    marginTop: 12,
  },
  entryInner: {
    paddingBottom: 32,
  },
  summary: {
    fontSize: 18,
    lineHeight: 26,
    color: '#111',
    marginBottom: 16,
  },
  moodBadge: {
    alignSelf: 'flex-start',
    backgroundColor: '#eef',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    marginBottom: 16,
  },
  moodText: {
    color: '#334',
    fontSize: 12,
    textTransform: 'lowercase',
  },
  section: {
    marginBottom: 14,
  },
  sectionLabel: {
    fontSize: 11,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    color: '#888',
    marginBottom: 4,
  },
  sectionBody: {
    fontSize: 15,
    lineHeight: 22,
    color: '#222',
  },
  chipRow: {
    marginVertical: 8,
  },
  chipRowLabel: {
    fontSize: 11,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    color: '#888',
    marginBottom: 6,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  chip: {
    backgroundColor: '#f3f3f3',
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 10,
  },
  chipText: {
    fontSize: 12,
    color: '#444',
  },
  transcriptLabel: {
    fontSize: 11,
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    color: '#888',
    marginTop: 16,
    marginBottom: 4,
  },
  transcript: {
    fontSize: 13,
    lineHeight: 20,
    color: '#555',
    fontStyle: 'italic',
  },
});
