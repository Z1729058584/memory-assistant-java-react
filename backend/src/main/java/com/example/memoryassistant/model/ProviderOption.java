package com.example.memoryassistant.model;

import java.util.List;

public record ProviderOption(String id, String label, String baseUrl, boolean available, List<ModelOption> models) {}
