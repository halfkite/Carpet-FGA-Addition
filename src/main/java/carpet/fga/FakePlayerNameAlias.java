package carpet.fga;

import com.mojang.authlib.GameProfile;

import java.util.function.Supplier;

public final class FakePlayerNameAlias {
    private static final int NETWORK_NAME_LIMIT = 16;
    private static final int PREFIX_LENGTH = 7;
    private static final ThreadLocal<Boolean> FULL_NAMES = ThreadLocal.withInitial(() -> false);

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

    public static String alias(String name) {
        if (name.length() <= NETWORK_NAME_LIMIT) {
            return name;
        }
        return name.substring(0, PREFIX_LENGTH) + "...";
    }
}
