package com.yoman.backend.transcription;

public interface TranscriptionService {
    String transcribe(byte[] audio, String filename, String contentType);
}
