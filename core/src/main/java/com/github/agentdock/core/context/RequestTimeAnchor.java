package com.github.agentdock.core.context;

import java.time.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** 将宿主时间与时区固定为本次执行的相对日期基准。 */
public final class RequestTimeAnchor {
    private RequestTimeAnchor() { }

    public static Map<String, String> from(Map<String, Object> attributes) {
        Object runtime = attributes == null ? null : attributes.get("runtimeEnvironment");
        Map<?, ?> environment = runtime instanceof Map<?, ?> map ? map : Map.of();
        Instant instant;
        ZoneId zone;
        try { instant = Instant.parse(String.valueOf(environment.get("requestTimeUtc"))); }
        catch (RuntimeException ignored) { instant = Instant.now(); }
        try { zone = ZoneId.of(String.valueOf(environment.get("userTimezone"))); }
        catch (RuntimeException ignored) { zone = ZoneOffset.UTC; }
        LocalDate today = instant.atZone(zone).toLocalDate();
        Map<String, String> anchor = new LinkedHashMap<>();
        anchor.put("requestTimeUtc", instant.toString());
        anchor.put("timezone", zone.getId());
        anchor.put("today", today.toString());
        anchor.put("tomorrow", today.plusDays(1).toString());
        anchor.put("dayAfterTomorrow", today.plusDays(2).toString());
        return Map.copyOf(anchor);
    }
}
