package fr.astrocommunity.Greysi.Web.services;

import com.github.manolo8.darkbot.Main;
import eu.darkbot.api.game.entities.*;
import eu.darkbot.api.game.galaxy.GalaxyGate;
import eu.darkbot.api.game.galaxy.GalaxyInfo;
import eu.darkbot.api.game.galaxy.GateInfo;
import eu.darkbot.api.game.other.EntityInfo;
import eu.darkbot.api.managers.*;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * DataCollector - Collect all bot data for web transmission
 */
public class DataCollector {
    private final Main main;
    private final HeroAPI hero;
    private final BotAPI bot;
    private final StatsAPI stats;
    private final EntitiesAPI entities;
    private final StarSystemAPI starSystem;
    private final GroupAPI group;
    private final ConfigAPI config;
    private final GalaxySpinnerAPI galaxySpinner;

    public DataCollector(Main main, HeroAPI hero, BotAPI bot, StatsAPI stats, EntitiesAPI entities,
                         StarSystemAPI starSystem, GroupAPI group, ConfigAPI config,
                         GalaxySpinnerAPI galaxySpinner) {
        this.main = main;
        this.hero = hero;
        this.bot = bot;
        this.stats = stats;
        this.entities = entities;
        this.starSystem = starSystem;
        this.group = group;
        this.config = config;
        this.galaxySpinner = galaxySpinner;
    }

    /**
     * Collect basic bot info
     */
    public Map<String, Object> collectBasicInfo(String botId, String username, long userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("botId", botId);
        data.put("username", username);
        data.put("userId", userId);
        data.put("online", true);
        data.put("running", bot.isRunning());
        data.put("timestamp", System.currentTimeMillis());
        return data;
    }

    /**
     * Collect module info
     */
    public void collectModuleInfo(Map<String, Object> data) {
        try {
            if (bot.getModule() != null) {
                data.put("module", bot.getModule().getClass().getSimpleName());
                data.put("moduleStatus", bot.getModule().getStatus());
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Collect hero data
     */
    public void collectHeroData(Map<String, Object> data) {
        try {
            Map<String, Object> h = new HashMap<>();
            h.put("x", hero.getLocationInfo().getX());
            h.put("y", hero.getLocationInfo().getY());
            h.put("hp", hero.getHealth().getHp());
            h.put("maxHp", hero.getHealth().getMaxHp());
            h.put("hpPercent", hero.getHealth().hpPercent());
            h.put("shield", hero.getHealth().getShield());
            h.put("maxShield", hero.getHealth().getMaxShield());
            h.put("shieldPercent", hero.getHealth().shieldPercent());
            h.put("speed", hero.getSpeed());
            data.put("hero", h);
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Collect stats data - only send TOTAL values, let backend calculate earnings
     * Backend calculates: earned = current_total - session_start_snapshot
     */
    public void collectStatsData(Map<String, Object> data, SessionTracker sessionTracker) {
        try {
            Map<String, Object> s = new HashMap<>();
            // Send only TOTAL (absolute) values - backend will calculate earned
            s.put("credits", sessionTracker.getCurrentCredits());
            s.put("uridium", sessionTracker.getCurrentUridium());
            s.put("experience", sessionTracker.getCurrentExperience());
            s.put("honor", sessionTracker.getCurrentHonor());
            s.put("level", stats.getLevel());
            // DO NOT send _earned values - they become incorrect after death
            // Backend calculates them correctly using session snapshot
            s.put("cargo", stats.getStatValue(eu.darkbot.api.game.stats.Stats.General.CARGO));
            s.put("maxCargo", stats.getStatValue(eu.darkbot.api.game.stats.Stats.General.MAX_CARGO));
            data.put("stats", s);
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Collect map data
     */
    public void collectMapData(Map<String, Object> data) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("mapId", starSystem.getCurrentMap().getId());
            m.put("mapName", starSystem.getCurrentMap().getName());
            data.put("map", m);
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Collect config data
     */
    public void collectConfigData(Map<String, Object> data) {
        try {
            Map<String, Object> c = new HashMap<>();
            c.put("currentProfile", config.getCurrentProfile());
            c.put("availableProfiles", new ArrayList<>(config.getConfigProfiles()));
            c.put("faction", hero.getEntityInfo().getFaction().name());

            data.put("config", c);
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Collect performance data
     */
    public void collectPerformanceData(Map<String, Object> data, SessionTracker sessionTracker) {
        try {
            Map<String, Object> p = new HashMap<>();
            p.put("runtime", sessionTracker.getRuntime());
            p.put("ping", stats.getPing());
            data.put("performance", p);
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Collect entities for minimap
     */
    public void collectEntities(Map<String, Object> data) {
        try {
            List<Map<String, Object>> entList = new ArrayList<>();

            // NPCs
            for (Npc npc : entities.getNpcs()) {
                Map<String, Object> e = new HashMap<>();
                e.put("type", "npc");
                e.put("name", npc.getEntityInfo().getUsername());
                e.put("x", npc.getLocationInfo().getX());
                e.put("y", npc.getLocationInfo().getY());
                e.put("hp", npc.getHealth().hpPercent());
                entList.add(e);
            }

            // Players
            for (Player player : entities.getPlayers()) {
                Map<String, Object> e = new HashMap<>();
                EntityInfo info = player.getEntityInfo();

                String playerType = determinePlayerType(player, info);

                e.put("type", playerType);
                e.put("name", info.getUsername());
                e.put("clan", info.getClanTag());
                e.put("x", player.getLocationInfo().getX());
                e.put("y", player.getLocationInfo().getY());
                entList.add(e);
            }

            // Boxes
            for (Box box : entities.getBoxes()) {
                Map<String, Object> e = new HashMap<>();
                e.put("type", "box");
                e.put("x", box.getLocationInfo().getX());
                e.put("y", box.getLocationInfo().getY());
                entList.add(e);
            }

            // Portals
            for (Portal portal : entities.getPortals()) {
                Map<String, Object> e = new HashMap<>();
                e.put("type", "portal");
                e.put("x", portal.getLocationInfo().getX());
                e.put("y", portal.getLocationInfo().getY());
                entList.add(e);
            }

            // Bases
            for (Station station : entities.getStations()) {
                Map<String, Object> e = new HashMap<>();
                e.put("type", "base");
                e.put("x", station.getLocationInfo().getX());
                e.put("y", station.getLocationInfo().getY());
                entList.add(e);
            }

            // Battle Stations (CBS)
            for (BattleStation bs : entities.getBattleStations()) {
                Map<String, Object> e = new HashMap<>();
                String cbsType = "cbs";

                if (bs instanceof BattleStation.Hull) {
                    cbsType = "cbs_hull";
                } else if (bs instanceof BattleStation.Module) {
                    cbsType = "cbs_module";
                } else if (bs instanceof BattleStation.Asteroid) {
                    cbsType = "cbs_asteroid";
                }

                e.put("type", cbsType);
                e.put("x", bs.getLocationInfo().getX());
                e.put("y", bs.getLocationInfo().getY());

                try {
                    EntityInfo info = bs.getEntityInfo();
                    if (info != null) {
                        e.put("owner", info.getUsername());
                        e.put("isOwned", bs.isOwned());
                        e.put("isEnemy", info.isEnemy());
                    }
                } catch (Exception ex) {
                    // Ignore
                }

                entList.add(e);
            }

            data.put("entities", entList);
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Determine player type (enemy, ally, group, etc.)
     */
    private String determinePlayerType(Player player, EntityInfo info) {
        boolean isInGroup = group.hasGroup() && group.getMember(player) != null;

        if (isInGroup) {
            return "group";
        } else if (!info.isEnemy()) {
            EntityInfo.Diplomacy diplomacy = info.getClanDiplomacy();
            if (diplomacy == EntityInfo.Diplomacy.ALLIED) {
                return "ally";
            } else if (diplomacy == EntityInfo.Diplomacy.NOT_ATTACK_PACT) {
                return "nap";
            } else {
                return "neutral";
            }
        } else {
            return "enemy";
        }
    }

    /**
     * Collect target info
     */
    public void collectTargetInfo(Map<String, Object> data) {
        try {
            if (hero.getTarget() != null) {
                Map<String, Object> t = new HashMap<>();
                t.put("x", hero.getTarget().getLocationInfo().getX());
                t.put("y", hero.getTarget().getLocationInfo().getY());

                if (hero.getTarget() instanceof Ship) {
                    Ship ship = (Ship) hero.getTarget();
                    t.put("name", ship.getEntityInfo().getUsername());
                    t.put("hp", ship.getHealth().getHp());
                    t.put("maxHp", ship.getHealth().getMaxHp());
                    t.put("shield", ship.getHealth().getShield());
                    t.put("maxShield", ship.getHealth().getMaxShield());
                } else if (hero.getTarget() instanceof Npc) {
                    Npc npc = (Npc) hero.getTarget();
                    t.put("name", npc.getEntityInfo().getUsername());
                    t.put("hp", npc.getHealth().getHp());
                    t.put("maxHp", npc.getHealth().getMaxHp());
                    t.put("shield", npc.getHealth().getShield());
                    t.put("maxShield", npc.getHealth().getMaxShield());
                }
                data.put("target", t);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Collect galaxy gates info
     */
    public void collectGalaxyInfo(Map<String, Object> data) {
        try {
            GalaxyInfo galaxyInfo = galaxySpinner.getGalaxyInfo();
            if (galaxyInfo != null) {
                Map<String, Object> galaxy = new HashMap<>();
                galaxy.put("uridium", galaxyInfo.getUridium());
                galaxy.put("freeEnergy", galaxyInfo.getFreeEnergy());
                galaxy.put("energyCost", galaxyInfo.getEnergyCost());
                galaxy.put("spinSale", galaxyInfo.isSpinSale());
                galaxy.put("spinSalePercent", galaxyInfo.getSpinSalePercentage());
                galaxy.put("galaxyGateDay", galaxyInfo.isGalaxyGateDay());
                galaxy.put("bonusRewardsDay", galaxyInfo.isBonusRewardsDay());

                List<Map<String, Object>> gates = new ArrayList<>();
                for (GalaxyGate gate : GalaxyGate.values()) {
                    try {
                        GateInfo info = galaxyInfo.getGateInfo(gate);
                        if (info != null) {
                            Map<String, Object> g = new HashMap<>();
                            g.put("name", gate.getName());
                            g.put("id", gate.getId());
                            g.put("currentParts", info.getCurrentParts());
                            g.put("totalParts", info.getTotalParts());
                            g.put("currentWave", info.getCurrentWave());
                            g.put("totalWave", info.getTotalWave());
                            g.put("livesLeft", info.getLivesLeft());
                            g.put("onMap", info.isOnMap());
                            g.put("completed", info.isCompleted());
                            gates.add(g);
                        }
                    } catch (Exception ex) {
                        // Ignore
                    }
                }
                galaxy.put("gates", gates);
                data.put("galaxy", galaxy);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * Collect hangars (ships owned by the player)
     */
    public void collectHangars(Map<String, Object> data) {
        Map<String, Object> hangarsData = new HashMap<>();

        try {
            // Access hangar manager via backpage API
            if (main != null && main.backpage != null && main.backpage.hangarManager != null) {
                // Force update from backpage
                main.backpage.hangarManager.updateHangarList();

                var response = main.backpage.hangarManager.getHangarList();

                if (response != null && response.getIsError() == 0) {
                    var responseData = response.getData();
                    if (responseData != null) {
                        var ret = responseData.getRet();
                        if (ret != null) {
                            var shipInfos = ret.getShipInfos();

                            Map<String, String> hangarsMap = new HashMap<>();

                            if (shipInfos != null) {
                                for (var ship : shipInfos) {
                                    // Only include owned ships (owned > 0)
                                    if (ship.getOwned() > 0) {
                                        String shipName = ship.getLootId();  // "ship_phoenix", "ship_goliath", etc.
                                        String lootId = String.valueOf(ship.getHangarId());

                                        // Normalize ship name (remove "ship_" prefix and capitalize)
                                        String normalizedName = normalizeShipName(shipName);
                                        hangarsMap.put(normalizedName, lootId);
                                    }
                                }
                            }

                            hangarsData.put("hangars", hangarsMap);
                            hangarsData.put("count", hangarsMap.size());
                        }
                    }
                }
            }

        } catch (Exception e) {
            // Silently ignore errors - hangars will just be empty
        }

        // Always add hangars to data (even if empty)
        data.put("hangars", hangarsData.getOrDefault("hangars", new HashMap<>()));
        data.put("hangarsCount", hangarsData.getOrDefault("count", 0));
    }

    /**
     * Normalize ship name (from "ship_goliath" to "Goliath")
     */
    private String normalizeShipName(String apiShipName) {
        // Remove 'ship_' prefix
        String name = apiShipName.replace("ship_", "");

        // Handle hyphenated names (G-Veteran, V-Revenge, etc.)
        if (name.contains("-")) {
            String[] parts = name.split("-");
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) result.append("-");
                String part = parts[i];
                result.append(part.substring(0, 1).toUpperCase());
                result.append(part.substring(1).toLowerCase());
            }
            return result.toString();
        }

        // Capitalize first letter
        return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
    }
}
