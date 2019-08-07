package me.jts3304.playerdata;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * <p>Extends the functionality of a {@link Player} by giving developers the ability to easily associate custom data
 * such as permissions or currency balance.</p>
 *
 * <p>In order to retreieve a player's data, use {@link #get(OfflinePlayer) PlayerData.get()}. This function works
 * for any {@link Player} that has played on the server, whether they are online or not. For retrieving data of
 * offline players, it is recommended to use the {@link #get(OfflinePlayer)} get function within a
 * 'try-with-resources' block. This class implements {@link Closeable}, and therefore the function {@link #close()}
 * should be called when all data operations are complete for an offline player.</p>
 * <p></p>
 * <p>This class also acts as the interface for managing the data fields that it uses via the static methods listed
 * here:</p>
 * <p>{@link #addField(PlayerDataField)}</p>
 * <p>{@link #removeField(String)}</p>
 * <p>{@link #getFields()}</p>
 * <p></p>
 * <p>Plugins can also run tasks when a player's data is loaded or unloaded, via these static methods:</p>
 * <p>{@link #onLoad(BiConsumer)}</p>
 * <p>{@link #onJoin(BiConsumer)}</p>
 * <p>{@link #onQuit(BiConsumer)}</p>
 * <p>{@link #onUnload(BiConsumer)}</p>
 */
public class PlayerData implements Closeable
{
    private Map<String, String> data;
    private transient PlayerDataManager plugin = PlayerDataManager.getInstance();
    private transient List<Integer> taskIds = new ArrayList<>();

    protected PlayerData(Map<String, String> data)
    {
        this.data = data;
    }

    /**
     * <p>Get the value of the specified data field casted to a {@link Boolean}</p>
     * <p>Note: casting of values is unchecked</p>
     *
     * @param fieldName the name of the data field
     * @return the value
     */
    public Boolean getBoolean(String fieldName)
    {
        return getCustomType(fieldName, Boolean.class);
    }

    /**
     * <p>Get the value of the specified data field casted to a {@link Double}</p>
     * <p>Note: casting of values is unchecked</p>
     *
     * @param fieldName the name of the data field
     * @return the value
     */
    public Double getDouble(String fieldName)
    {
        return getNumber(fieldName).doubleValue();
    }

    /**
     * <p>Get the value of the specified data field casted to a {@link Float}</p>
     * <p>Note: casting of values is unchecked</p>
     *
     * @param fieldName the name of the data field
     * @return the value
     */
    public Float getFloat(String fieldName)
    {
        return getNumber(fieldName).floatValue();
    }

    /**
     * <p>Get the value of the specified data field casted to a {@link Long}</p>
     * <p>Note: casting of values is unchecked</p>
     *
     * @param fieldName the name of the data field
     * @return the value
     */
    public Long getLong(String fieldName)
    {
        return getNumber(fieldName).longValue();
    }

    /**
     * <p>Get the value of the specified data field casted to a {@link Integer}</p>
     * <p>Note: casting of values is unchecked</p>
     *
     * @param fieldName the name of the data field
     * @return the value
     */
    public Integer getInt(String fieldName)
    {
        return getNumber(fieldName).intValue();
    }

    /**
     * <p>Get the value of the specified data field casted to a {@link Short}</p>
     * <p>Note: casting of values is unchecked</p>
     *
     * @param fieldName the name of the data field
     * @return the value
     */
    public Short getShort(String fieldName)
    {
        return getNumber(fieldName).shortValue();
    }

    /**
     * <p>Get the value of the specified data field casted to a {@link Character}</p>
     * <p>Note: casting of values is unchecked</p>
     *
     * @param fieldName the name of the data field
     * @return the value
     */
    public Character getChar(String fieldName)
    {
        return getCustomType(fieldName, Character.class);
    }

    /**
     * <p>Get the value of the specified data field casted to a {@link Byte}</p>
     * <p>Note: casting of values is unchecked</p>
     *
     * @param fieldName the name of the data field
     * @return the value
     */
    public Byte getByte(String fieldName)
    {
        return getCustomType(fieldName, Byte.class);
    }

    /**
     * <p>Get the value of the specified data field as a {@link Number}</p>
     * <p>Note: casting of values is unchecked</p>
     *
     * @param fieldName
     * @return
     */
    public Number getNumber(String fieldName)
    {
        return getCustomType(fieldName, Number.class);
    }

    /**
     * <p>Get the value of the specified data field as a {@link String}</p>
     * <p>Note: casting of values is unchecked</p>
     *
     * @param fieldName the name of the data field
     * @return the value
     */
    public String getString(String fieldName)
    {
        return getCustomType(fieldName, String.class);
    }

    /**
     * Get the value of the specified data field as an {@link Object}
     *
     * @param fieldName the name of the data field
     * @return the data field's value, if it exists, null otherwise
     */
    public Object getObject(String fieldName)
    {
        return getCustomType(fieldName, Object.class);
    }

    /**
     * <p>Convert a field value to the specified type via Gson.</p>
     * <p>Note: casting of values is unchecked</p>
     *
     * @param fieldName the name of the data field
     * @param type      the expect class type of the value
     * @param <T>       the expected type of the value
     * @return an instance of the specified type with data contained in the specified field
     */
    public <T> T getCustomType(String fieldName, Class<T> type)
    {
        return new Gson().fromJson(data.get(fieldName), type);
    }

    /**
     * Check whether the specified field is null
     *
     * @param fieldName the name of the field
     * @return true if the field's value is null, false otherwise
     */
    public boolean fieldIsNull(String fieldName)
    {
        return data.get(fieldName) == null;
    }

    /**
     * Set a data field's value
     *
     * @param fieldName the name of the field
     * @param value     the new value of the field
     */
    public void set(String fieldName, Object value)
    {
        data.replace(fieldName, new GsonBuilder().setPrettyPrinting().create().toJson(value));
    }


    /**
     * Check whether the player has the specified permission
     *
     * @param permission the permission node
     * @return true if the player has the permission, false otherwise
     */
    public boolean hasPermission(String permission)
    {
        return getPermissions().contains(permission) || Bukkit.getOfflinePlayer(getUniqueId()).isOp();
    }

    /**
     * @return a list of every permission the player has
     */
    public List<String> getPermissions()
    {
        return new Gson().fromJson(data.get("permissions"), new TypeToken<List<String>>()
        {
        }.getType());
    }

    /**
     * @return the combined total number of seconds the player has played on the server
     */
    public long getPlayingTime()
    {
        return getLong("playing_time");
    }

    protected void setPlayingTime(long playingTime)
    {
        set("playing_time", playingTime);
    }

    /**
     * @return the UUID of the player
     */
    public UUID getUniqueId()
    {
        return getCustomType("uuid", UUID.class);
    }

    protected void setUniqueId(UUID uuid)
    {
        set("uuid", uuid);
    }

    /**
     * @return the username of the player
     */
    public String getName()
    {
        return getString("username");
    }

    protected void setName(String name)
    {
        set("username", name);
    }

    protected List<Integer> getTaskIds()
    {
        return taskIds;
    }

    /**
     * <p>Adds the integer id of the specified {@link BukkitTask} to a list. When the player quits, any task with an id
     * in this list will be cancelled. This is meant for repeating tasks that should continue until the player quits.
     * <p>Alternatively to this function, plugins can make their own maps to cancel them whenever they please.
     *
     * @param task the {@link BukkitTask} to store the id of
     * @return the task id
     */
    public int addBukkitTask(BukkitTask task)
    {
        taskIds.add(task.getTaskId());
        return task.getTaskId();
    }

    /**
     * @return an immutable map of all data pertaining to the player
     */
    public ImmutableMap<String, Object> getAll()
    {
        return ImmutableMap.copyOf(data);
    }

    protected Map<String, String> getMutableData()
    {
        return data;
    }

    /**
     * Get the instance of {@link PlayerData} associated with the specified player
     *
     * @param player the {@link OfflinePlayer} or {@link Player} to get the data of
     * @return the player's {@link PlayerData}
     */
    public static PlayerData get(OfflinePlayer player)
    {
        if (player.isOnline()) return PlayerDataManager.getInstance().getLoadedPlayers().stream().filter(playerData ->
                playerData.getUniqueId().equals(player.getUniqueId())).findFirst().orElse(null);
        else return PlayerDataManager.getInstance().loadPlayer(player);
    }

    @Override
    public void close() throws IllegalStateException
    {
        if (plugin.getLoadedPlayers().contains(this)) return;
        PlayerDataManager.getInstance().unloadData(this);
    }

    /**
     * Performs the specified actions when the player's data is loaded from a file, as well as if the server is
     * reloaded while the player is online
     *
     * @param task the task to be run
     */
    public static void onLoad(BiConsumer<OfflinePlayer, PlayerData> task)
    {
        PlayerDataManager.getInstance().getLoadTasks().add(task);
    }

    /**
     * Performs the specified actions when the player joins the server, as well as if the server is reloaded while
     * the player is online
     *
     * @param task the task to be run
     */
    public static void onJoin(BiConsumer<Player, PlayerData> task)
    {
        PlayerDataManager.getInstance().getJoinTasks().add(task);
    }

    /**
     * Performs the specified actions when the player quits the server as well as if the server is reloaded while
     * the player is online
     *
     * @param task the task to be run
     */
    public static void onQuit(BiConsumer<Player, PlayerData> task)
    {
        PlayerDataManager.getInstance().getQuitTasks().add(task);
    }

    /**
     * Performs the specified actions when the player's data is saved to a file as well as if the server is
     * reloaded while the player is online
     *
     * @param task the task to be run
     */
    public static void onUnload(BiConsumer<OfflinePlayer, PlayerData> task)
    {
        PlayerDataManager.getInstance().getUnloadTasks().add(task);
    }

    /**
     * <p>Adds a new data field to players' data files. This function must be performed during the loading phase of
     * plugins.</p>
     * <p>Note: if the field already exists, a new one will not be added</p>
     *
     * @param field the new data field
     */
    public static void addField(PlayerDataField field)
    {
        if (PlayerDataManager.getInstance().isInLoadingPhase())
        {
            if (!PlayerDataManager.getInstance().getFields().stream()
                    .map(PlayerDataField::getName).collect(Collectors.toList()).contains(field.getName()))
                PlayerDataManager.getInstance().getFields().add(field);
        }
        try
        {
            PlayerDataManager.getInstance().saveFields();
        } catch (IOException e)
        {
            PlayerDataManager.getInstance().getLogger().severe("Failed to save data fields file");
            e.printStackTrace();
        }
    }

    /**
     * <p>Removes a data field. This function must be performed during the loading phase of plugins.
     * <p>Note: the data fields "uuid", "username", and "playing_time" cannot be removed. Removing a data field
     * cannot be undone. Fields will be removed from the stored list of fields, then their mapping to each player is
     * removed as they are loaded.
     *
     * @param name the name of the data field
     */
    public static void removeField(String name)
    {
        switch (name)
        {
            case "uuid":
            case "username":
            case "playing_time":
                return; // DO NOT REMOVE THESE FIELDS
        }
        PlayerDataManager.getInstance().getFields().remove(PlayerDataManager.getInstance().getFields().stream().filter(field ->
                field.getName().equalsIgnoreCase(name)).findFirst().orElse(null));
    }

    /**
     * Check whether player data has the specified field
     *
     * @param fieldName the name of the field
     * @return true if the field exists, false otherwise
     */
    public static boolean containsField(String fieldName)
    {
        return getFields().stream().map(PlayerDataField::getName).collect(Collectors.toList()).contains(fieldName);
    }

    /**
     * @return an immutable list of the fields that are being stored
     */
    public static ImmutableList<PlayerDataField> getFields()
    {
        return ImmutableList.copyOf(PlayerDataManager.getInstance().getFields());
    }
}
