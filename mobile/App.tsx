import { useEffect, useState } from 'react';
import { StatusBar } from 'expo-status-bar';
import { Alert, Button, ScrollView, StyleSheet, Text, View } from 'react-native';
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
  const [transcript, setTranscript] = useState<string>('');
  const [transcribing, setTranscribing] = useState(false);

  // useAudioPlayer binds to its initial source and ignores later prop changes,
  // so we create it empty and swap the source imperatively via player.replace().
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
      setTranscript('');
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
      if (uri) {
        setLastUri(uri);
        player.replace({ uri });
      }
      await setAudioModeAsync({ allowsRecording: false, playsInSilentMode: true });
      if (uri) await transcribeRecording(uri);
    } catch (err) {
      Alert.alert('Stop failed', err instanceof Error ? err.message : String(err));
    }
  }

  async function transcribeRecording(uri: string) {
    setTranscribing(true);
    setTranscript('Transcribing...');
    try {
      const form = new FormData();
      // React Native FormData accepts { uri, name, type } for file uploads.
      form.append('audio', {
        uri,
        name: 'recording.m4a',
        type: 'audio/m4a',
      } as unknown as Blob);

      const res = await fetch(`${BACKEND_URL}/entries`, {
        method: 'POST',
        body: form,
      });
      if (!res.ok) {
        const text = await res.text();
        throw new Error(`${res.status}: ${text.slice(0, 200)}`);
      }
      const json = (await res.json()) as { transcript: string };
      setTranscript(json.transcript || '(empty transcript)');
    } catch (err) {
      setTranscript(`Error: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setTranscribing(false);
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
      <Text style={styles.subtitle}>Weekend 3 — voice to transcript</Text>

      <View style={styles.section}>
        <Text style={styles.duration}>
          {recorderState.isRecording ? formatMs(recorderState.durationMillis) : '0:00'}
        </Text>
        <Button
          title={recorderState.isRecording ? 'Stop' : 'Record'}
          color={recorderState.isRecording ? '#c0392b' : undefined}
          onPress={recorderState.isRecording ? stopRecording : startRecording}
          disabled={transcribing}
        />
      </View>

      <View style={styles.section}>
        <Button
          title={playerStatus.playing ? 'Pause' : 'Play last recording'}
          onPress={togglePlayback}
          disabled={!lastUri}
        />
      </View>

      <ScrollView style={styles.transcriptBox} contentContainerStyle={styles.transcriptInner}>
        <Text style={styles.transcriptLabel}>
          {transcribing ? 'Transcript (in progress)' : 'Transcript'}
        </Text>
        <Text style={styles.transcript}>{transcript || 'Record something and stop to see your words here.'}</Text>
      </ScrollView>

      <StatusBar style="auto" />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    paddingTop: 64,
    paddingHorizontal: 24,
    paddingBottom: 24,
  },
  title: {
    fontSize: 32,
    fontWeight: '600',
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 14,
    color: '#666',
    textAlign: 'center',
    marginBottom: 24,
  },
  section: {
    alignItems: 'center',
    marginVertical: 12,
  },
  duration: {
    fontSize: 36,
    fontFamily: 'Courier',
    marginBottom: 8,
  },
  transcriptBox: {
    flex: 1,
    marginTop: 16,
    borderColor: '#e0e0e0',
    borderWidth: 1,
    borderRadius: 8,
  },
  transcriptInner: {
    padding: 16,
  },
  transcriptLabel: {
    fontSize: 12,
    color: '#888',
    marginBottom: 8,
    textTransform: 'uppercase',
    letterSpacing: 0.5,
  },
  transcript: {
    fontSize: 16,
    lineHeight: 24,
    color: '#111',
  },
});
