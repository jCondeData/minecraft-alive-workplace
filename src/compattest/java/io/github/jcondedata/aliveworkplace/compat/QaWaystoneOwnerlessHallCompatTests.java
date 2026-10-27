package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-d-1027-1909) for ROADMAP 41.2, the nightly survivor at VillageWaystones.java:350 ({@code viewer == null}
 * made {@code !=}): the network is "the villages of the hall's owner (the viewer's own when the hall has none)". On a
 * hall nobody owns, a player's Map lists the player's own villages with a waystone; with no viewer at all (a server-side
 * look) the hall's own stone is all there is, and nothing fails.
 */
public class QaWaystoneOwnerlessHallCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final String NETWORK = "aliveworkplace_village_waystones";
	private static final String LINE = "screen.aliveworkplace.hall.waystone";

	private static void keepStones(ServerLevel level, List<BlockPos> halls, List<BlockPos> stones) {
		CompoundTag tag = VillageWaystones.Data.get(level).save(new CompoundTag(), level.registryAccess());
		long[] oldHalls = tag.getLongArray("halls");
		long[] oldStones = tag.getLongArray("stones");
		long[] h = new long[oldHalls.length + halls.size()];
		long[] s = new long[oldStones.length + stones.size()];
		System.arraycopy(oldHalls, 0, h, 0, oldHalls.length);
		System.arraycopy(oldStones, 0, s, 0, oldStones.length);
		for (int i = 0; i < halls.size(); i++) {
			h[oldHalls.length + i] = halls.get(i).asLong();
			s[oldStones.length + i] = stones.get(i).asLong();
		}
		tag.putLongArray("halls", h);
		tag.putLongArray("stones", s);
		level.getDataStorage().set(NETWORK, VillageWaystones.Data.load(tag, level.registryAccess()));
	}

	private static void forget(ServerLevel level, List<BlockPos> halls) {
		RealmData realms = RealmData.get(level.getServer());
		for (BlockPos hall : halls) {
			VillageWaystones.Data.get(level).forget(hall);
			realms.forgetKnown(GlobalPos.of(level.dimension(), hall));
		}
	}

	/**
	 * Home's hall has no owner; the player owns Farbrook, 2,000 blocks east (not loaded), whose stone the network file
	 * holds. The player's Map on Home lists both, Home first; with no viewer, only Home's own stone, without failing.
	 */
	//$ gametest_batch AREA '"qaWaystoneOwnerless"'
	@GameTest(template = AREA, batch = "qaWaystoneOwnerless")
	public void aHallNobodyOwnsShowsTheViewersOwnNetwork(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		UUID owner = player.getUUID();
		BlockPos home = helper.absolutePos(new BlockPos(15, 2, 15));
		BlockPos homeStone = home.offset(4, 0, 0);
		BlockPos farStone = home.offset(2000, 0, 0);
		BlockPos far = farStone.offset(2, 0, 2);
		List<BlockPos> halls = List.of(home, far);
		forget(level, halls);
		try {
			level.setBlock(home, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
			if (!(level.getBlockEntity(home) instanceof VillageHallBlockEntity hall)) {
				throw new GameTestAssertException("no hall at " + home);
			}
			hall.setCustomName(Component.literal("Home"));
			helper.assertTrue(hall.owner() == null, "setup: Home's hall has an owner: " + hall.owner());
			helper.assertFalse(level.isLoaded(far), "setup: Farbrook's hall is loaded");
			RealmData.get(level.getServer()).putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), far), Component.literal("Farbrook"),
				Optional.of(owner), "Jesse", "hamlet", 0, 0, level.getGameTime()));
			keepStones(level, halls, List.of(homeStone, farStone));

			List<String> lines = WaystoneQaCompatTests.texts(WaystoneQaCompatTests.mapLore(player, home), LINE);
			helper.assertTrue(lines.size() == 3 && lines.get(0).equals("Waystone network (2):") && lines.get(1).startsWith("Home, this village")
				&& lines.get(2).startsWith("Farbrook, 2000 blocks east"),
				"the player's Map on a hall nobody owns doesn't list the player's own villages: " + lines);

			List<VillageWaystones.Village> alone = VillageWaystones.network(level, home, null);
			helper.assertTrue(alone.size() == 1 && alone.get(0).here() && alone.get(0).stone().equals(homeStone),
				"with no viewer, an ownerless hall's network: " + alone);
		} finally {
			forget(level, halls);
			level.setBlock(home, Blocks.AIR.defaultBlockState(), 3);
		}
		helper.succeed();
	}
}
