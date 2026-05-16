import { useEffect, useState } from 'react';
import { StatusBar } from 'expo-status-bar';
import { Alert, Button, StyleSheet, Text, View } from 'react-native';
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
  const [healthStatus, setHealthStatus] = useState<string>('Tap to test backend');
  const [healthLoading, setHealthLoading] = useState(false);

  const player = useAudioPlayer(lastUri ? { uri: lastUri } : null);
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
      if (recorder.uri) setLastUri(recorder.uri);
      // Switch session out of record-capable mode so iOS routes playback
      // to the main speaker instead of the earpiece.
      await setAudioModeAsync({ allowsRecording: false, playsInSilentMode: true });
    } catch (err) {
      Alert.alert('Stop failed', err instanceof Error ? err.message : String(err));
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

  async function pingHealth() {
    setHealthLoading(true);
    setHealthStatus('Calling /health...');
    try {
      const res = await fetch(`${BACKEND_URL}/health`);
      const json = await res.json();
      setHealthStatus(JSON.stringify(json));
    } catch (err) {
      setHealthStatus(`Error: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setHealthLoading(false);
    }
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Yoman</Text>
      <Text style={styles.subtitle}>Weekend 2 — record &amp; play</Text>

      <View style={styles.section}>
        <Text style={styles.duration}>
          {recorderState.isRecording ? formatMs(recorderState.durationMillis) : '0:00'}
        </Text>
        <Button
          title={recorderState.isRecording ? 'Stop' : 'Record'}
          color={recorderState.isRecording ? '#c0392b' : undefined}
          onPress={recorderState.isRecording ? stopRecording : startRecording}
        />
      </View>

      <View style={styles.section}>
        <Button
          title={playerStatus.playing ? 'Pause' : 'Play last recording'}
          onPress={togglePlayback}
          disabled={!lastUri}
        />
        <Text style={styles.uri} numberOfLines={2}>
          {lastUri ? lastUri : 'No recording yet'}
        </Text>
      </View>

      <View style={styles.section}>
        <Button
          title={healthLoading ? 'Calling...' : 'Ping /health'}
          onPress={pingHealth}
          disabled={healthLoading}
        />
        <Text style={styles.uri}>{healthStatus}</Text>
      </View>

      <StatusBar style="auto" />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#fff',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  title: {
    fontSize: 32,
    fontWeight: '600',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 14,
    color: '#666',
    marginBottom: 24,
  },
  section: {
    width: '100%',
    alignItems: 'center',
    marginVertical: 16,
  },
  duration: {
    fontSize: 36,
    fontFamily: 'Courier',
    marginBottom: 8,
  },
  uri: {
    marginTop: 8,
    fontFamily: 'Courier',
    fontSize: 11,
    color: '#444',
    textAlign: 'center',
  },
});
