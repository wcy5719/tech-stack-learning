package com.wc.aiservice.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {
    private String response;
    private String userId;
    private long timestamp;
    private String sessionId;
    private String intent;
    private Long responseTimeMs;
    
    @JsonProperty("cached")
    @Builder.Default
    private boolean cached = false;
}
