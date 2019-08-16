package me.jts3304.playerdata;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PlayerDataManager extends JavaPlugin implements Listener {
    private static PlayerDataManager instance;
    private Path dataDirPath;
    private Exception loadException;
    private boolean debug;
    private boolean inLoadingPhase;

    private Path fieldsPath;
    private List<PlayerDataField> fields;

    private List<BiConsumer<OfflinePlayer, PlayerData>> loadTasks = new ArrayList<>();
    private List<BiConsumer<Player, PlayerData>> joinTasks = new ArrayList<>();
    private List<BiConsumer<Player, PlayerData>> quitTasks = new ArrayList<>();
    private List<BiConsumer<OfflinePlayer, PlayerData>> unloadTasks = new ArrayList<>();

    private List<PlayerData> loadedPlayers = new ArrayList<>();

    @Override
    public void onLoad() {
        inLoadingPhase = true;
        instance = this;
        getDataFolder().mkdirs();
        dataDirPath = getDataFolder().toPath().resolve("playerdata");
        if(Files.notExists(dataDirPath)) {
            try {
                Files.createDirectory(dataDirPath);
            } catch (IOException e) {
                loadException = e;
                return;
            }
        }

        // DATA FIELDS
        fieldsPath = getDataFolder().toPath().resolve("fields.json");
        // create if doesn't exist
        if(Files.notExists(fieldsPath)){
            fields = new ArrayList<>();
            try{
                Files.createFile(fieldsPath);
                saveFields();
                getLogger().info("Created fields.json");
            } catch (IOException e) {
                loadException = e;
                return;
            }
        }
        // load, ensure base values, save to file
        try(BufferedReader reader = Files.newBufferedReader(fieldsPath)){
            fields = new Gson().fromJson(reader, new TypeToken<List<PlayerDataField>>(){}.getType());
            PlayerDataField uuidField = new PlayerDataField("uuid", null);
            PlayerDataField usernameField = new PlayerDataField("username", null);
            PlayerDataField playingTimeField = new PlayerDataField("playing_time", 0L);
            PlayerDataField permissionsField = new PlayerDataField("permissions", new ArrayList<>());
            List<String> fieldNames = fields.stream().map(PlayerDataField::getName).collect(Collectors.toList());
            if(!fieldNames.contains(uuidField.getName())) fields.add(uuidField);
            if(!fieldNames.contains(usernameField.getName())) fields.add(usernameField);
            if(!fieldNames.contains(playingTimeField.getName())) fields.add(playingTimeField);
            if(!fieldNames.contains(permissionsField.getName())) fields.add(permissionsField);
            saveFields();
        } catch (IOException e) {
            loadException = e;
            return;
        }

        // LOAD TASKS
        loadTasks.add((player, playerData) -> {
            if(playerData.getCustomType("uuid", UUID.class) == null) playerData.setUniqueId(player.getUniqueId());
            if(playerData.getCustomType("username", String.class) == null) playerData.setName(player.getName());
        });

        // JOIN TASKS
        joinTasks.add(((player, playerData) -> playerData.addBukkitTask(Bukkit.getScheduler().runTaskTimer(this,
                () -> playerData.setPlayingTime(playerData.getPlayingTime() + 1L) ,20L, 20L))));

        // QUIT TASKS
        quitTasks.add(((player, playerData) -> playerData.getTaskIds().forEach(taskId -> Bukkit.getScheduler().cancelTask(taskId))));

        // UNLOAD TASKS

        loadException = null; // loading was successful

    }

    protected void saveFields() throws IOException {
        try(BufferedWriter writer = Files.newBufferedWriter(fieldsPath)){
            writer.write(new GsonBuilder().serializeNulls().create().toJson(fields));
        }
    }

    @Override
    public void onEnable(){
        inLoadingPhase = false;
        if(loadException != null) {
            getLogger().log(Level.SEVERE, "Fatal error occured while loading. Disabling...");
            loadException.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // enable after everything else
        Bukkit.getScheduler().runTaskLater(this, ()->{
            this.saveDefaultConfig();
            debug = getConfig().getBoolean("debug");
            getServer().getPluginManager().registerEvents(this, this);
            Bukkit.getOnlinePlayers().forEach(this::joinPlayer);
        }, 1);
    }

    @Override
    public void onDisable() {
        Bukkit.getOnlinePlayers().forEach(this::quitPlayer);
        try {
            saveFields();
        } catch (IOException e) {
            getLogger().severe("Failed to save data fields file");
            e.printStackTrace();
        }
    }

    private void joinPlayer(Player player){
        PlayerData data = loadPlayer(player);
        joinTasks.forEach(playerPlayerDataBiConsumer -> playerPlayerDataBiConsumer.accept(player, data));
        loadedPlayers.add(data);
    }

    protected PlayerData loadPlayer(OfflinePlayer offlinePlayer){
        Path dataPath = dataDirPath.resolve(offlinePlayer.getUniqueId() + ".json");
        final PlayerData data;

        // CHECK IF PLAYED BEFORE/FILE EXISTS, IF NOT CREATE NEW PlayerData, OTHERWISE LOAD FILE
        if(!offlinePlayer.hasPlayedBefore() || Files.notExists(dataPath)){
            data = new PlayerData(new HashMap<>());
            if(debug) getLogger().info("Created player data: " + offlinePlayer.getName());
        }else{
            try(BufferedReader reader = Files.newBufferedReader(dataPath)){
                data = new PlayerData(new HashMap<>(new Gson()
                        .fromJson(reader, new TypeToken<Map<String, String>>(){}.getType())));
            } catch (IOException e) {
                getLogger().severe("Failed to load data of " + offlinePlayer.getName());
                if(debug) e.printStackTrace();
                return null;
            }
        }

        // ENSURE FIELDS HAVE NO HOLES
        fields.forEach(field -> {
            if(!data.getMutableData().containsKey(field.getName()) || data.getCustomType(field.getName(), Object.class) == null){
                data.getMutableData().put(field.getName(),
                        new GsonBuilder().setPrettyPrinting().create().toJson(field.getDefaultValue()));
            }
        });
        // CLEAN UNUSED FIELDS
        List<String> flagged = new ArrayList<>();
        data.getMutableData().forEach((field, value) ->{
            if(!fields.stream().map(PlayerDataField::getName).collect(Collectors.toList()).contains(field))
                flagged.add(field);
        });
        flagged.forEach(field -> data.getMutableData().remove(field));

        // PERFORM TASKS GIVEN HERE OR BY PLUGINS USING API
        loadTasks.forEach(offlinePlayerPlayerDataBiConsumer -> offlinePlayerPlayerDataBiConsumer.accept(offlinePlayer, data));
        if(debug) getLogger().info("Loaded data: " + offlinePlayer.getName());
        return data;
    }

    private void quitPlayer(Player player){
        PlayerData data =
                loadedPlayers.stream().filter(d -> d.getUniqueId().equals(player.getUniqueId())).findFirst().orElse(null);
        quitTasks.forEach(playerPlayerDataBiConsumer -> playerPlayerDataBiConsumer.accept(player, data));
        loadedPlayers.remove(data);
        unloadData(data);
    }

    protected void unloadData(PlayerData data){
        try(BufferedWriter writer = Files.newBufferedWriter(dataDirPath.resolve(data.getUniqueId() + ".json"))){
            unloadTasks.forEach(offlinePlayerPlayerDataBiConsumer ->
                    offlinePlayerPlayerDataBiConsumer.accept(Bukkit.getOfflinePlayer(data.getUniqueId()), data));
            writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(data.getMutableData()));
            if(debug) getLogger().info("Saved data: " + data.getName());
        } catch (IOException e) {
            getLogger().severe("Failed to save data of " + data.getName());
            if(debug) e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event){
        joinPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event){
        quitPlayer(event.getPlayer());
    }

    protected static PlayerDataManager getInstance() {
        return instance;
    }

    protected boolean isDebug() {
        return debug;
    }

    protected List<PlayerDataField> getFields() {
        return fields;
    }

    protected List<BiConsumer<OfflinePlayer, PlayerData>> getLoadTasks() {
        return loadTasks;
    }

    protected List<BiConsumer<Player, PlayerData>> getJoinTasks() {
        return joinTasks;
    }

    protected List<BiConsumer<Player, PlayerData>> getQuitTasks() {
        return quitTasks;
    }

    protected List<BiConsumer<OfflinePlayer, PlayerData>> getUnloadTasks() {
        return unloadTasks;
    }

    protected List<PlayerData> getLoadedPlayers() {
        return loadedPlayers;
    }

    protected boolean isInLoadingPhase(){
        return inLoadingPhase;
    }
}
