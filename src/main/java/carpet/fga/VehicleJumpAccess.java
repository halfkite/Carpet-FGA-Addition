//#if MC >= 1.21.1 && MC <= 26.3
package carpet.fga;

/** Server-validated input only; implementations retain cooldown state on the vehicle. */
public interface VehicleJumpAccess {
    boolean carpetFga$tryJump();
}
//#endif
