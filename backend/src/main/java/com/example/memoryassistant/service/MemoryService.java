package com.example.memoryassistant.service;

import com.example.memoryassistant.config.AppProperties;
import com.example.memoryassistant.entity.ConversationTurnEntity;
import com.example.memoryassistant.entity.MemoryEntity;
import com.example.memoryassistant.model.MemoryCandidate;
import com.example.memoryassistant.model.MemoryOut;
import com.example.memoryassistant.model.MemoryReviewOut;
import com.example.memoryassistant.model.MemoryReviewStats;
import com.example.memoryassistant.repository.ConversationTurnRepository;
import com.example.memoryassistant.repository.MemoryRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemoryService {
    private final MemoryRepository memoryRepo;
    private final ConversationTurnRepository turnRepo;
    private final AppProperties props;

    public MemoryService(MemoryRepository memoryRepo, ConversationTurnRepository turnRepo, AppProperties props) {
        this.memoryRepo = memoryRepo;
        this.turnRepo = turnRepo;
        this.props = props;
    }

    @Transactional
    public List<MemoryEntity> listMemories(String userId, boolean includeInactive, boolean includeExpired) {
        // 每次查询前先跑生命周期，避免前端读到过期但未归档的数据。
        applyLifecycle(userId);
        LocalDateTime now = LocalDateTime.now();
        return memoryRepo.findByUserId(userId).stream()
            .filter(m -> includeInactive || "active".equalsIgnoreCase(m.getStatus()))
            .filter(m -> includeExpired || !isExpired(m, now))
            .sorted(Comparator.comparing(MemoryEntity::getUpdatedAt, Comparator.nullsLast(LocalDateTime::compareTo)).reversed())
            .toList();
    }

    @Transactional
    public List<MemoryEntity> retrieveRelevantMemories(String userId, String message) {
        applyLifecycle(userId);
        LocalDateTime now = LocalDateTime.now();
        List<String> tokens = tokenize(message);
        List<Scored> scored = new ArrayList<>();
        for (MemoryEntity m : memoryRepo.findByUserIdAndStatus(userId, "active")) {
            if (isExpired(m, now)) continue;
            String hay = (m.getCategory() + " " + m.getMemoryKey() + " " + m.getValue()).toLowerCase(Locale.ROOT);
            // 总分 = 关键词命中数 + 置信度权重；置顶记忆额外加分。
            double score = tokens.stream().filter(t -> !t.isBlank() && hay.contains(t)).count() + effectiveConfidence(m, now) * 10;
            if (Boolean.TRUE.equals(m.getPinned())) score += 2;
            scored.add(new Scored(score, m));
        }
        scored.sort(Comparator.comparing(Scored::score).reversed());
        // 正常返回正分结果；如果全部 <= 0，则兜底返回 TopN，避免上下文完全为空。
        List<MemoryEntity> top = scored.stream().filter(s -> s.score() > 0).map(Scored::memory).limit(props.getMemoryLimit()).toList();
        if (top.isEmpty()) {
            top = scored.stream().map(Scored::memory).limit(props.getMemoryLimit()).toList();
        }
        // 命中的记忆会提升访问次数，用于后续衰减锚点计算。
        markAccessed(top, now);
        return top;
    }

    @Transactional
    public List<MemoryEntity> upsertMemories(String userId, List<MemoryCandidate> candidates) {
        LocalDateTime now = LocalDateTime.now();
        List<MemoryEntity> out = new ArrayList<>();
        for (MemoryCandidate c : dedupe(candidates)) {
            String norm = normalize(c.value());
            // 先按 (category,key) 合并，再按标准化 value 合并，减少重复记忆。
            Optional<MemoryEntity> existing = memoryRepo.findFirstByUserIdAndCategoryAndMemoryKey(userId, c.category(), c.key());
            if (existing.isEmpty()) existing = memoryRepo.findFirstByUserIdAndCategoryAndNormalizedValueAndStatus(userId, c.category(), norm, "active");
            MemoryEntity e = existing.orElseGet(MemoryEntity::new);
            if (e.getId() == null) {
                e.setUserId(userId); e.setCategory(c.category()); e.setMemoryKey(c.key());
                e.setAccessCount(0); e.setStatus("active"); e.setLastAccessedAt(now);
            }
            e.setValue(c.value());
            e.setNormalizedValue(norm);
            e.setConfidence(e.getConfidence() == null ? c.confidence() : Math.max(e.getConfidence(), c.confidence()));
            e.setSource(blank(c.source()) ? "chat" : c.source());
            e.setPinned(Boolean.TRUE.equals(e.getPinned()) || c.pinned());
            if (c.expiresAt() != null) e.setExpiresAt(c.expiresAt());
            // ongoing_task 未显式设置过期时间时，自动给一个默认 TTL。
            if (e.getExpiresAt() == null && "ongoing_task".equalsIgnoreCase(c.category())) e.setExpiresAt(now.plusDays(props.getOngoingTaskTtlDays()));
            e.setStatus("active");
            e.setUpdatedAt(now);
            out.add(memoryRepo.save(e));
        }
        return out;
    }

    @Transactional
    public MemoryEntity createMemory(String userId, MemoryCandidate candidate) {
        return upsertMemories(userId, List.of(candidate)).getFirst();
    }

    @Transactional
    public MemoryEntity updateMemory(String userId, Long id, String category, String key, String value,
                                     Double confidence, String source, Boolean pinned, String status,
                                     LocalDateTime expiresAt, Boolean clearExpiry) {
        MemoryEntity m = memoryRepo.findByIdAndUserId(id, userId).orElseThrow(() -> new IllegalArgumentException("Memory not found"));
        if (category != null) m.setCategory(category);
        if (key != null) m.setMemoryKey(key);
        if (value != null) { m.setValue(value); m.setNormalizedValue(normalize(value)); }
        if (confidence != null) m.setConfidence(confidence);
        if (source != null) m.setSource(source);
        if (pinned != null) m.setPinned(pinned);
        if (status != null) m.setStatus(status);
        // clearExpiry 优先级高于 expiresAt，前端可以显式清除过期时间。
        if (Boolean.TRUE.equals(clearExpiry)) m.setExpiresAt(null); else if (expiresAt != null) m.setExpiresAt(expiresAt);
        return memoryRepo.save(m);
    }

    @Transactional
    public boolean deleteMemory(String userId, Long id) {
        Optional<MemoryEntity> m = memoryRepo.findByIdAndUserId(id, userId);
        if (m.isEmpty()) return false;
        memoryRepo.delete(m.get());
        return true;
    }

    @Transactional
    public MemoryReviewOut reviewMemories(String userId) {
        applyLifecycle(userId);
        LocalDateTime now = LocalDateTime.now();
        List<MemoryEntity> all = memoryRepo.findByUserId(userId);
        List<MemoryEntity> active = all.stream().filter(m -> "active".equalsIgnoreCase(m.getStatus()) && !isExpired(m, now)).toList();
        // stale 定义为“接近低置信阈值”的活跃记忆，便于前端提醒用户确认。
        List<MemoryOut> stale = active.stream().filter(m -> effectiveConfidence(m, now) <= props.getLowConfidenceThreshold() + 0.1).limit(10).map(MemoryOut::fromEntity).toList();
        // soon 定义为在配置窗口内即将过期的活跃记忆。
        List<MemoryOut> soon = active.stream().filter(m -> m.getExpiresAt() != null && !m.getExpiresAt().isBefore(now) && !m.getExpiresAt().isAfter(now.plusDays(props.getExpiringSoonDays()))).limit(10).map(MemoryOut::fromEntity).toList();
        Map<String, Integer> by = new LinkedHashMap<>();
        for (MemoryEntity m : active) by.merge(m.getCategory(), 1, Integer::sum);
        MemoryReviewStats stats = new MemoryReviewStats(all.size(), active.size(),
            (int) all.stream().filter(m -> Boolean.TRUE.equals(m.getPinned())).count(),
            (int) all.stream().filter(m -> isExpired(m, now)).count(),
            (int) all.stream().filter(m -> "archived".equalsIgnoreCase(m.getStatus())).count(),
            stale.size(), soon.size());
        return new MemoryReviewOut(stats, by, stale, soon);
    }

    @Transactional
    public ConversationTurnEntity addTurn(String userId, String role, String content) {
        ConversationTurnEntity t = new ConversationTurnEntity();
        t.setUserId(userId); t.setRole(role); t.setContent(content);
        return turnRepo.save(t);
    }

    public List<ConversationTurnEntity> recentTurns(String userId) {
        return recentTurns(userId, props.getRecentTurnsLimit());
    }

    public List<ConversationTurnEntity> recentTurns(String userId, int limit) {
        int safeLimit = Math.max(1, limit);
        List<ConversationTurnEntity> turns = turnRepo.findByUserId(
            userId,
            PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<ConversationTurnEntity> out = new ArrayList<>(turns);
        out.sort(Comparator.comparing(ConversationTurnEntity::getCreatedAt));
        return out;
    }

    @Transactional
    public void applyLifecycle(String userId) {
        LocalDateTime now = LocalDateTime.now();
        List<MemoryEntity> all = memoryRepo.findByUserId(userId);
        boolean changed = false;
        for (MemoryEntity m : all) {
            // 到期即归档，保证过期数据不会继续参与召回。
            if (isExpired(m, now)) { if (!"archived".equalsIgnoreCase(m.getStatus())) { m.setStatus("archived"); changed = true; } continue; }
            // 置顶或非 active 状态不参与自动衰减归档。
            if (Boolean.TRUE.equals(m.getPinned()) || !"active".equalsIgnoreCase(m.getStatus())) continue;
            if (effectiveConfidence(m, now) < props.getLowConfidenceThreshold()) { m.setStatus("archived"); changed = true; }
        }
        if (changed) memoryRepo.saveAll(all);
    }

    private void markAccessed(List<MemoryEntity> memories, LocalDateTime now) {
        for (MemoryEntity m : memories) {
            int count = m.getAccessCount() == null ? 0 : m.getAccessCount();
            m.setAccessCount(count + 1);
            m.setLastAccessedAt(now);
        }
        if (!memories.isEmpty()) memoryRepo.saveAll(memories);
    }

    private boolean isExpired(MemoryEntity m, LocalDateTime now) { return m.getExpiresAt() != null && !m.getExpiresAt().isAfter(now); }
    private double conf(MemoryEntity m) { return m.getConfidence() == null ? 1.0 : m.getConfidence(); }
    private double effectiveConfidence(MemoryEntity m, LocalDateTime now) {
        // 置顶或关闭衰减时，直接使用原始置信度。
        if (Boolean.TRUE.equals(m.getPinned()) || !props.isMemoryDecayEnabled()) return conf(m);
        // 衰减锚点优先取最近访问时间，否则回退到更新时间/创建时间。
        LocalDateTime anchor = m.getLastAccessedAt() != null ? m.getLastAccessedAt() : (m.getUpdatedAt() != null ? m.getUpdatedAt() : m.getCreatedAt());
        if (anchor == null) return conf(m);
        // 每过一个 interval 下降 decayFactor，最低降到 0。
        long steps = Math.max(Duration.between(anchor, now).toDays(), 0) / Math.max(props.getMemoryDecayIntervalDays(), 1);
        return Math.max(conf(m) - steps * props.getMemoryDecayFactor(), 0.0);
    }
    private String normalize(String v) { return v == null ? "" : v.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    private boolean blank(String v) { return v == null || v.isBlank(); }
    private List<String> tokenize(String t) { return normalize(t).replaceAll("[^a-z0-9\\s]", " ").lines().flatMap(s -> java.util.Arrays.stream(s.split("\\s+"))).filter(s -> s.length() > 1).distinct().limit(12).toList(); }
    private List<MemoryCandidate> dedupe(List<MemoryCandidate> in) {
        if (in == null) return List.of();
        Map<String, MemoryCandidate> m = new LinkedHashMap<>();
        for (MemoryCandidate c : in) {
            if (c == null || blank(c.category()) || blank(c.key()) || blank(c.value())) continue;
            String k = (c.category() + "|" + c.key() + "|" + normalize(c.value())).toLowerCase(Locale.ROOT);
            MemoryCandidate old = m.get(k);
            // 重复候选时保留置信度更高的一条。
            if (old == null || c.confidence() > old.confidence()) m.put(k, c);
        }
        return new ArrayList<>(m.values());
    }

    private record Scored(double score, MemoryEntity memory) {}
}
