package fr.minecraftguns;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.util.hit.HitResult;

public class PistolItem extends Item {
    public PistolItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (player.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        if (world.isClient) {
            return TypedActionResult.success(stack);
        }
        ServerWorld serverWorld = (ServerWorld) world;
        player.getItemCooldownManager().set(this, 8); // 0.4 seconde
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 0.7f, 1.7f);

        Vec3d start = player.getEyePos();
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d maxEnd = start.add(look.multiply(32.0));
        HitResult wall = world.raycast(new RaycastContext(start, maxEnd,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        Vec3d end = wall.getType() == HitResult.Type.MISS ? maxEnd : wall.getPos();
        double maxDistance = start.distanceTo(end);

        Box searchArea = new Box(start, end).expand(1.5);
        LivingEntity target = null;
        double best = maxDistance + 0.01;
        for (Entity entity : world.getOtherEntities(player, searchArea, e -> e instanceof LivingEntity)) {
            LivingEntity candidate = (LivingEntity) entity;
            if (!candidate.isAlive() || candidate.isSpectator()) continue;
            Box hitbox = candidate.getBoundingBox().expand(0.25);
            var collision = hitbox.raycast(start, end);
            if (collision.isPresent()) {
                double dist = start.distanceTo(collision.get());
                if (dist < best) {
                    best = dist;
                    target = candidate;
                }
            }
        }
        if (target != null) {
            target.damage(world.getDamageSources().playerAttack(player), 8.0f);
            end = start.add(look.multiply(best));
        }
        double length = start.distanceTo(end);
        for (double distance = 0.7; distance < length; distance += 0.6) {
            Vec3d point = start.add(look.multiply(distance));
            serverWorld.spawnParticles(ParticleTypes.CRIT, point.x, point.y, point.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        serverWorld.spawnParticles(ParticleTypes.SMOKE, end.x, end.y, end.z,
                6, 0.08, 0.08, 0.08, 0.02);
        return TypedActionResult.success(stack);
    }
}
