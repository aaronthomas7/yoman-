import { useEffect, useState } from 'react';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
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
import { createEntry, describeError } from '../api';
import type { RootStackParamList } from '../types';

type Props = NativeStackScreenProps<RootStackParamList, 'Record'>;

function formatMs(ms: number): string {
  const total = Math.floor(ms / 1000);
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

export function RecordScreen({ navigation }: Props) {
  const recorder = useAudioRecorder(RecordingPresets.HIGH_QUALITY);
  const recorderState = useAudioRecorderState(recorder);

  const [lastUri, setLastUri] = useState<string | null>(null);
  const [status, setStatus] = useState<string>('Tap Record to start.');
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
      const entry = await createEntry(uri, durationSeconds);
      // Navigate straight to the detail of the entry we just saved.
      // Replace, not push, so the back button returns to Home not Record.
      navigation.replace('Detail', { id: entry.id });
    } catch (err) {
      setStatus(describeError(err));
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
      <View style={styles.spacer} />

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

      <View style={styles.spacer} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff', alignItems: 'center', padding: 24 },
  spacer: { flex: 1 },
  duration: { fontSize: 40, fontFamily: 'Courier', marginBottom: 16 },
  buttonRow: { flexDirection: 'row' },
  status: { marginTop: 20, fontSize: 13, color: '#666', textAlign: 'center' },
});
