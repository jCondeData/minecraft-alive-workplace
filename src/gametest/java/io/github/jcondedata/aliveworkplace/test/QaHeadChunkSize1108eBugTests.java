package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintImporter;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * QA (lane-e-1108-2212), B1065's and B1073's kind for heads: a block built from an uploaded file should hold no more than
 * a player's block could (B1047's promise), because every player who comes near is sent the chunk's block data and a
 * client drops any packet over 8 MiB uncompressed. A head's saved "profile" (the skin it shows) is copied whole, with
 * any number of properties of any length; a survival player's mob head has no profile at all, and a real player's head
 * carries one signed texture property of about a kilobyte. Here 48 zombie heads (a survival block: a charged creeper's
 * drop) in one chunk, each with a profile of four 60,000-character properties, built by a builder from one upload.
 */
public class QaHeadChunkSize1108eBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(26, 2, 26);
	private static final int WIDE = 8;
	private static final int DEEP = 6;
	private static final int PROPERTIES = 4;
	private static final int LENGTH = 60_000;
	/** The largest packet a client accepts, uncompressed (CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH). */
	private static final int MAX_PACKET = 8388608;

	//$ gametest_ticks_batch HUGE '4000' '"qaHeadChunkSize1108e"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaHeadChunkSize1108e")
	public void aChunkOfHeadsFromAnUploadedFileStaysUnderThePacketLimit(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();

		BlockPos min = helper.absolutePos(BlockPos.ZERO);
		int x0 = ((min.getX() + 4 + 15) >> 4) << 4;
		int z0 = ((min.getZ() + 4 + 15) >> 4) << 4;
		BlockPos origin = new BlockPos(x0, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), z0);

		helper.setBlock(SRC, Blocks.ZOMBIE_HEAD);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(SRC, Blocks.AIR);
		CompoundTag file = template.save(new CompoundTag());
		CompoundTag one = file.getList("blocks", Tag.TAG_COMPOUND).getCompound(0);
		CompoundTag data = one.getCompound("nbt");
		CompoundTag profile = new CompoundTag();
		profile.putString("name", "QaHead");
		ListTag properties = new ListTag();
		String value = "ewogICJ0aW1lc3RhbXAiIDog".repeat(LENGTH / 24);
		for (int i = 0; i < PROPERTIES; i++) {
			CompoundTag property = new CompoundTag();
			property.putString("name", "textures");
			property.putString("value", i + value.substring(1)); // distinct values: the profile keeps each one
			properties.add(property);
		}
		profile.put("properties", properties);
		data.put("profile", profile);
		one.put("nbt", data);
		ListTag blocks = new ListTag();
		for (int x = 0; x < WIDE; x++) {
			for (int z = 0; z < DEEP; z++) {
				CompoundTag entry = one.copy();
				ListTag pos = new ListTag();
				pos.add(IntTag.valueOf(x));
				pos.add(IntTag.valueOf(0));
				pos.add(IntTag.valueOf(z));
				entry.put("pos", pos);
				blocks.add(entry);
			}
		}
		file.put("blocks", blocks);
		ListTag size = new ListTag();
		size.add(IntTag.valueOf(WIDE));
		size.add(IntTag.valueOf(1));
		size.add(IntTag.valueOf(DEEP));
		file.put("size", size);
		byte[] bytes;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(file, out);
			bytes = out.toByteArray();
		} catch (IOException e) {
			throw new GameTestAssertException("(setup) couldn't write the file: " + e);
		}
		ResourceLocation id;
		try {
			// the import a player's upload at the Table goes through (TableServer: folder "uploads/<player>")
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_header",
				"head_wall_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
		} catch (Exception e) {
			throw new GameTestAssertException("(setup) the upload didn't import (" + bytes.length + " bytes): " + e);
		}

		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		int[] measured = {-1, 0, 0};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			int heads = 0;
			int withProfile = 0;
			for (int x = 0; x < WIDE; x++) {
				for (int z = 0; z < DEEP; z++) {
					if (level.getBlockEntity(origin.offset(x, 0, z)) instanceof SkullBlockEntity head) {
						heads++;
						if (head.getOwnerProfile() != null) {
							withProfile++;
						}
					}
				}
			}
			helper.assertTrue(heads == WIDE * DEEP, "(setup) only " + heads + " of " + WIDE * DEEP + " heads were built");
			if (measured[0] < 0) {
				LevelChunk chunk = level.getChunkAt(origin);
				long start = System.nanoTime();
				ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null);
				RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
				try {
					ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(buf, packet);
					measured[0] = buf.readableBytes();
				} finally {
					buf.release();
				}
				measured[1] = (int) ((System.nanoTime() - start) / 1_000_000L);
				measured[2] = withProfile;
			}
			helper.assertTrue(measured[0] <= MAX_PACKET, "the chunk holding " + heads + " zombie heads a builder built from one uploaded file ("
				+ measured[2] + " with the file's profile) is sent in a packet of " + measured[0] + " bytes (built in " + measured[1]
				+ " ms on the server thread), over the " + MAX_PACKET + " a client accepts, so every player who comes near is"
				+ " disconnected; a head from a file should carry no more than a player's head could");
		});
	}
}
