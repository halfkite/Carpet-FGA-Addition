//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga;

/** Validation shared by Carpet rules and their generation/vehicle consumers. */
public final class NewFeatureOptions {
    private NewFeatureOptions() { }

    public static boolean validCapacity(String value) {
        if ("false".equals(value)) return true;
        try { return value.matches("[0-9]+") && Integer.parseInt(value) > 0; }
        catch (NumberFormatException ignored) { return false; }
    }

    public static int capacity(String value) {
        return "false".equals(value) ? 0 : Integer.parseInt(value);
    }

    public static boolean validIce(String value) {
        if ("false".equals(value)) return true;
        if (value == null || !value.matches("[0-9]+,[0-9]+")) return false;
        try {
            String[] parts = value.split(",");
            int packed = Integer.parseInt(parts[0]), blue = Integer.parseInt(parts[1]);
            return packed <= 100 && blue <= 100 && packed + blue <= 100;
        } catch (NumberFormatException ignored) { return false; }
    }

    /** 0 = regular ice, 1 = packed ice, 2 = blue ice; a single exclusive roll. */
    public static int iceChoice(String value, int roll) {
        if ("false".equals(value)) return 0;
        String[] parts = value.split(",");
        int packed = Integer.parseInt(parts[0]), blue = Integer.parseInt(parts[1]);
        return roll < packed ? 1 : roll < packed + blue ? 2 : 0;
    }

    public static boolean validBedrock(String value) {
        return "false".equals(value) || "true".equals(value) || value.matches("[1-5]");
    }

    public static int bedrockLayers(String value) {
        return "false".equals(value) ? 0 : "true".equals(value) ? 1 : Integer.parseInt(value);
    }
}
//#endif
