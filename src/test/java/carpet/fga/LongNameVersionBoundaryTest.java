package carpet.fga;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the generated configuration, not raw commented preprocessor source. */
final class LongNameVersionBoundaryTest {
    private static final boolean MODERN =
            //#if MC >= 26.3
            //$$ true;
            //#else
            false;
            //#endif
    private static final boolean MC_26_3 =
            //#if MC == 26.3
            //$$ true;
            //#else
            false;
            //#endif
    private static final boolean MODERN_REJOIN_MIXIN =
            //#if MC >= 26.1 && MC <= 26.3
            //$$ true;
            //#else
            false;
            //#endif

    @Test
    void modernAndLegacyLongNameNetworkPathsAreMutuallyExclusive() throws Exception {
        try (var input = getClass().getResourceAsStream("/carpet-fga-addition.mixins.json")) {
            assertNotNull(input);
            var config = JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            var common = names(config.getAsJsonArray("mixins"));
            var client = names(config.getAsJsonArray("client"));
            assertTrue(common.contains("ClientboundPlayerInfoUpdatePacketMixin"), "shared alias/decoration must remain");
            assertEquals(MC_26_3, common.contains("ClientboundOpenScreenPacketUnicodeProfileMixin"),
                    "Unicode profile sanitization follows the 26.3 packet API");
            assertEquals(MODERN_REJOIN_MIXIN, common.contains("FakePlayerRejoinVehicleMixin"),
                    "vehicle-preservation injection only targets the matching modern Carpet callback");
            for (String mixin : Set.of("ModernLongNameUtf8CodecMixin", "ModernLongNamePlayerInfoEncodeMixin",
                    "ModernLongNameServerHandshakeMixin")) {
                assertEquals(MODERN, common.contains(mixin), mixin);
            }
            for (String mixin : Set.of("ModernLongNamePlayerInfoDecodeMixin", "ModernLongNameClientHandshakeMixin")) {
                assertEquals(MODERN, client.contains(mixin), mixin);
            }
            for (String mixin : Set.of("FriendlyByteBufWriteMixin", "ServerGamePacketListenerImplMixin")) {
                assertEquals(!MODERN, common.contains(mixin), mixin);
            }
            for (String mixin : Set.of("FriendlyByteBufMixin", "ClientPacketListenerMixin")) {
                assertEquals(!MODERN, client.contains(mixin), mixin);
            }
            assertTrue(common.contains("CustomPacketPayloadMixin"), "shared payload codec registration must remain");
        }
    }

    private static Set<String> names(JsonArray entries) {
        var names = new HashSet<String>();
        entries.forEach(entry -> names.add(entry.getAsString()));
        assertEquals(entries.size(), names.size(), "duplicate Mixin registration");
        return names;
    }
}
