package immersive_aircraft.network.c2s;

import immersive_aircraft.cobalt.network.Message;
import immersive_aircraft.entity.VehicleEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

public class CommandMessage implements Message {
    private final Key key;
    private final double fx;
    private final double fy;
    private final double fz;

    public CommandMessage(Key key, Vec3d velocity) {
        this.key = key;
        this.fx = velocity.x;
        this.fy = velocity.y;
        this.fz = velocity.z;
    }

    @Override
    public void receive(PlayerEntity e) {
        if (e.getRootVehicle() instanceof VehicleEntity vehicle) {
            if (key == Key.DISMOUNT) {
                e.stopRiding();
                vehicle.chill();
                vehicle.setVelocity(fx, fy, fz);
            } else if (key == Key.BOOST && vehicle.canBoost()) {
                vehicle.boost();
            }
        }
    }

    public enum Key {
        DISMOUNT,
        BOOST
    }
}
