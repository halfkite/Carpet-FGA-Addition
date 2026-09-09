package carpet.fga;

import com.mojang.authlib.GameProfile;

import java.util.function.Supplier;

public final class FakePlayerNameAlias {
    private static final int NETWORK_NAME_LIMIT = 16;
    private static final int PREFIX_LENGTH = 7;
    private static final ThreadLocal<Boolean> FULL_NAMES = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> READ_PREVIOUS = new ThreadLocal<>();

    private FakePlayerNameAlias() {
    }

    public static GameProfile networkProfile(GameProfile profile) {
        if (FULL_NAMES.get() ||
                //#if MC >= 1.21.10
                //$$ profile.name()
                //#else
                profile.getName()
                //#endif
                .length() <= NETWORK_NAME_LIMIT) {
            return profile;
        }
        //#if MC >= 1.21.10
        //$$ return new GameProfile(profile.id(), alias(profile.name()), profile.properties());
        //#else
        GameProfile aliasProfile = new GameProfile(profile.getId(), alias(profile.getName()));
        aliasProfile.getProperties().putAll(profile.getProperties());
        return aliasProfile;
        //#endif
    }

    public static <T> T withFullNames(Supplier<T> supplier) {
        boolean previous = FULL_NAMES.get();
        FULL_NAMES.set(true);
        try {
            return supplier.get();
        } finally {
            FULL_NAMES.set(previous);
        }
    }

    /**
     * Returns whether the current packet operation is explicitly carrying an
     * FGA long-name PlayerInfo payload.  The value is deliberately scoped to
     * the current thread so generic UTF fields are left untouched.
     */
    public static boolean fullNamesActive() {
        return FULL_NAMES.get();
    }

    public static void beginFullNamesRead() {
        READ_PREVIOUS.set(FULL_NAMES.get());
        FULL_NAMES.set(true);
    }

    public static void endFullNamesRead() {
        Boolean previous = READ_PREVIOUS.get();
        if (previous == null || !previous) FULL_NAMES.remove();
        else FULL_NAMES.set(true);
        READ_PREVIOUS.remove();
    }

    public static String alias(String name) {
        if (name.length() <= NETWORK_NAME_LIMIT) {
            return name;
        }
        return name.substring(0, PREFIX_LENGTH) + "...";
    }
}
