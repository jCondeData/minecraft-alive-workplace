package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (lane c, 2026-11-14) for B1244, the other side of its boundary: a far (unloaded) village whose saved name is
 * readable keeps it on the hall's Map waystone network (the made-up name is only for a blank one), and a blank one next to
 * it on the same Map gets its made-up name, each line rendered with its real distance and direction.
 */
public class QaWaystoneNames1114cCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final String LINE = "screen.aliveworkplace.hall.waystone";

	private static RealmData.Known known(ServerLevel level, BlockPos hall, Component name, UUID owner) {
		return new RealmData.Known(GlobalPos.of(level.dimension(), hall), name, Optional.of(owner), "Jesse", "hamlet", 0, 0, level.getGameTime());
	}

	/** Two far villages of the player's, unloaded, each with a waystone: "Willowmere" (saved) and one with an empty saved name. */
	//$ gametest_batch AREA '"qaWaystoneNames1114cFar"'
	@GameTest(template = AREA, batch = "qaWaystoneNames1114cFar")
	public void aFarVillageWithASavedNameKeepsItBesideABlankOne(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID owner = player.getUUID();
		BlockPos home = helper.absolutePos(WaystonesCompatTests.HALL);
		List<BlockPos> stones = List.of(home.offset(3000, 0, 0), home.offset(0, 0, -3000));
		List<BlockPos> halls = stones.stream().map(s -> s.offset(2, 0, 2)).toList();
		RealmData realms = RealmData.get(level.getServer());
		Runnable forget = () -> {
			for (BlockPos h : halls) {
				VillageWaystones.Data.get(level).forget(h);
				realms.forgetKnown(GlobalPos.of(level.dimension(), h));
			}
			VillageWaystones.Data.get(level).forget(home);
			realms.forgetKnown(GlobalPos.of(level.dimension(), home));
			level.removeBlock(home, false);
		};
		forget.run();
		try {
			level.setBlock(home, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
			if (!(level.getBlockEntity(home) instanceof VillageHallBlockEntity entity)) {
				throw new GameTestAssertException("setup: no hall at " + home);
			}
			entity.setCustomName(Component.literal("Home"));
			entity.setOwner(owner, "Jesse");
			for (BlockPos h : halls) {
				helper.assertFalse(level.isLoaded(h), "setup: a far village's hall is loaded at " + h);
			}
			realms.putKnown(known(level, halls.get(0), Component.literal("Willowmere"), owner));
			realms.putKnown(known(level, halls.get(1), Component.empty(), owner));
			CompoundTag tag = VillageWaystones.Data.get(level).save(new CompoundTag(), level.registryAccess());
			long[] oldHalls = tag.getLongArray("halls");
			long[] oldStones = tag.getLongArray("stones");
			long[] h = java.util.Arrays.copyOf(oldHalls, oldHalls.length + 2);
			long[] s = java.util.Arrays.copyOf(oldStones, oldStones.length + 2);
			for (int i = 0; i < 2; i++) {
				h[oldHalls.length + i] = halls.get(i).asLong();
				s[oldStones.length + i] = stones.get(i).asLong();
			}
			tag.putLongArray("halls", h);
			tag.putLongArray("stones", s);
			level.getDataStorage().set("aliveworkplace_village_waystones", VillageWaystones.Data.load(tag, level.registryAccess()));

			List<VillageWaystones.Village> network = VillageWaystones.network(level, home, player);
			List<String> lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, home), LINE);
			String named = network.stream().filter(v -> v.hall().equals(halls.get(0))).findFirst().map(v -> v.name().getString()).orElse("(not listed)");
			helper.assertTrue("Willowmere".equals(named), "a far village saved as Willowmere is listed as '" + named + "' (made-up: "
				+ VillageHalls.madeUpName(halls.get(0)).getString() + ")");
			helper.assertTrue(lines.contains("Willowmere, 3000 blocks east: its waystone is broken"), "the Map doesn't read 'Willowmere, 3000 blocks east: its waystone is broken': " + lines);
			String made = VillageHalls.madeUpName(halls.get(1)).getString();
			helper.assertFalse(made.isBlank(), "setup: the made-up name is blank");
			helper.assertTrue(lines.contains(made + ", 3000 blocks north: its waystone is broken"), "the blank-named village isn't read as '" + made
				+ ", 3000 blocks north: its waystone is broken': " + lines);
			helper.assertFalse(lines.stream().anyMatch(l -> l.startsWith(",") || l.startsWith(" ")), "a line on the Map starts without a name: " + lines);
		} finally {
			forget.run();
		}
		helper.succeed();
	}
}
