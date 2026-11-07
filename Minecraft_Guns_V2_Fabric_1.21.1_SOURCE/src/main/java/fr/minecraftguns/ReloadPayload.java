package fr.minecraftguns;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ReloadPayload() implements CustomPayload {
    public static final Id<ReloadPayload> ID = new Id<>(Identifier.of(MinecraftGuns.MOD_ID, "reload"));
    public static final PacketCodec<RegistryByteBuf, ReloadPayload> CODEC = PacketCodec.unit(new ReloadPayload());
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}
