package fr.astrocommunity.Greysi.Web.services;

import eu.darkbot.api.game.stats.Stats;
import eu.darkbot.api.managers.StatsAPI;

/**
 * SessionTracker - Track session statistics and earnings
 */
public class SessionTracker {
    private final StatsAPI stats;
    private final long sessionStartTime;
    private final long creditsStart;
    private final long uridiumStart;
    private final long experienceStart;
    private final long honorStart;

    public SessionTracker(StatsAPI stats) {
        this.stats = stats;
        this.sessionStartTime = System.currentTimeMillis();
        this.creditsStart = (long) stats.getStatValue(Stats.General.CREDITS);
        this.uridiumStart = (long) stats.getStatValue(Stats.General.URIDIUM);
        this.experienceStart = (long) stats.getStatValue(Stats.General.EXPERIENCE);
        this.honorStart = (long) stats.getStatValue(Stats.General.HONOR);
    }

    /**
     * Get current credits
     */
    public long getCurrentCredits() {
        return (long) stats.getStatValue(Stats.General.CREDITS);
    }

    /**
     * Get current uridium
     */
    public long getCurrentUridium() {
        return (long) stats.getStatValue(Stats.General.URIDIUM);
    }

    /**
     * Get current experience
     */
    public long getCurrentExperience() {
        return (long) stats.getStatValue(Stats.General.EXPERIENCE);
    }

    /**
     * Get current honor
     */
    public long getCurrentHonor() {
        return (long) stats.getStatValue(Stats.General.HONOR);
    }

    /**
     * Get credits earned since session start
     */
    public long getCreditsEarned() {
        return getCurrentCredits() - creditsStart;
    }

    /**
     * Get uridium earned since session start
     */
    public long getUridiumEarned() {
        return getCurrentUridium() - uridiumStart;
    }

    /**
     * Get experience earned since session start
     */
    public long getExperienceEarned() {
        return getCurrentExperience() - experienceStart;
    }

    /**
     * Get honor earned since session start
     */
    public long getHonorEarned() {
        return getCurrentHonor() - honorStart;
    }

    /**
     * Get session runtime in milliseconds
     */
    public long getRuntime() {
        return System.currentTimeMillis() - sessionStartTime;
    }

    /**
     * Get session start time
     */
    public long getSessionStartTime() {
        return sessionStartTime;
    }
}
