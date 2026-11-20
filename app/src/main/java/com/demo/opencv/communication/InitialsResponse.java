package com.demo.opencv.communication;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;

/** Reads the same short JSON response from all Chat Completions providers. */
public final class InitialsResponse {
    private List<InitialsCandidate> candidates;

    public static List<InitialsCandidate> parse(String raw, String requested) {
        List<InitialsCandidate> valid = new ArrayList<>();
        int first = raw.indexOf('{'), last = raw.lastIndexOf('}');
        if (first < 0 || last < first) return valid;
        try {
            InitialsResponse response = new Gson().fromJson(raw.substring(first, last + 1), InitialsResponse.class);
            if (response == null || response.candidates == null) return valid;
            for (InitialsCandidate candidate : response.candidates) {
                if (candidate == null || candidate.text == null || !candidate.matches(requested)) continue;
                boolean duplicate = false;
                for (InitialsCandidate existing : valid) if (existing.text.equals(candidate.text)) duplicate = true;
                if (!duplicate) valid.add(candidate);
                if (valid.size() == 3) break;
            }
        } catch (JsonParseException error) {
            // The user can retry if a provider does not follow the response format.
            return valid;
        }
        return valid;
    }
}
