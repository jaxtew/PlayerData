package me.jts3304.playerdata;

/**
 * Represents a data field utilized by {@link PlayerData}
 */
public class PlayerDataField<T> {
    private final String name;
    private final T defaultValue;

    public PlayerDataField(String name, T defaultValue){
        this.name = name;
        this.defaultValue = defaultValue;
    }

    /**
     * @return the name of the field
     */
    public String getName() {
        return name;
    }

    /**
     * @return the default value of the field
     */
    public Object getDefaultValue() {
        return defaultValue;
    }

    /**
     * @return the type of the value of the field
     */
    public Class getType(){
        return defaultValue.getClass();
    }
}
