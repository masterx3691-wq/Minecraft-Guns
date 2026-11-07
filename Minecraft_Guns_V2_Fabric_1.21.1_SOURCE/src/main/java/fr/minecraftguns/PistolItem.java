package fr.minecraftguns;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

public class PistolItem extends Item {
    private static final int CAPACITY = 12;
    private static final int RELOAD_TICKS = 32;
    public PistolItem(Settings settings) { super(settings); }

    private static NbtCompound data(ItemStack stack) {
        return stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
    }
    private static void put(ItemStack stack, NbtCompound tag) {
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(tag));
    }
    private static int rounds(ItemStack stack) { NbtCompound tag = data(stack); return tag.contains("Rounds") ? tag.getInt("Rounds") : CAPACITY; }
    private static long reloadUntil(ItemStack stack) { return data(stack).getLong("ReloadUntil"); }
    private static void model(ItemStack stack, int frame) {
        int current = stack.contains(DataComponentTypes.CUSTOM_MODEL_DATA) ? stack.get(DataComponentTypes.CUSTOM_MODEL_DATA).value() : 0;
        if (current != frame) stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(frame));
    }
    private static int looseAmmo(ServerPlayerEntity player) {
        int total = 0;
        for (int i=0; i<player.getInventory().size(); i++) {
            ItemStack item = player.getInventory().getStack(i);
            if (item.isOf(MinecraftGuns.AMMO)) total += item.getCount();
        }
        return total;
    }
    public static void tryReload(ServerPlayerEntity player) {
        ItemStack stack = player.getMainHandStack();
        if (!stack.isOf(MinecraftGuns.PISTOL)) return;
        if (reloadUntil(stack) > player.getServerWorld().getTime() || rounds(stack) >= CAPACITY) return;
        if (looseAmmo(player) == 0) { player.sendMessage(Text.literal("§cPlus de balles !"), true); return; }
        NbtCompound tag = data(stack);
        tag.putLong("ReloadUntil", player.getServerWorld().getTime() + RELOAD_TICKS);
        put(stack,tag);
        model(stack,2);
        player.getWorld().playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.PLAYERS,0.6f,1.4f);
        player.sendMessage(Text.literal("§6Rechargement..."),true);
    }
    public static void tick(ServerPlayerEntity player, ItemStack stack) {
        long now = player.getServerWorld().getTime();
        NbtCompound tag=data(stack);
        long until=tag.getLong("ReloadUntil");
        if (until > 0) {
            if (now < until) { model(stack, now < until-15 ? 2 : 3); return; }
            int needed=CAPACITY-rounds(stack);
            int filled=0;
            for(int i=0;i<player.getInventory().size() && filled<needed;i++) {
                ItemStack item=player.getInventory().getStack(i);
                if (!item.isOf(MinecraftGuns.AMMO)) continue;
                int take=Math.min(item.getCount(),needed-filled);
                item.decrement(take);
                filled += take;
            }
            tag.putInt("Rounds",rounds(stack)+filled);
            tag.remove("ReloadUntil");
            put(stack,tag);
            model(stack,0);
            player.getWorld().playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,SoundCategory.PLAYERS,0.6f,1.7f);
            player.sendMessage(Text.literal("§aChargeur : "+rounds(stack)+"/"+CAPACITY+" §7| Réserve : "+looseAmmo(player)),true);
            return;
        }
        if(now-tag.getLong("ShotAt") >= 3) model(stack,0);
        if(now%20==0) player.sendMessage(Text.literal("§e"+rounds(stack)+"/"+CAPACITY+" §7| Balles : "+looseAmmo(player)+" §8| R : recharger"),true);
    }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack=player.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        if (reloadUntil(stack)>world.getTime()) return TypedActionResult.fail(stack);
        if (player.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(stack);
        if(rounds(stack)<=0) {
            world.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.BLOCK_LEVER_CLICK,SoundCategory.PLAYERS,0.5f,1.7f);
            player.sendMessage(Text.literal("§cChargeur vide ! Appuie sur R"),true);
            return TypedActionResult.fail(stack);
        }
        NbtCompound tag=data(stack);
        tag.putInt("Rounds",rounds(stack)-1);
        tag.putLong("ShotAt",world.getTime());
        put(stack,tag);
        model(stack,1);
        player.getItemCooldownManager().set(this,6);
        world.playSound(null,player.getX(),player.getY(),player.getZ(),SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,SoundCategory.PLAYERS,0.65f,1.75f);
        Vec3d start=player.getEyePos();
        Vec3d look=player.getRotationVec(1f).normalize();
        Vec3d far=start.add(look.multiply(32));
        HitResult wall=world.raycast(new RaycastContext(start,far,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,player));
        Vec3d end=wall.getType()==HitResult.Type.MISS?far:wall.getPos();
        Box area=new Box(start,end).expand(1.5);
        LivingEntity target=null;
        double best=start.distanceTo(end)+0.001;
        for(Entity e:world.getOtherEntities(player,area,entity->entity instanceof LivingEntity)) {
            LivingEntity c=(LivingEntity)e;
            if(!c.isAlive()||c.isSpectator())continue;
            var hit=c.getBoundingBox().expand(0.2).raycast(start,end);
            if(hit.isPresent()) {
                double distance=start.distanceTo(hit.get());
                if(distance<best){best=distance;target=c;}
            }
        }
        if(target!=null) {
            target.damage(world.getDamageSources().playerAttack(player),8f);
            end=start.add(look.multiply(best));
        }
        ServerWorld server=(ServerWorld)world;
        double length=start.distanceTo(end);
        for(double d=.5;d<length;d+=.9){Vec3d p=start.add(look.multiply(d));server.spawnParticles(ParticleTypes.CRIT,p.x,p.y,p.z,1,0,0,0,0);}
        server.spawnParticles(ParticleTypes.SMOKE,end.x,end.y,end.z,5,.08,.08,.08,.02);
        player.sendMessage(Text.literal("§e"+rounds(stack)+"/"+CAPACITY+" §7| Balles : "+looseAmmo((ServerPlayerEntity)player)),true);
        return TypedActionResult.success(stack);
    }
}
