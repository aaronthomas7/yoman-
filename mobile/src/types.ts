export type Entry = {
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

export type RootStackParamList = {
  Home: undefined;
  Detail: { id: string };
  Record: undefined;
};
