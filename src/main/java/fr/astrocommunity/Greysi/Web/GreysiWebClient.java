package fr.astrocommunity.Greysi.Web;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.core.itf.Behaviour;
import com.github.manolo8.darkbot.extensions.features.Feature;
import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Editor;
import eu.darkbot.api.config.annotations.Option;
import eu.darkbot.api.config.util.OptionEditor;
import eu.darkbot.api.extensions.Configurable;
import eu.darkbot.api.managers.*;
import fr.astrocommunity.Greysi.Web.network.WebApiClient;
import fr.astrocommunity.Greysi.Web.services.DataCollector;
import fr.astrocommunity.Greysi.Web.services.DeathTracker;
import fr.astrocommunity.Greysi.Web.services.SessionTracker;
import fr.astrocommunity.Greysi.Web.utils.JsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Greysi Web Client - by Greysi/AstroCommunity
 * Envoie les données du bot vers un serveur web central
 * Package: fr.astrocommunity.Greysi.Web
 */
@Feature(name = "Greysi Web Client", description = "Send bot data to web server - by Greysi")
public class GreysiWebClient implements Behaviour, Configurable<GreysiWebClient.Config> {

    private static final String WEB_SERVER_URL = "https://do.astrocommunity.fr/api/bot/update";
    private static final int UPDATE_INTERVAL = 2000; // 2 secondes
    private static GreysiWebClient instance; // Static reference for editor callback

    // Services
    private DataCollector dataCollector;
    private final SessionTracker sessionTracker;
    private final DeathTracker deathTracker;
    private final HeroAPI hero;
    private final BotAPI bot;
    private final StatsAPI stats;
    private final EntitiesAPI entities;
    private final StarSystemAPI starSystem;
    private final GroupAPI group;
    private final ConfigAPI config;
    private final GalaxySpinnerAPI galaxySpinner;

    // Network
    private WebApiClient apiClient;
    private Timer timer;
    private String apiKey = null;
    private String botId;
    private boolean firstDataSent = false;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public GreysiWebClient(HeroAPI hero, BotAPI bot, StatsAPI stats, EntitiesAPI entities,
                           StarSystemAPI starSystem, GroupAPI group, PetAPI pet, ConfigAPI config,
                           GalaxySpinnerAPI galaxySpinner) {
        this.hero = hero;
        this.bot = bot;
        this.stats = stats;
        this.entities = entities;
        this.starSystem = starSystem;
        this.group = group;
        this.config = config;
        this.galaxySpinner = galaxySpinner;

        // Initialize services (DataCollector will be initialized in install() with Main)
        this.sessionTracker = new SessionTracker(stats);
        this.deathTracker = new DeathTracker(hero, starSystem);
    }

    @Configuration("greysi_web.config")
    public static class Config {
        @Option("API Key")
        @Editor(ApiKeyEditor.class)
        public String API_KEY = "";
    }

    public static class ApiKeyEditor extends JPanel implements OptionEditor<String> {
        private final JTextField textField;
        private String currentValue;

        public ApiKeyEditor() {
            setLayout(new FlowLayout(FlowLayout.LEFT, 5, 0));

            JLabel label = new JLabel("API Key:");
            textField = new JTextField(30);
            JButton validateButton = new JButton("✓");
            JButton clearButton = new JButton("✗");

            validateButton.setToolTipText("Valider");
            clearButton.setToolTipText("Effacer");

            validateButton.addActionListener((ActionEvent e) -> {
                String testKey = textField.getText().trim();
                if (testKey.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                        "Veuillez entrer une clé API!",
                        "Erreur",
                        JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Sauvegarder la valeur immédiatement
                currentValue = testKey;

                // Test de la clé (synchrone - bloquera l'UI pendant max 5 secondes)
                validateButton.setEnabled(false);
                validateButton.setText("Test...");

                try {
                    System.out.println("[GreysiWeb] Testing API key...");
                    boolean isValid = WebApiClient.testApiKey(WEB_SERVER_URL, testKey);
                    System.out.println("[GreysiWeb] Test result: " + (isValid ? "VALID" : "INVALID"));

                    // Re-enable button
                    validateButton.setEnabled(true);
                    validateButton.setText("✓");

                    // Show result
                    if (isValid) {
                        JOptionPane.showMessageDialog(this,
                            "API Key valide et sauvegardée!",
                            "Succès",
                            JOptionPane.INFORMATION_MESSAGE);

                        // Apply new key immediately without restarting
                        if (instance != null) {
                            instance.applyNewApiKey(testKey);
                        }
                    } else {
                        JOptionPane.showMessageDialog(this,
                            "Clé API invalide!\nVérifiez votre clé et réessayez.",
                            "Erreur",
                            JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    System.err.println("[GreysiWeb] Error testing API key: " + ex.getMessage());
                    ex.printStackTrace();
                    validateButton.setEnabled(true);
                    validateButton.setText("✓");
                    JOptionPane.showMessageDialog(this,
                        "Erreur lors de la validation: " + ex.getMessage(),
                        "Erreur",
                        JOptionPane.ERROR_MESSAGE);
                }
            });

            clearButton.addActionListener((ActionEvent e) -> {
                textField.setText("");
                currentValue = "";
                JOptionPane.showMessageDialog(this,
                    "API Key effacée!",
                    "Information",
                    JOptionPane.INFORMATION_MESSAGE);
            });

            add(label);
            add(textField);
            add(validateButton);
            add(clearButton);
        }

        @Override
        public JComponent getEditorComponent(ConfigSetting<String> apiKey) {
            this.currentValue = apiKey.getValue();
            this.textField.setText(currentValue != null ? currentValue : "");
            return this;
        }

        @Override
        public String getEditorValue() {
            return currentValue;
        }
    }

    @Override
    public void setConfig(ConfigSetting<Config> config) {
        this.apiKey = config.getValue().API_KEY;
    }

    @Override
    public void install(Main main) {
        instance = this; // Store instance for static access

        // Initialize DataCollector with Main object for hangar access
        this.dataCollector = new DataCollector(main, hero, bot, stats, entities, starSystem,
                                               group, config, galaxySpinner);

        System.out.println("==========================================");
        System.out.println("[GreysiWeb] by Greysi/AstroCommunity");
        System.out.println("[GreysiWeb] Server: " + WEB_SERVER_URL);
        System.out.println("[GreysiWeb] Waiting for hero data...");
        System.out.println("==========================================");

        timer = new Timer("GreysiWebTimer", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendDataToServer();
            }
        }, UPDATE_INTERVAL, UPDATE_INTERVAL);
    }

    /**
     * Apply new API key without restarting the bot.
     * Just updates the key and resets the client so it reconnects on next tick.
     */
    void applyNewApiKey(String newKey) {
        System.out.println("[GreysiWeb] Applying new API key...");
        this.apiKey = newKey;
        this.apiClient = null; // Force re-creation with new key on next sendDataToServer()
        System.out.println("[GreysiWeb] API key applied, will reconnect on next tick.");
    }

    @Override
    public void uninstall() {
        instance = null; // Clear static reference

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        // Send offline status
        if (apiClient != null && botId != null) {
            try {
                Map<String, Object> data = dataCollector.collectBasicInfo(botId, "", 0);
                data.put("online", false);
                apiClient.sendData(JsonBuilder.toJson(data));
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Override
    public void tick() {
        // Update death tracker
        deathTracker.tick();
    }

    private void sendDataToServer() {
        try {
            // Check if API key is configured
            if (apiKey == null || apiKey.isEmpty()) {
                return;
            }

            // Initialize API client if needed
            if (apiClient == null) {
                apiClient = new WebApiClient(WEB_SERVER_URL, apiKey);
            }

            // Wait until hero data is loaded
            String username = hero.getEntityInfo().getUsername();
            long heroId = hero.getId();
            if (username == null || username.isEmpty() || heroId == 0) {
                return;
            }

            // Generate botId on first valid data
            if (botId == null) {
                botId = username + "_" + heroId;
                System.out.println("[GreysiWeb] Bot ID: " + botId);
                System.out.println("[GreysiWeb] Plugin ready!");
            }

            // Collect all data
            Map<String, Object> data = collectAllData(username, heroId);

            // Send to server
            String response = apiClient.sendData(JsonBuilder.toJson(data));

            if (!firstDataSent) {
                System.out.println("[GreysiWeb] First data sent successfully!");
                firstDataSent = true;
            }

            // Handle commands from server
            handleServerResponse(response);

        } catch (Exception e) {
            if (!firstDataSent) {
                System.err.println("[GreysiWeb] Error: " + e.getMessage());
            }
        }
    }

    /**
     * Collect all bot data
     */
    private Map<String, Object> collectAllData(String username, long heroId) {
        Map<String, Object> data = dataCollector.collectBasicInfo(botId, username, heroId);

        dataCollector.collectModuleInfo(data);
        dataCollector.collectHeroData(data);
        dataCollector.collectStatsData(data, sessionTracker);
        dataCollector.collectMapData(data);
        dataCollector.collectConfigData(data);
        dataCollector.collectPerformanceData(data, sessionTracker);
        dataCollector.collectEntities(data);
        dataCollector.collectTargetInfo(data);
        dataCollector.collectGalaxyInfo(data);
        dataCollector.collectHangars(data);

        // Add death tracking
        data.put("deaths", deathTracker.getDeathCount());
        data.put("deathLog", deathTracker.getDeathLog());

        return data;
    }

    /**
     * Handle server response (commands)
     */
    private void handleServerResponse(String response) {
        try {
            if (response == null || response.isEmpty()) return;

            // Parse JSON response
            JsonObject jsonResponse = JsonParser.parseString(response).getAsJsonObject();

            // Handle simple commands (start/stop/setProfile)
            if (jsonResponse.get("command") != null) {
                String command = jsonResponse.get("command").getAsString();

                if (command.equals("start")) {
                    System.out.println("[GreysiWeb] Command received: START");
                    bot.setRunning(true);
                } else if (command.equals("stop")) {
                    System.out.println("[GreysiWeb] Command received: STOP");
                    bot.setRunning(false);
                } else if (command.equals("setProfile")) {
                    if (jsonResponse.get("profile") != null) {
                        String profileName = jsonResponse.get("profile").getAsString();
                        System.out.println("[GreysiWeb] Command received: SET PROFILE to " + profileName);
                        config.setConfigProfile(profileName);
                    }
                }
            }

            // Handle config write commands (new system)
            if (jsonResponse.get("configCommands") != null) {
                JsonArray commands = jsonResponse.getAsJsonArray("configCommands");
                // Use for-each instead of .size() (old Gson compatibility)
                for (JsonElement element : commands) {
                    JsonObject cmd = element.getAsJsonObject();
                    handleConfigCommand(cmd);
                }
            }

        } catch (Exception e) {
            System.err.println("[GreysiWeb] Command error: " + e.getMessage());
        }
    }

    /**
     * Handle config write command
     */
    private void handleConfigCommand(JsonObject command) {
        try {
            String type = command.get("type").getAsString();

            if (!type.equals("writeConfig")) {
                System.err.println("[GreysiWeb] Unknown command type: " + type);
                return;
            }

            String configName = command.get("configName").getAsString();
            JsonObject configJson = command.get("configJson").getAsJsonObject();
            boolean smartUpdate = command.get("smartUpdate") != null && command.get("smartUpdate").getAsBoolean();

            System.out.println("[GreysiWeb] Config write command received: " + configName);

            // Write config to file
            boolean success = writeConfig(configName, configJson, smartUpdate);

            if (success) {
                System.out.println("[GreysiWeb] Config written successfully: " + configName);
            } else {
                System.err.println("[GreysiWeb] Failed to write config: " + configName);
            }

        } catch (Exception e) {
            System.err.println("[GreysiWeb] Error handling config command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Write config to appropriate location
     */
    private boolean writeConfig(String configName, JsonObject configJson, boolean smartUpdate) {
        try {
            // DarkBot working directory (where config.json and configs/ folder are)
            File workingDir = new File(System.getProperty("user.dir"));
            System.out.println("[GreysiWeb] Working directory: " + workingDir.getAbsolutePath());

            File targetFile;

            // Determine target location
            if (configName.equals("config")) {
                // Main config file - write to working directory
                targetFile = new File(workingDir, "config.json");
            } else {
                // Profile config - write to configs/ subdirectory
                File configsDir = new File(workingDir, "configs");
                if (!configsDir.exists()) {
                    configsDir.mkdirs();
                    System.out.println("[GreysiWeb] Created configs directory: " + configsDir.getAbsolutePath());
                }
                targetFile = new File(configsDir, configName + ".json");
            }

            System.out.println("[GreysiWeb] Writing config to: " + targetFile.getAbsolutePath());

            // Smart Update logic - server already determined this is the current profile
            if (smartUpdate && targetFile.exists() && !configName.equals("config")) {
                System.out.println("[GreysiWeb] Smart update: using async backup method");

                // Create backup copy
                final File backupFile = new File(targetFile.getParent(), configName + "_backup.json");
                Files.copy(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                // Switch to backup profile
                config.setConfigProfile(configName + "_backup");

                // Use Timer to delay next steps (avoids Thread.sleep which is blocked)
                final File finalTargetFile = targetFile;
                final String finalConfigName = configName;
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            // Write new config to original file
                            writeJsonToFile(configJson, finalTargetFile);
                            System.out.println("[GreysiWeb] New config written, switching back...");

                            // Switch back to updated config
                            config.setConfigProfile(finalConfigName);

                            // Delete backup after another delay
                            new Timer().schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    if (backupFile.delete()) {
                                        System.out.println("[GreysiWeb] Smart update completed, backup deleted");
                                    }
                                }
                            }, 500);
                        } catch (Exception e) {
                            System.err.println("[GreysiWeb] Smart update error: " + e.getMessage());
                        }
                    }
                }, 500);

                return true; // Async operation started
            }

            // Normal write (no smart update needed)
            writeJsonToFile(configJson, targetFile);
            System.out.println("[GreysiWeb] Config written successfully");
            return true;

        } catch (Exception e) {
            System.err.println("[GreysiWeb] Error writing config: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Write JSON object to file with pretty printing (UTF-8 encoding)
     */
    private void writeJsonToFile(JsonObject json, File file) throws IOException {
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(json, writer);
        }
    }

}
