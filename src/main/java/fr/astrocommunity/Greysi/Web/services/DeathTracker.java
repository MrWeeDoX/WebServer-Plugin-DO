package fr.astrocommunity.Greysi.Web.services;

import eu.darkbot.api.managers.HeroAPI;
import eu.darkbot.api.managers.StarSystemAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeathTracker - Track player deaths
 */
public class DeathTracker {
    private final HeroAPI hero;
    private final StarSystemAPI starSystem;
    private int deathCount = 0;
    private List<Map<String, Object>> deathLog = new ArrayList<>();
    private boolean wasAlive = true;

    public DeathTracker(HeroAPI hero, StarSystemAPI starSystem) {
        this.hero = hero;
        this.starSystem = starSystem;
    }

    /**
     * Update death tracking - call this every tick
     */
    public void tick() {
        try {
            boolean alive = hero.getHealth().getHp() > 0;
            if (wasAlive && !alive) {
                recordDeath();
            }
            wasAlive = alive;
        } catch (Exception e) {
            // Ignore errors
        }
    }

    /**
     * Record a death event
     */
    private void recordDeath() {
        deathCount++;
        Map<String, Object> death = new HashMap<>();
        death.put("time", System.currentTimeMillis());
        try {
            death.put("map", starSystem.getCurrentMap().getName());
        } catch (Exception e) {
            death.put("map", "Unknown");
        }
        deathLog.add(death);
    }

    /**
     * Get total death count
     */
    public int getDeathCount() {
        return deathCount;
    }

    /**
     * Get death log
     */
    public List<Map<String, Object>> getDeathLog() {
        return deathLog;
    }

    /**
     * Reset death tracking
     */
    public void reset() {
        deathCount = 0;
        deathLog.clear();
        wasAlive = true;
    }
}
