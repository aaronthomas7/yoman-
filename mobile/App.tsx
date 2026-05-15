import { useState } from 'react';
import { StatusBar } from 'expo-status-bar';
import { Button, StyleSheet, Text, View } from 'react-native';

// Laptop's LAN IP. Update if your Wi-Fi assigns a different one
// (run `ipconfig` and look for the active adapter's IPv4 Address).
const BACKEND_URL = 'http://192.168.0.7:8080';

export default function App() {
  const [status, setStatus] = useState<string>('Tap to test backend');
  const [loading, setLoading] = useState(false);

  async function pingHealth() {
    setLoading(true);
    setStatus('Calling /health...');
    try {
      const res = await fetch(`${BACKEND_URL}/health`);
      const json = await res.json();
      setStatus(JSON.stringify(json, null, 2));
    } catch (err) {
      setStatus(`Error: ${err instanceof Error ? err.message : String(err)}`);
    } finally {
      setLoading(false);
    }
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Yoman</Text>
      <Text style={styles.subtitle}>Weekend 1 — backend ping</Text>
      <Button title={loading ? 'Calling...' : 'Ping /health'} onPress={pingHealth} disabled={loading} />
      <Text style={styles.response}>{status}</Text>
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
  response: {
    marginTop: 24,
    fontFamily: 'Courier',
    fontSize: 13,
    color: '#222',
    textAlign: 'center',
  },
});
