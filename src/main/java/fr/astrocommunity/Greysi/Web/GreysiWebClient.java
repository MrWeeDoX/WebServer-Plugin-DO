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
import fr.astrocommunity.Greysi.Web.services.LocalApiServer;
import fr.astrocommunity.Greysi.Web.utils.JsonBuilder;

import javax.swing.*;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.ServerSocket;
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

    private static final String WEB_SERVER_URL = "http://116.202.163.21:3000/api/bot/update";
    private static final int UPDATE_INTERVAL = 2000; // 2 secondes

    // Services
    private final DataCollector dataCollector;
    private final SessionTracker sessionTracker;
    private final DeathTracker deathTracker;
    private final HeroAPI hero;
    private final BotAPI bot;
    private final ConfigAPI config;

    // Network
    private WebApiClient apiClient;
    private LocalApiServer localApiServer;
    private Timer timer;
    private String apiKey = null;
    private String botId;
    private int localApiPort = 0;
    private boolean firstDataSent = false;

    public GreysiWebClient(HeroAPI hero, BotAPI bot, StatsAPI stats, EntitiesAPI entities,
                           StarSystemAPI starSystem, GroupAPI group, PetAPI pet, ConfigAPI config,
                           GalaxySpinnerAPI galaxySpinner) {
        this.hero = hero;
        this.bot = bot;
        this.config = config;

        // Initialize services
        this.dataCollector = new DataCollector(hero, bot, stats, entities, starSystem,
                                               group, config, galaxySpinner);
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
                            "API Key valide et sauvegardée!\nLes changements sont appliqués automatiquement.",
                            "Succès",
                            JOptionPane.INFORMATION_MESSAGE);
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
        System.out.println("==========================================");
        System.out.println("[GreysiWeb] by Greysi/AstroCommunity");
        System.out.println("[GreysiWeb] Server: " + WEB_SERVER_URL);
        System.out.println("[GreysiWeb] Waiting for hero data...");

        // Start local API server on an available port
        try {
            localApiPort = findAvailablePort(8000, 9000);
            localApiServer = new LocalApiServer(localApiPort, config);
            System.out.println("[GreysiWeb] Local API ready on port " + localApiPort);
        } catch (IOException e) {
            System.err.println("[GreysiWeb] Failed to start local API server: " + e.getMessage());
            localApiPort = 0;
        }

        System.out.println("==========================================");

        timer = new Timer("GreysiWebTimer", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendDataToServer();
            }
        }, UPDATE_INTERVAL, UPDATE_INTERVAL);
    }

    @Override
    public void uninstall() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        // Stop local API server
        if (localApiServer != null) {
            localApiServer.shutdown();
            localApiServer = null;
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

        // Add death tracking
        data.put("deaths", deathTracker.getDeathCount());
        data.put("deathLog", deathTracker.getDeathLog());

        // Add local API port for config management
        data.put("apiPort", localApiPort);

        return data;
    }

    /**
     * Handle server response (commands)
     */
    private void handleServerResponse(String response) {
        try {
            if (response == null || response.isEmpty()) return;

            if (response.contains("\"command\":\"start\"")) {
                System.out.println("[GreysiWeb] Command received: START");
                bot.setRunning(true);
            } else if (response.contains("\"command\":\"stop\"")) {
                System.out.println("[GreysiWeb] Command received: STOP");
                bot.setRunning(false);
            } else if (response.contains("\"command\":\"setProfile\"")) {
                String profileName = JsonBuilder.extractJsonValue(response, "profile");
                if (profileName != null && !profileName.isEmpty()) {
                    System.out.println("[GreysiWeb] Command received: SET PROFILE to " + profileName);
                    config.setConfigProfile(profileName);
                }
            }
        } catch (Exception e) {
            System.err.println("[GreysiWeb] Command error: " + e.getMessage());
        }
    }

    /**
     * Find an available port in the given range
     */
    private int findAvailablePort(int startPort, int endPort) throws IOException {
        for (int port = startPort; port <= endPort; port++) {
            try (ServerSocket socket = new ServerSocket(port)) {
                return port;
            } catch (IOException ignored) {
                // Port in use, try next
            }
        }
        throw new IOException("No available port found in range " + startPort + "-" + endPort);
    }
}
