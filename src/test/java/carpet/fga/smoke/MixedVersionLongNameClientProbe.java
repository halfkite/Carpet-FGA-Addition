//#if MC == 26.3
//$$ package carpet.fga.smoke;
//$$
//$$ import net.fabricmc.api.ClientModInitializer;
//$$ import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//$$ import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
//$$ import net.fabricmc.loader.api.FabricLoader;
//$$ import net.minecraft.client.gui.screens.ConnectScreen;
//$$ import net.minecraft.client.gui.screens.TitleScreen;
//$$ import net.minecraft.client.multiplayer.ServerData;
//$$ import net.minecraft.client.multiplayer.resolver.ServerAddress;
//$$
//$$ /** Opt-in loopback integration probe; uses no FGA API so it can test old and unmodded clients. */
//$$ public final class MixedVersionLongNameClientProbe implements ClientModInitializer {
//$$     private boolean connecting;
//$$     private boolean joined;
//$$     private boolean shortNameSeen;
//$$     private int ticks;
//$$     private int stopAt = Integer.MAX_VALUE;
//$$
//$$     @Override
//$$     public void onInitializeClient() {
//$$         String endpoint = System.getProperty("fga.mixedVersion.endpoint");
//$$         if (endpoint == null || !endpoint.startsWith("127.0.0.1:")) {
//$$             throw new IllegalArgumentException("Mixed-version probe only connects to explicit loopback endpoints");
//$$         }
//$$         String expected = System.getProperty("fga.mixedVersion.expectedName", "FGA_LongFake_0001");
//$$         String version = FabricLoader.getInstance().getModContainer("carpet-fga-addition")
//$$                 .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("absent");
//$$         System.out.println("FGA_MIXED_VERSION_CLIENT: fga=" + version);
//$$         ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
//$$             joined = true;
//$$             System.out.println("FGA_MIXED_VERSION_JOIN_PASS: connected to " + endpoint);
//$$         });
//$$         ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
//$$             System.out.println("FGA_MIXED_VERSION_DISCONNECTED: joined=" + joined + ", shortNameSeen=" + shortNameSeen);
//$$             stopAt = ticks + 20;
//$$         });
//$$         ClientTickEvents.END_CLIENT_TICK.register(client -> {
//$$             ticks++;
//$$             if (!connecting && client.isGameLoadFinished() && ticks > 20) {
//$$                 connecting = true;
//$$                 ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(endpoint),
//$$                         new ServerData("FGA mixed-version smoke", endpoint, ServerData.Type.OTHER), false, null);
//$$             }
//$$             if (client.getConnection() != null && client.level != null) {
//$$                 var names = client.getConnection().getOnlinePlayers().stream()
//$$                         .map(info -> info.getProfile().name()).toList();
//$$                 if (!shortNameSeen && names.contains("FGAMixedShort")) {
//$$                     shortNameSeen = true;
//$$                     System.out.println("FGA_MIXED_VERSION_SHORT_NAME_PASS: FGAMixedShort");
//$$                 }
//$$                 if (shortNameSeen && names.contains(expected) && stopAt == Integer.MAX_VALUE) {
//$$                     System.out.println("FGA_MIXED_VERSION_LONG_NAME_PASS: profile=" + expected);
//$$                     stopAt = ticks + 40;
//$$                 }
//$$             }
//$$             if (ticks >= stopAt || ticks >= 2400) {
//$$                 if (ticks >= 2400) {
//$$                     System.err.println("FGA_MIXED_VERSION_TIMEOUT: connection test did not complete");
//$$                 }
//$$                 client.stop();
//$$             }
//$$         });
//$$     }
//$$ }
//#endif
