package dev.local.copycatlight;

import java.util.concurrent.ConcurrentMap;

/** Owned by each Level so data cannot cross saves or dimensions. */
public interface LightCacheAccess {
    ConcurrentMap<Long, ConcurrentMap<Long, MaterialLighting.Profile>> copycatlight$profiles();
}
