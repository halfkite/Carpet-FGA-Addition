package carpet.fga;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Source-maintained smoke-test procedures for feature work and version ports.
 *
 * <p>When a feature is added or changed, update this catalog in the same
 * change as the implementation and the corresponding script. The procedure
 * must name one baseline version, the version gate, the exact commands or
 * actions, and an observable expected result. A build proves compilation only;
 * a gameplay claim belongs here only after the listed smoke test has run.</p>
 *
 * <p>Version-port workflow: run the baseline plan first, archive the tested
 * artifact, obtain confirmation, then copy the plan to each remaining build
 * node and record version-specific differences here.</p>
 */
final class FeatureSmokeTestPlanTest {
    private record SmokePlan(
            String feature,
            String versionGate,
            String baseline,
            String script,
            String procedure,
            String expected) {
    }

    /**
     * Keep every active feature's test flow in source so an upgrade can be
     * repeated from the implementation repository without relying on chat
     * history.
     */
    private static final List<SmokePlan> PLANS = List.of(
            new SmokePlan(
                    "unlimitedMultiplayerPlayers",
                    "MC >= 1.21",
                    "1.21.1",
                    "scripts/powershell/unlimited-multiplayer-players-network-smoke-1.21.1.ps1",
                    "Set max-players below the fake-player count; keep the FGA rule false; enable GCA "
                            + "fakePlayerResident; summon 20 fake players; restart the isolated server; "
                            + "then reconnect all fake players.",
                    "All 20 fake players join before and after restart, while ban, whitelist, and IP-ban checks remain active."),
            new SmokePlan(
                    "lightSourceStonecuttingRecipes",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.2",
                    "scripts/powershell/fake-player-stonecutter-recipe-smoke-all.ps1",
                    "Enable the rule and reload an isolated server; place a stonecutter; summon a fake player; "
                            + "give it a beacon or a high-version copper light source; inspect the generated tag "
                            + "and request light_level_01_15_from_light_source_stonecutter.",
                    "The recipe is registered, the copper tag entries exist only in versions that provide them, and the server stops cleanly."),
            new SmokePlan(
                    "namedEnderPearlTeleport",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.2",
                    "scripts/powershell/named-ender-pearl-teleport-smoke-all.ps1",
                    "Run the script without -VersionList for the complete build matrix, or pass "
                            + "-VersionList <version[,version...]> for an upgrade target; enable the rule; summon "
                            + "fake player 1 as the target and fake player 2 as the thrower; give player 2 an ender "
                            + "pearl named 1 and throw it at a blocking wall; compare player 1's position before and "
                            + "after the hit; take player 1 offline; give player 2 another pearl named 1 and compare "
                            + "player 2's position before and after the hit.",
                    "The first pearl moves fake player 1; after fake player 1 is offline, the second pearl does not "
                            + "teleport fake player 2 or any other fake player; the Mixin loads without an injection error."),
            new SmokePlan(
                    "soulSpeedNoDurability",
                    "MC >= 1.21 && MC <= 26.3",
                    "1.21.1",
                    "scripts/powershell/fake-player-stonecutter-soul-speed-smoke-1.21.1.ps1",
                    "Enable the rule; equip a fake player with Soul Speed III boots at a known durability; walk it "
                            + "at least 50 metres on soul sand; read the position and item durability afterward.",
                    "The player travels at least 50 metres and the boots' durability is unchanged."),
            new SmokePlan(
                    "thornsNoDurability",
                    "MC >= 1.21 && MC <= 26.3",
                    "1.21.1",
                    "MANUAL: server startup plus a controlled in-game hit",
                    "Enable the rule; equip Thorns armor with recorded durability; trigger a reflected hit; "
                            + "repeat with the rule disabled as the vanilla control.",
                    "With the rule enabled, reflected damage still occurs but the Thorns armor does not lose durability; record client and server observations."),
            new SmokePlan(
                    "playerPossessionCommandTreeRefresh",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.2",
                    "scripts/powershell/player-possession-command-refresh-smoke-26.2.ps1",
                    "Use an isolated integrated-server world with temporary commandPlayer true and playerPossession true; "
                            + "join once, without reconnecting, summon FGARefreshTarget, run /player FGARefreshTarget possess, "
                            + "then run /controlPlayer; restore the original Carpet config after the test.",
                    "The client accepts /player ... possess and /controlPlayer after the first join, logs the swapped state "
                            + "and the active controller, and does not report an unknown command."),
            new SmokePlan(
                    "fireAspectOnTools",
                    "MC >= 1.21 && MC <= 26.3",
                    "26.2",
                    "scripts/powershell/fire-aspect-tool-smoke-all.ps1",
                    "Enable fireAspectOnTools; summon fake players FGAFireOne, FGAFireTwo, and FGAFireFortune; "
                            + "give them a pickaxe with Fire Aspect I, a pickaxe with Fire Aspect II, and a pickaxe "
                            + "with Fire Aspect plus Fortune; have each fake player mine the prepared test block; "
                            + "record the resulting item stacks and the enchantment component before mining.",
                    "Fire Aspect I applies one smelting pass, Fire Aspect II applies two chained passes, and the "
                            + "Fire Aspect plus Fortune pickaxe produces smelted ore with the Fortune-enlarged count; "
                            + "the server stops cleanly without a Mixin injection failure."));

    @Test
    void everyPlanContainsAnExecutableUpgradeProcedure() {
        assertFalse(PLANS.isEmpty(), "the source test catalog must not be empty");
        for (SmokePlan plan : PLANS) {
            assertTrue(notBlank(plan.feature()), "missing feature name");
            assertTrue(notBlank(plan.versionGate()), plan.feature() + " is missing its version gate");
            assertTrue(notBlank(plan.baseline()), plan.feature() + " is missing its baseline version");
            assertTrue(notBlank(plan.script()), plan.feature() + " is missing its script or manual marker");
            assertTrue(notBlank(plan.procedure()), plan.feature() + " is missing its procedure");
            assertTrue(notBlank(plan.expected()), plan.feature() + " is missing its expected result");
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
