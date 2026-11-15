package com.opssentinel.diagnosis;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Powers the dashboard's "ask about this incident" follow-up chat box. */
@RestController
public class AskController {

    private final GeminiClient geminiClient;

    public AskController(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    @PostMapping("/api/incidents/ask")
    public AskAnswer ask(@RequestBody AskIncidentRequest request) {
        String prompt = """
                You are the Diagnosis Agent for an AIOps incident-response system,
                answering a follow-up question from an engineer about an incident
                you already investigated. Be concise — 2-4 sentences. Answer only
                from the context given; if the context doesn't cover it, say so
                plainly rather than guessing.

                Incident context:
                %s

                Engineer's question: %s
                """.formatted(request.context(), request.question());

        String answer = geminiClient.generateFreeText(prompt);
        return new AskAnswer(answer);
    }
}