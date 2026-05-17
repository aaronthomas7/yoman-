package com.yoman.backend.structuring;

public interface StructuringService {
    /**
     * Turn a raw transcript into a structured diary entry.
     *
     * Weekend 4 takes the transcript alone. Weekend 5+ will add a
     * UserContext parameter (preferred name, languages, tradition, important
     * people, recent tags) and rebuild the prompt per user.
     */
    StructuredEntry structure(String transcript);
}
