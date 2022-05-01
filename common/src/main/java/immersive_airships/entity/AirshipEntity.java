package immersive_airships.entity;

import com.google.common.collect.Lists;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LilyPadBlock;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockLocating;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class AirshipEntity extends Entity {
    static final TrackedData<Integer> DAMAGE_WOBBLE_TICKS = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.INTEGER);
    static final TrackedData<Integer> DAMAGE_WOBBLE_SIDE = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.INTEGER);
    static final TrackedData<Float> DAMAGE_WOBBLE_STRENGTH = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.FLOAT);
    static final TrackedData<Integer> BOAT_TYPE = DataTracker.registerData(AirshipEntity.class, TrackedDataHandlerRegistry.INTEGER);

    float ticksUnderwater;
    int field_7708;

    public float yawVelocity;
    public float pitchVelocity;
    public float airshipPitch;

    double x;
    double y;
    double z;

    double boatYaw;
    double boatPitch;

    boolean pressingLeft;
    boolean pressingRight;
    boolean pressingForward;
    boolean pressingBack;
    boolean pressingUp;
    boolean pressingDown;

    double waterLevel;
    float slipperiness;
    Location location;
    Location lastLocation;
    double fallVelocity;

    public AirshipEntity(EntityType<? extends AirshipEntity> entityType, World world) {
        super(entityType, world);
        intersectionChecked = true;
    }

    @Override
    protected float getEyeHeight(EntityPose pose, EntityDimensions dimensions) {
        return dimensions.height;
    }

    @Override
    protected Entity.MoveEffect getMoveEffect() {
        return Entity.MoveEffect.NONE;
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(DAMAGE_WOBBLE_TICKS, 0);
        dataTracker.startTracking(DAMAGE_WOBBLE_SIDE, 1);
        dataTracker.startTracking(DAMAGE_WOBBLE_STRENGTH, 0.0f);
        dataTracker.startTracking(BOAT_TYPE, Type.OAK.ordinal());
    }

    @Override
    public boolean collidesWith(Entity other) {
        return AirshipEntity.canCollide(this, other);
    }

    public static boolean canCollide(Entity entity, Entity other) {
        return (other.isCollidable() || other.isPushable()) && !entity.isConnectedThroughVehicle(other);
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    protected Vec3d positionInPortal(Direction.Axis portalAxis, BlockLocating.Rectangle portalRect) {
        return LivingEntity.positionInPortal(super.positionInPortal(portalAxis, portalRect));
    }

    @Override
    public double getMountedHeightOffset() {
        return -0.1;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (world.isClient || isRemoved()) {
            return true;
        }
        setDamageWobbleSide(-getDamageWobbleSide());
        setDamageWobbleTicks(10);
        setDamageWobbleStrength(getDamageWobbleStrength() + amount * 10.0f);
        scheduleVelocityUpdate();
        emitGameEvent(GameEvent.ENTITY_DAMAGED, source.getAttacker());
        if (getDamageWobbleStrength() > 40.0f) {
            if (world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
                dropItem(asItem());
            }
            discard();
        }
        return true;
    }

    @Override
    public void onBubbleColumnSurfaceCollision(boolean drag) {
        world.addParticle(ParticleTypes.SPLASH, getX() + (double)random.nextFloat(), getY() + 0.7, getZ() + (double)random.nextFloat(), 0.0, 0.0, 0.0);
        if (random.nextInt(20) == 0) {
            world.playSound(getX(), getY(), getZ(), getSplashSound(), getSoundCategory(), 1.0f, 0.8f + 0.4f * random.nextFloat(), false);
        }
        emitGameEvent(GameEvent.SPLASH, getPrimaryPassenger());
    }

    @Override
    public void pushAwayFrom(Entity entity) {
        if (entity instanceof AirshipEntity) {
            if (entity.getBoundingBox().minY < getBoundingBox().maxY) {
                super.pushAwayFrom(entity);
            }
        } else if (entity.getBoundingBox().minY <= getBoundingBox().minY) {
            super.pushAwayFrom(entity);
        }
    }

    public Item asItem() {
        switch (getBoatType()) {
            default: {
                return Items.OAK_BOAT;
            }
            case SPRUCE: {
                return Items.SPRUCE_BOAT;
            }
            case BIRCH: {
                return Items.BIRCH_BOAT;
            }
            case JUNGLE: {
                return Items.JUNGLE_BOAT;
            }
            case ACACIA: {
                return Items.ACACIA_BOAT;
            }
            case DARK_OAK:
        }
        return Items.DARK_OAK_BOAT;
    }

    @Override
    public void animateDamage() {
        setDamageWobbleSide(-getDamageWobbleSide());
        setDamageWobbleTicks(10);
        setDamageWobbleStrength(getDamageWobbleStrength() * 11.0f);
    }

    @Override
    public boolean collides() {
        return !isRemoved();
    }

    @Override
    public void updateTrackedPositionAndAngles(double x, double y, double z, float yaw, float pitch, int interpolationSteps, boolean interpolate) {
        this.x = x;
        this.y = y;
        this.z = z;
        boatYaw = yaw;
        boatPitch = pitch;
        field_7708 = 10;
    }

    @Override
    public Direction getMovementDirection() {
        return getHorizontalFacing().rotateYClockwise();
    }

    @Override
    public void tick() {
        // pilot
        if (world.isClient() && getPassengerList().size() > 0) {
            Entity entity = getPassengerList().get(0);
            if (entity instanceof ClientPlayerEntity player) {
                setInputs(player.input.pressingLeft, player.input.pressingRight, player.input.pressingForward, player.input.pressingBack, player.input.jumping, player.input.sneaking);
            }
        }

        lastLocation = location;
        location = checkLocation();
        ticksUnderwater = location == Location.UNDER_WATER || location == Location.UNDER_FLOWING_WATER ? ticksUnderwater + 1.0f : 0.0f;
        if (!world.isClient && ticksUnderwater >= 60.0f) {
            removeAllPassengers();
        }
        if (getDamageWobbleTicks() > 0) {
            setDamageWobbleTicks(getDamageWobbleTicks() - 1);
        }
        if (getDamageWobbleStrength() > 0.0f) {
            setDamageWobbleStrength(getDamageWobbleStrength() - 1.0f);
        }
        super.tick();
        wobble();
        if (isLogicalSideForUpdatingMovement()) {
            updateVelocity();
            if (world.isClient) {
                updateController();
            }
            move(MovementType.SELF, getVelocity());
        } else {
            setVelocity(Vec3d.ZERO);
        }

        checkBlockCollision();
        List<Entity> list = world.getOtherEntities(this, getBoundingBox().expand(0.2f, -0.01f, 0.2f), EntityPredicates.canBePushedBy(this));
        if (!list.isEmpty()) {
            boolean bl = !world.isClient && !(getPrimaryPassenger() instanceof PlayerEntity);
            for (Entity entity : list) {
                if (entity.hasPassenger(this)) continue;
                if (bl && getPassengerList().size() < 2 && !entity.hasVehicle() && entity.getWidth() < getWidth() && entity instanceof LivingEntity && !(entity instanceof WaterCreatureEntity) && !(entity instanceof PlayerEntity)) {
                    entity.startRiding(this);
                    continue;
                }
                pushAwayFrom(entity);
            }
        }
    }

    private void wobble() {
        if (isLogicalSideForUpdatingMovement()) {
            field_7708 = 0;
            updateTrackedPosition(getX(), getY(), getZ());
        }
        if (field_7708 <= 0) {
            return;
        }
        if (true) {
            return;
        }
        double d = getX() + (x - getX()) / (double)field_7708;
        double e = getY() + (y - getY()) / (double)field_7708;
        double f = getZ() + (z - getZ()) / (double)field_7708;
        double g = MathHelper.wrapDegrees(boatYaw - (double)getYaw());
        setYaw(getYaw() + (float)g / (float)field_7708);
        setPitch(getPitch() + (float)(boatPitch - (double)getPitch()) / (float)field_7708);
        --field_7708;
        setPosition(d, e, f);
        setRotation(getYaw(), getPitch());
    }

    private Location checkLocation() {
        Location location = getUnderWaterLocation();
        if (location != null) {
            waterLevel = getBoundingBox().maxY;
            return location;
        }
        if (checkBoatInWater()) {
            return Location.IN_WATER;
        }
        slipperiness = getSlipperiness();
        if (slipperiness > 0.0f) {
            return Location.ON_LAND;
        }
        return Location.IN_AIR;
    }

    public float method_7544() {
        Box box = getBoundingBox();
        int i = MathHelper.floor(box.minX);
        int j = MathHelper.ceil(box.maxX);
        int k = MathHelper.floor(box.maxY);
        int l = MathHelper.ceil(box.maxY - fallVelocity);
        int m = MathHelper.floor(box.minZ);
        int n = MathHelper.ceil(box.maxZ);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        block0:
        for (int o = k; o < l; ++o) {
            float f = 0.0f;
            for (int p = i; p < j; ++p) {
                for (int q = m; q < n; ++q) {
                    mutable.set(p, o, q);
                    FluidState fluidState = world.getFluidState(mutable);
                    if (fluidState.isIn(FluidTags.WATER)) {
                        f = Math.max(f, fluidState.getHeight(world, mutable));
                    }
                    if (f >= 1.0f) continue block0;
                }
            }
            if (!(f < 1.0f)) continue;
            return (float)mutable.getY() + f;
        }
        return l + 1;
    }

    public float getSlipperiness() {
        Box box = getBoundingBox();
        Box box2 = new Box(box.minX, box.minY - 0.001, box.minZ, box.maxX, box.minY, box.maxZ);
        int i = MathHelper.floor(box2.minX) - 1;
        int j = MathHelper.ceil(box2.maxX) + 1;
        int k = MathHelper.floor(box2.minY) - 1;
        int l = MathHelper.ceil(box2.maxY) + 1;
        int m = MathHelper.floor(box2.minZ) - 1;
        int n = MathHelper.ceil(box2.maxZ) + 1;
        VoxelShape voxelShape = VoxelShapes.cuboid(box2);
        float f = 0.0f;
        int o = 0;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int p = i; p < j; ++p) {
            for (int q = m; q < n; ++q) {
                int r = (p == i || p == j - 1 ? 1 : 0) + (q == m || q == n - 1 ? 1 : 0);
                if (r == 2) continue;
                for (int s = k; s < l; ++s) {
                    if (r > 0 && (s == k || s == l - 1)) continue;
                    mutable.set(p, s, q);
                    BlockState blockState = world.getBlockState(mutable);
                    if (blockState.getBlock() instanceof LilyPadBlock || !VoxelShapes.matchesAnywhere(blockState.getCollisionShape(world, mutable).offset(p, s, q), voxelShape, BooleanBiFunction.AND)) continue;
                    f += blockState.getBlock().getSlipperiness();
                    ++o;
                }
            }
        }
        return f / (float)o;
    }

    private boolean checkBoatInWater() {
        Box box = getBoundingBox();
        int i = MathHelper.floor(box.minX);
        int j = MathHelper.ceil(box.maxX);
        int k = MathHelper.floor(box.minY);
        int l = MathHelper.ceil(box.minY + 0.001);
        int m = MathHelper.floor(box.minZ);
        int n = MathHelper.ceil(box.maxZ);
        boolean bl = false;
        waterLevel = -1.7976931348623157E308;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int o = i; o < j; ++o) {
            for (int p = k; p < l; ++p) {
                for (int q = m; q < n; ++q) {
                    mutable.set(o, p, q);
                    FluidState fluidState = world.getFluidState(mutable);
                    if (!fluidState.isIn(FluidTags.WATER)) continue;
                    float f = (float)p + fluidState.getHeight(world, mutable);
                    waterLevel = Math.max(f, waterLevel);
                    bl |= box.minY < (double)f;
                }
            }
        }
        return bl;
    }

    @Nullable
    private Location getUnderWaterLocation() {
        Box box = getBoundingBox();
        double d = box.maxY + 0.001;
        int i = MathHelper.floor(box.minX);
        int j = MathHelper.ceil(box.maxX);
        int k = MathHelper.floor(box.maxY);
        int l = MathHelper.ceil(d);
        int m = MathHelper.floor(box.minZ);
        int n = MathHelper.ceil(box.maxZ);
        boolean bl = false;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int o = i; o < j; ++o) {
            for (int p = k; p < l; ++p) {
                for (int q = m; q < n; ++q) {
                    mutable.set(o, p, q);
                    FluidState fluidState = world.getFluidState(mutable);
                    if (!fluidState.isIn(FluidTags.WATER) || !(d < (double)((float)mutable.getY() + fluidState.getHeight(world, mutable)))) continue;
                    if (fluidState.isStill()) {
                        bl = true;
                        continue;
                    }
                    return Location.UNDER_FLOWING_WATER;
                }
            }
        }
        return bl ? Location.UNDER_WATER : null;
    }

    abstract void updateVelocity();

    abstract void updateController();

    @Override
    public void updatePassengerPosition(Entity passenger) {
        if (!hasPassenger(passenger)) {
            return;
        }
        float f = 0.0f;
        float g = (float)((isRemoved() ? (double)0.01f : getMountedHeightOffset()) + passenger.getHeightOffset());
        if (getPassengerList().size() > 1) {
            int i = getPassengerList().indexOf(passenger);
            f = i == 0 ? 0.2f : -0.6f;
            if (passenger instanceof AnimalEntity) {
                f += 0.2f;
            }
        }
        Vec3d vec3d = new Vec3d(f, 0.0, 0.0).rotateY(-getYaw() * ((float)Math.PI / 180) - 1.5707964f);
        passenger.setPosition(getX() + vec3d.x, getY() + (double)g, getZ() + vec3d.z);
        passenger.setYaw(passenger.getYaw() + yawVelocity);
        passenger.setHeadYaw(passenger.getHeadYaw() + yawVelocity);
        copyEntityData(passenger);
        if (passenger instanceof AnimalEntity && getPassengerList().size() > 1) {
            int j = passenger.getId() % 2 == 0 ? 90 : 270;
            passenger.setBodyYaw(((AnimalEntity)passenger).bodyYaw + (float)j);
            passenger.setHeadYaw(passenger.getHeadYaw() + (float)j);
        }
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        double e;
        Vec3d vec3d = AirshipEntity.getPassengerDismountOffset(getWidth() * MathHelper.SQUARE_ROOT_OF_TWO, passenger.getWidth(), passenger.getYaw());
        double d = getX() + vec3d.x;
        BlockPos blockPos = new BlockPos(d, getBoundingBox().maxY, e = getZ() + vec3d.z);
        BlockPos blockPos2 = blockPos.down();
        if (!world.isWater(blockPos2)) {
            double g;
            ArrayList<Vec3d> list = Lists.newArrayList();
            double f = world.getDismountHeight(blockPos);
            if (Dismounting.canDismountInBlock(f)) {
                list.add(new Vec3d(d, (double)blockPos.getY() + f, e));
            }
            if (Dismounting.canDismountInBlock(g = world.getDismountHeight(blockPos2))) {
                list.add(new Vec3d(d, (double)blockPos2.getY() + g, e));
            }
            for (EntityPose entityPose : passenger.getPoses()) {
                for (Vec3d vec3d2 : list) {
                    if (!Dismounting.canPlaceEntityAt(world, vec3d2, passenger, entityPose)) continue;
                    passenger.setPose(entityPose);
                    return vec3d2;
                }
            }
        }
        return super.updatePassengerForDismount(passenger);
    }

    protected void copyEntityData(Entity entity) {
        entity.setBodyYaw(getYaw());
        float f = MathHelper.wrapDegrees(entity.getYaw() - getYaw());
        float g = MathHelper.clamp(f, -105.0f, 105.0f);
        entity.prevYaw += g - f;
        entity.setYaw(entity.getYaw() + g - f);
        entity.setHeadYaw(entity.getYaw());
    }

    @Override
    public void onPassengerLookAround(Entity passenger) {
        copyEntityData(passenger);
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putString("Type", getBoatType().getName());
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("Type", 8)) {
            setBoatType(Type.getType(nbt.getString("Type")));
        }
    }

    @Override
    public boolean hasNoGravity() {
        return true;
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.shouldCancelInteraction()) {
            return ActionResult.PASS;
        }
        if (ticksUnderwater < 60.0f) {
            if (!world.isClient) {
                return player.startRiding(this) ? ActionResult.CONSUME : ActionResult.PASS;
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState landedState, BlockPos landedPosition) {
        fallVelocity = getVelocity().y;
        if (hasVehicle()) {
            return;
        }
        if (onGround) {
            if (fallDistance > 3.0f) {
                if (location != Location.ON_LAND) {
                    onLanding();
                    return;
                }
                handleFallDamage(fallDistance, 1.0f, DamageSource.FALL);
                if (!world.isClient && !isRemoved()) {
                    kill();
                    if (world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
                        int i;
                        for (i = 0; i < 3; ++i) {
                            dropItem(getBoatType().getBaseBlock());
                        }
                        for (i = 0; i < 2; ++i) {
                            dropItem(Items.STICK);
                        }
                    }
                }
            }
            onLanding();
        } else if (!world.getFluidState(getBlockPos().down()).isIn(FluidTags.WATER) && heightDifference < 0.0) {
            fallDistance -= (float)heightDifference;
        }
    }

    public void setDamageWobbleStrength(float wobbleStrength) {
        dataTracker.set(DAMAGE_WOBBLE_STRENGTH, wobbleStrength);
    }

    public float getDamageWobbleStrength() {
        return dataTracker.get(DAMAGE_WOBBLE_STRENGTH);
    }

    public void setDamageWobbleTicks(int wobbleTicks) {
        dataTracker.set(DAMAGE_WOBBLE_TICKS, wobbleTicks);
    }

    public int getDamageWobbleTicks() {
        return dataTracker.get(DAMAGE_WOBBLE_TICKS);
    }

    public void setDamageWobbleSide(int side) {
        dataTracker.set(DAMAGE_WOBBLE_SIDE, side);
    }

    public int getDamageWobbleSide() {
        return dataTracker.get(DAMAGE_WOBBLE_SIDE);
    }

    public void setBoatType(Type type) {
        dataTracker.set(BOAT_TYPE, type.ordinal());
    }

    public Type getBoatType() {
        return Type.getType(dataTracker.get(BOAT_TYPE));
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengerList().size() < 2 && !isSubmergedIn(FluidTags.WATER);
    }

    @Override
    @Nullable
    public Entity getPrimaryPassenger() {
        return getFirstPassenger();
    }

    public void setInputs(boolean pressingLeft, boolean pressingRight, boolean pressingForward, boolean pressingBack, boolean pressingUp, boolean pressingDown) {
        this.pressingLeft = pressingLeft;
        this.pressingRight = pressingRight;
        this.pressingForward = pressingForward;
        this.pressingBack = pressingBack;
        this.pressingUp = pressingUp;
        this.pressingDown = pressingDown;
    }

    @Override
    public Packet<?> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    @Override
    public boolean isSubmergedInWater() {
        return location == Location.UNDER_WATER || location == Location.UNDER_FLOWING_WATER;
    }

    @Override
    public ItemStack getPickBlockStack() {
        return new ItemStack(asItem());
    }

    public boolean canDismount() {
        return location == Location.ON_LAND;
    }

    public enum Type {
        OAK(Blocks.OAK_PLANKS, "oak"),
        SPRUCE(Blocks.SPRUCE_PLANKS, "spruce"),
        BIRCH(Blocks.BIRCH_PLANKS, "birch"),
        JUNGLE(Blocks.JUNGLE_PLANKS, "jungle"),
        ACACIA(Blocks.ACACIA_PLANKS, "acacia"),
        DARK_OAK(Blocks.DARK_OAK_PLANKS, "dark_oak");

        private final String name;
        private final Block baseBlock;

        Type(Block baseBlock, String name) {
            this.name = name;
            this.baseBlock = baseBlock;
        }

        public String getName() {
            return name;
        }

        public Block getBaseBlock() {
            return baseBlock;
        }

        public String toString() {
            return name;
        }

        public static Type getType(int type) {
            Type[] types = Type.values();
            if (type < 0 || type >= types.length) {
                type = 0;
            }
            return types[type];
        }

        public static Type getType(String name) {
            Type[] types = Type.values();
            for (Type type : types) {
                if (!type.getName().equals(name)) continue;
                return type;
            }
            return types[0];
        }
    }

    public enum Location {
        IN_WATER,
        UNDER_WATER,
        UNDER_FLOWING_WATER,
        ON_LAND,
        IN_AIR
    }
}

