import { useCallback, useState } from 'react';
import { useFocusEffect } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import {
  ActivityIndicator,
  FlatList,
  Pressable,
  RefreshControl,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { fetchEntries } from '../api';
import type { Entry, RootStackParamList } from '../types';

type Props = NativeStackScreenProps<RootStackParamList, 'Home'>;

type Section = { title: string; data: Entry[] };

function groupByDate(entries: Entry[]): Section[] {
  const map = new Map<string, Entry[]>();
  for (const e of entries) {
    const key = e.entryDate;
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(e);
  }
  return Array.from(map.entries())
    .sort(([a], [b]) => (a < b ? 1 : -1))
    .map(([title, data]) => ({ title, data }));
}

function formatDate(iso: string): string {
  // iso = "YYYY-MM-DD"
  const d = new Date(iso + 'T00:00:00');
  return d.toLocaleDateString(undefined, { weekday: 'long', month: 'short', day: 'numeric' });
}

function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' });
}

export function HomeScreen({ navigation }: Props) {
  const [entries, setEntries] = useState<Entry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const load = useCallback(async () => {
    setError(null);
    try {
      const data = await fetchEntries();
      setEntries(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
    }, [load]),
  );

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await load();
    } finally {
      setRefreshing(false);
    }
  }, [load]);

  if (entries === null) {
    return (
      <View style={styles.center}>
        <ActivityIndicator />
      </View>
    );
  }

  const sections = groupByDate(entries);
  // Flatten into a FlatList with sticky-ish date headers (FlatList is simpler than SectionList here).
  type Row =
    | { kind: 'header'; date: string }
    | { kind: 'item'; entry: Entry };
  const rows: Row[] = sections.flatMap<Row>((s) => [
    { kind: 'header', date: s.title },
    ...s.data.map<Row>((e) => ({ kind: 'item', entry: e })),
  ]);

  return (
    <View style={styles.container}>
      <FlatList
        data={rows}
        keyExtractor={(row, i) => (row.kind === 'header' ? `h-${row.date}` : row.entry.id) + ':' + i}
        ListHeaderComponent={
          error ? (
            <View style={styles.errorBox}>
              <Text style={styles.errorText}>{error}</Text>
            </View>
          ) : null
        }
        ListEmptyComponent={
          <View style={styles.empty}>
            <Text style={styles.emptyTitle}>No entries yet</Text>
            <Text style={styles.emptyBody}>Tap the + button to record your first entry.</Text>
          </View>
        }
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        renderItem={({ item }) =>
          item.kind === 'header' ? (
            <Text style={styles.dateHeader}>{formatDate(item.date)}</Text>
          ) : (
            <Pressable
              style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
              onPress={() => navigation.navigate('Detail', { id: item.entry.id })}
            >
              <View style={styles.rowHead}>
                <Text style={styles.rowTime}>{formatTime(item.entry.createdAt)}</Text>
                {item.entry.mood ? <Text style={styles.rowMood}>{item.entry.mood}</Text> : null}
              </View>
              <Text style={styles.rowSummary} numberOfLines={2}>
                {item.entry.summary}
              </Text>
              {item.entry.tags && item.entry.tags.length > 0 ? (
                <Text style={styles.rowTags} numberOfLines={1}>
                  {item.entry.tags.map((t) => `#${t}`).join('  ')}
                </Text>
              ) : null}
            </Pressable>
          )
        }
        contentContainerStyle={rows.length === 0 ? styles.flexFill : undefined}
      />

      <Pressable style={styles.fab} onPress={() => navigation.navigate('Record')}>
        <Text style={styles.fabText}>+</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  center: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  flexFill: { flexGrow: 1, justifyContent: 'center' },
  empty: { padding: 32, alignItems: 'center' },
  emptyTitle: { fontSize: 16, fontWeight: '600', color: '#333', marginBottom: 4 },
  emptyBody: { fontSize: 13, color: '#888', textAlign: 'center' },
  errorBox: { backgroundColor: '#fee', padding: 12, margin: 16, borderRadius: 8 },
  errorText: { color: '#900', fontSize: 12 },
  dateHeader: {
    fontSize: 12,
    fontWeight: '600',
    color: '#888',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
    marginTop: 24,
    marginBottom: 8,
    paddingHorizontal: 20,
  },
  row: {
    paddingHorizontal: 20,
    paddingVertical: 12,
    borderBottomColor: '#f0f0f0',
    borderBottomWidth: 1,
  },
  rowPressed: { backgroundColor: '#fafafa' },
  rowHead: { flexDirection: 'row', justifyContent: 'space-between', marginBottom: 4 },
  rowTime: { fontSize: 12, color: '#888' },
  rowMood: { fontSize: 12, color: '#669' },
  rowSummary: { fontSize: 15, lineHeight: 20, color: '#111' },
  rowTags: { fontSize: 11, color: '#888', marginTop: 4 },
  fab: {
    position: 'absolute',
    bottom: 32,
    right: 24,
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: '#111',
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowOffset: { width: 0, height: 3 },
    shadowRadius: 6,
    elevation: 4,
  },
  fabText: { color: '#fff', fontSize: 30, lineHeight: 32 },
});
