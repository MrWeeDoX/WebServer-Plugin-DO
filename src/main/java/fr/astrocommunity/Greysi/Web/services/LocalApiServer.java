package fr.astrocommunity.Greysi.Web.services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.darkbot.api.managers.ConfigAPI;
import fi.iki.elonen.NanoHTTPD;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Local HTTP server to receive config upload requests from web server
 */
public class LocalApiServer extends NanoHTTPD {

    private final ConfigAPI configAPI;
    private final Gson gson;
    private final File jarDirectory;

    public LocalApiServer(int port, ConfigAPI configAPI) throws IOException {
        super(port);
        this.configAPI = configAPI;
        this.gson = new Gson();

        // Find jar directory (go back from classes directory)
        String classPath = getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
        File classFile = new File(classPath);
        this.jarDirectory = classFile.getParentFile(); // Should be the folder containing the .jar

        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("[GreysiWeb] Local API Server started on port " + port);
        System.out.println("[GreysiWeb] Working directory: " + jarDirectory.getAbsolutePath());
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        // Only handle POST to /api/uploadConfig
        if (!uri.equals("/api/uploadConfig") || method != Method.POST) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "application/json",
                "{\"success\":false,\"error\":\"Endpoint not found\"}");
        }

        try {
            // Parse request body
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String requestBody = files.get("postData");

            if (requestBody == null || requestBody.isEmpty()) {
                return errorResponse("Empty request body");
            }

            // Parse JSON
            JsonObject json = JsonParser.parseString(requestBody).getAsJsonObject();

            if (!json.has("configName") || !json.has("configJson")) {
                return errorResponse("Missing required fields: configName, configJson");
            }

            String configName = json.get("configName").getAsString();
            JsonObject configJson = json.get("configJson").getAsJsonObject();
            boolean smartUpdate = json.has("smartUpdate") && json.get("smartUpdate").getAsBoolean();

            // Write config to file
            boolean success = writeConfig(configName, configJson, smartUpdate);

            if (success) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Config written successfully");
                return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(response));
            } else {
                return errorResponse("Failed to write config");
            }

        } catch (Exception e) {
            System.err.println("[GreysiWeb] Error handling uploadConfig: " + e.getMessage());
            e.printStackTrace();
            return errorResponse("Server error: " + e.getMessage());
        }
    }

    /**
     * Write config to appropriate location
     */
    private boolean writeConfig(String configName, JsonObject configJson, boolean smartUpdate) {
        try {
            File targetFile;

            // Determine target location
            if (configName.equals("config")) {
                // Main config file - write directly to jar directory
                targetFile = new File(jarDirectory, "config.json");
            } else {
                // Profile config - write to configs/ subdirectory
                File configsDir = new File(jarDirectory, "configs");
                if (!configsDir.exists()) {
                    // Try alternative name "config" (without s)
                    configsDir = new File(jarDirectory, "config");
                    if (!configsDir.exists()) {
                        configsDir.mkdirs();
                        System.out.println("[GreysiWeb] Created configs directory: " + configsDir.getAbsolutePath());
                    }
                }
                targetFile = new File(configsDir, configName + ".json");
            }

            System.out.println("[GreysiWeb] Writing config to: " + targetFile.getAbsolutePath());

            // Smart Update logic
            if (smartUpdate && targetFile.exists()) {
                String currentProfile = configAPI.getConfigProfile();
                boolean isCurrentProfile = (configName.equals("config") && currentProfile.isEmpty()) ||
                                          configName.equals(currentProfile);

                if (isCurrentProfile) {
                    System.out.println("[GreysiWeb] Smart update: current profile detected, creating backup");

                    // Create backup copy
                    File backupFile = new File(targetFile.getParent(), configName + "_backup.json");
                    Files.copy(targetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    // Switch to backup
                    if (!configName.equals("config")) {
                        configAPI.setConfigProfile(configName + "_backup");
                        Thread.sleep(500); // Wait for switch
                    }

                    // Write new config
                    writeJsonToFile(configJson, targetFile);

                    // Switch back to updated config
                    if (!configName.equals("config")) {
                        configAPI.setConfigProfile(configName);
                        Thread.sleep(500); // Wait for switch
                    }

                    // Delete backup
                    backupFile.delete();
                    System.out.println("[GreysiWeb] Smart update completed");

                    return true;
                }
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
     * Write JSON object to file with pretty printing
     */
    private void writeJsonToFile(JsonObject json, File file) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(json, writer);
        }
    }

    /**
     * Create error response
     */
    private Response errorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return newFixedLengthResponse(Response.Status.BAD_REQUEST, "application/json", gson.toJson(response));
    }

    /**
     * Stop the server
     */
    public void shutdown() {
        stop();
        System.out.println("[GreysiWeb] Local API Server stopped");
    }
}
