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
import net.minecraft.nbt.StringTag;
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
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * QA (lane b, 2026-11-08), from B1065's promise: "a chunk full of [blocks built from a file] stays far under the limit"
 * a client accepts (8 MiB a packet, CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH), and a block built from a file holds
 * at most what a player's could. A banner's pattern layers are block data a builder copies; a player's loom adds at most
 * 6 layers to a banner. A hand-written file can give a banner thousands, and every layer is sent to each player who comes
 * near, so a wall of such banners from one upload would keep every player out of that chunk.
 */
public class QaBannerChunkSize1108bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos SRC = new BlockPos(26, 2, 26);
	private static final int WIDE = 8;
	private static final int DEEP = 6;
	/** Layers a banner in the file has (a player's loom gives at most 6). */
	private static final int LAYERS = 5000;
	/** The largest packet a client accepts, uncompressed (CompressionDecoder.MAXIMUM_UNCOMPRESSED_LENGTH). */
	private static final int MAX_PACKET = 8388608;
	private static final String[] PATTERNS = {"minecraft:stripe_top", "minecraft:stripe_bottom", "minecraft:cross", "minecraft:border"};
	private static final String[] COLORS = {"red", "blue", "yellow", "lime"};

	//$ gametest_ticks_batch HUGE '4000' '"qaBannerChunkSize1108b"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaBannerChunkSize1108b")
	public void aChunkOfBannersFromAnUploadedFileStaysUnderThePacketLimit(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();

		// The build goes in one chunk: its corner on the first chunk border at least 4 blocks into the area
		BlockPos min = helper.absolutePos(BlockPos.ZERO);
		int x0 = ((min.getX() + 4 + 15) >> 4) << 4;
		int z0 = ((min.getZ() + 4 + 15) >> 4) << 4;
		BlockPos origin = new BlockPos(x0, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), z0);

		// A one-banner template made by the game gives the banner's state and saved data; the file repeats it WIDE x DEEP times
		helper.setBlock(SRC, Blocks.WHITE_BANNER);
		StructureTemplate template = new StructureTemplate();
		template.fillFromWorld(level, helper.absolutePos(SRC), new Vec3i(1, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(SRC, Blocks.AIR);
		CompoundTag file = template.save(new CompoundTag());
		CompoundTag one = file.getList("blocks", Tag.TAG_COMPOUND).getCompound(0);
		CompoundTag data = one.getCompound("nbt");
		ListTag patterns = new ListTag();
		for (int i = 0; i < LAYERS; i++) {
			CompoundTag layer = new CompoundTag();
			layer.putString("pattern", PATTERNS[i % PATTERNS.length]);
			layer.putString("color", COLORS[(i / PATTERNS.length) % COLORS.length]);
			patterns.add(layer);
		}
		data.put("patterns", patterns);
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
			id = BlueprintImporter.importBytes(level.getServer(), "uploads/qa_bannerman",
				"banner_wall_" + Long.toHexString(level.getGameTime()) + ".nbt", bytes).id();
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
			int banners = 0;
			int most = 0;
			for (int x = 0; x < WIDE; x++) {
				for (int z = 0; z < DEEP; z++) {
					if (level.getBlockEntity(origin.offset(x, 0, z)) instanceof BannerBlockEntity banner) {
						banners++;
						most = Math.max(most, banner.getPatterns().layers().size());
					}
				}
			}
			helper.assertTrue(banners == WIDE * DEEP, "(setup) only " + banners + " of " + WIDE * DEEP + " banners were built");
			if (measured[0] < 0) {
				// measured once (building the packet is slow), as the game builds it for each player who comes near
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
			}
			helper.assertTrue(measured[0] <= MAX_PACKET, "the chunk holding " + banners + " banners a builder built from one uploaded file"
				+ " (up to " + most + " pattern layers each; a player's loom gives at most 6) is sent in a packet of " + measured[0]
				+ " bytes (built in " + measured[1] + " ms on the server thread), over the " + MAX_PACKET + " a client accepts, so every"
				+ " player who comes near is disconnected; a banner from a file should carry no more than a player's banner could");
		});
	}
}
