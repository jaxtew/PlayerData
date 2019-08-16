package me.jts3304.playerdata;

/**
 * Represents a data field utilized by {@link PlayerData}
 */
public class PlayerDataField
{
    private final String name;
    private final Object defaultValue;

    public PlayerDataField(String name, Object defaultValue)
    {
        this.name = name;
        this.defaultValue = defaultValue;
    }

    /**
     * @return the name of the field
     */
    public String getName()
    {
        return name;
    }

    /**
     * @return the default value of the field
     */
    public Object getDefaultValue()
    {
        return defaultValue;
    }
}
