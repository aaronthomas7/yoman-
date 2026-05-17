import { StyleSheet, Text, View } from 'react-native';
import type { Entry } from './types';

export function EntryView({ entry }: { entry: Entry }) {
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
