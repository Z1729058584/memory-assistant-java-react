package com.example.memoryassistant.model;

import java.util.List;

public record AppConfigOut(boolean aiAvailable, String provider, String model, String source, List<ProviderOption> providers) {}
