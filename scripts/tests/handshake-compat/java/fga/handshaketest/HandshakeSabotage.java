package fga.handshaketest;

/** Records that the bundled interference mixin really removed the handshake from the vanilla payload list. */
public final class HandshakeSabotage {
    public static volatile int removed;
    public static volatile boolean stripped;

    private HandshakeSabotage() {
    }
}
