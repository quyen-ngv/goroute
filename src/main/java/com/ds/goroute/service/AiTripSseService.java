package com.ds.goroute.service;

import com.ds.goroute.entity.AiTripGenerationEvent;
import com.ds.goroute.mapper.AiTripGenerationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class AiTripSseService {
    private final AiTripGenerationMapper mapper;
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID jobId, long afterId) {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(jobId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> emitters.getOrDefault(jobId, new CopyOnWriteArrayList<>()).remove(emitter);
        emitter.onCompletion(remove); emitter.onTimeout(remove); emitter.onError(ignored -> remove.run());
        try {
            for (AiTripGenerationEvent event : mapper.findEventsAfter(jobId, afterId)) send(emitter, event);
        } catch (IOException error) {
            emitter.completeWithError(error);
        }
        return emitter;
    }

    /**
     * SSE comment heartbeat so proxies (nginx proxy_read_timeout) never see a silent stream
     * while the worker is between stages — an LLM call can easily stay quiet for minutes.
     */
    @Scheduled(fixedDelay = 15_000)
    public void heartbeat() {
        for (CopyOnWriteArrayList<SseEmitter> list : emitters.values()) {
            for (SseEmitter emitter : list) {
                try { emitter.send(SseEmitter.event().comment("heartbeat")); }
                catch (Exception error) { try { emitter.complete(); } catch (Exception ignored) { } }
            }
        }
    }

    public void publish(AiTripGenerationEvent event) {
        for (SseEmitter emitter : emitters.getOrDefault(event.getJobId(), new CopyOnWriteArrayList<>())) {
            try { send(emitter, event); } catch (IOException error) { emitter.complete(); }
        }
    }

    private void send(SseEmitter emitter, AiTripGenerationEvent event) throws IOException {
        emitter.send(SseEmitter.event().id(String.valueOf(event.getId())).name("progress").data(event));
        if (Set.of("COMPLETED", "FAILED", "CANCELLED").contains(event.getStatus())) emitter.complete();
    }
}
