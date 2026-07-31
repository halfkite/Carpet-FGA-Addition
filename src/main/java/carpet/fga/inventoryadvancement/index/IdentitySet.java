//#if MC >= 1.20.5
package carpet.fga.inventoryadvancement.index;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class IdentitySet {
    private IdentitySet() {}

    public static <T> Set<T> create() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
//#endif

