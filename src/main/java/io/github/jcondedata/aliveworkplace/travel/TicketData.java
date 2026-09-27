package io.github.jcondedata.aliveworkplace.travel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Where a Travel Ticket goes: the destination post (by id, in case it moves) and its name for the tooltip. */
public record TicketData(UUID post, GlobalPos destination, String name) {
	public static final Codec<TicketData> CODEC = RecordCodecBuilder.create(i -> i.group(
		UUIDUtil.CODEC.fieldOf("post").forGetter(TicketData::post),
		GlobalPos.CODEC.fieldOf("destination").forGetter(TicketData::destination),
		Codec.STRING.fieldOf("name").forGetter(TicketData::name)
	).apply(i, TicketData::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, TicketData> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
}
