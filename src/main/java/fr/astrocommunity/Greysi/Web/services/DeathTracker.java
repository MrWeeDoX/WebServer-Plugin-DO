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
    private boolean justDied = false;
    private boolean justRespawned = false;

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
            
            // Detect death
            if (wasAlive && !alive) {
                recordDeath();
                justDied = true;
                justRespawned = false;
            }
            
            // Detect respawn (was dead, now alive)
            if (!wasAlive && alive) {
                justRespawned = true;
                justDied = false;
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
     * Check if bot just died this tick
     */
    public boolean hasJustDied() {
        boolean result = justDied;
        justDied = false; // Reset flag after reading
        return result;
    }
    
    /**
     * Check if bot just respawned this tick
     */
    public boolean hasJustRespawned() {
        boolean result = justRespawned;
        justRespawned = false; // Reset flag after reading
        return result;
    }

    /**
     * Reset death tracking
     */
    public void reset() {
        deathCount = 0;
        deathLog.clear();
        wasAlive = true;
        justDied = false;
        justRespawned = false;
    }
}
