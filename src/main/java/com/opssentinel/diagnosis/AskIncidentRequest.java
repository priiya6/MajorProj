package com.opssentinel.diagnosis;

public record AskIncidentRequest(String incidentId, String context, String question) {}