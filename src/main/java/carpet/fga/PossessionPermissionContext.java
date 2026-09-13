package carpet.fga;

import java.util.function.Supplier;

/** Thread-local guard for permission checks that must bypass the live body profile. */
public final class PossessionPermissionContext {
    private static final ThreadLocal<Boolean> USE_ORIGINAL = ThreadLocal.withInitial(() -> false);

    private PossessionPermissionContext() {}

    public static boolean useOriginalPermission() {
        return USE_ORIGINAL.get();
    }

    public static <T> T withOriginalPermission(Supplier<T> action) {
        boolean previous = USE_ORIGINAL.get();
        USE_ORIGINAL.set(true);
        try {
            return action.get();
        } finally {
            USE_ORIGINAL.set(previous);
        }
    }
}
