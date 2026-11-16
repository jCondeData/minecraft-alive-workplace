package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.with;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane E, 2026-11-16), ROADMAP 47.4 (the Storehouse board in Copperline, Give and Give all) with Create: a builder
 * whose supply chest by his bench is a Create item vault. Give all hands what the player carries into the vault, counted
 * once; a full vault takes nothing and the player keeps every item; a vault with room for a few takes those few and the
 * rest stays in the pack, nothing lost or made. The plain suite's board tests only use vanilla chests. From the spec.
 */
public class QaStorehouseGiveCreate1116eCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos STOREHOUSE = new BlockPos(12, 2, 12);

	private record Setup(Villager builder, BlockPos vault, ServerPlayer player) {
	}

	private static Setup setup(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		BlockPos vault = helper.absolutePos(BENCH.north());
		set(level, vault, with(block("create:item_vault").defaultBlockState(), "axis", "x"));
		Villager v = helper.spawn(EntityType.VILLAGER, BENCH.south());
		v.setNoAi(true);
		Builders.employ(level, v, helper.absolutePos(BENCH));
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BUILDER));
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		return new Setup(v, vault, player);
	}

	private static void ask(Villager v, Item item, int count) {
		Requests.post(v, new ItemStack(item), count, item.getDescription(), new Requests.ExactItem(item));
	}

	private static long inVault(GameTestHelper helper, BlockPos vault, Item item) {
		return SupplyContainers.count(helper.getLevel(), List.of(vault), item);
	}

	/** Puts {@code count} of {@code item} in the vault through the mod's own store code; how many went in. */
	private static int put(GameTestHelper helper, BlockPos vault, Item item, int count) {
		ItemStack rest = SupplyContainers.insert(helper.getLevel(), List.of(vault), new ItemStack(item, count));
		return count - rest.getCount();
	}

	/** Fills the vault with cobblestone until it takes no more; how many went in. */
	private static int fillUp(GameTestHelper helper, BlockPos vault) {
		int total = 0;
		for (int i = 0; i < 100; i++) {
			int in = put(helper, vault, Items.COBBLESTONE, 64);
			total += in;
			if (in < 64) {
				break;
			}
		}
		return total;
	}

	private static String giveAllName(ChoiceMenu board) {
		ItemStack all = board.icon(StorehouseBoard.GIVE_ALL_SLOT);
		return all.isEmpty() ? "(none)" : all.getHoverName().getString();
	}

	/** A builder short of 16 logs, his supply a Create vault: Give all hands the player's 10 logs into it, the dirt stays. */
	//$ gametest_ticks_batch AREA '200' '"qa_e1116_give_vault"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_e1116_give_vault")
	public void giveAllPutsTheLogsIntoTheBuildersCreateVault(GameTestHelper helper) {
		Setup s = setup(helper);
		helper.runAfterDelay(20, () -> {
			Village.RADIUS = 48;
			try {
				ask(s.builder(), Items.OAK_LOG, 16);
				s.player().getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 10));
				s.player().getInventory().setItem(1, new ItemStack(Items.DIRT, 5));
				ChoiceMenu board = StorehouseBoard.boardForTest(s.player(), helper.absolutePos(STOREHOUSE));
				helper.assertTrue(giveAllName(board).equals("Give all 10"), "Give all reads '" + giveAllName(board) + "'");
				board.press(StorehouseBoard.GIVE_ALL_SLOT, s.player());
				helper.assertTrue(inVault(helper, s.vault(), Items.OAK_LOG) == 10, "the vault holds " + inVault(helper, s.vault(), Items.OAK_LOG)
					+ " logs, not the 10 given");
				helper.assertTrue(s.player().getInventory().countItem(Items.OAK_LOG) == 0, "logs left in the pack: "
					+ s.player().getInventory().countItem(Items.OAK_LOG));
				helper.assertTrue(s.player().getInventory().countItem(Items.DIRT) == 5, "the dirt nobody asked for was taken");
			} finally {
				Village.RADIUS = 0;
			}
			helper.succeed();
		});
	}

	/** The builder's vault is full: Give all gives nothing, the player keeps all 10 logs, the vault is as it was. */
	//$ gametest_ticks_batch AREA '200' '"qa_e1116_give_vault_full"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_e1116_give_vault_full")
	public void aFullCreateVaultTakesNothingAndThePlayerKeepsEveryLog(GameTestHelper helper) {
		Setup s = setup(helper);
		int stone = fillUp(helper, s.vault());
		helper.assertTrue(stone >= 64, "(control) the vault took only " + stone + " cobblestone");
		helper.runAfterDelay(20, () -> {
			Village.RADIUS = 48;
			try {
				ask(s.builder(), Items.OAK_LOG, 16);
				s.player().getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 10));
				ChoiceMenu board = StorehouseBoard.boardForTest(s.player(), helper.absolutePos(STOREHOUSE));
				board.press(StorehouseBoard.GIVE_ALL_SLOT, s.player());
				helper.assertTrue(s.player().getInventory().countItem(Items.OAK_LOG) == 10, "the player has "
					+ s.player().getInventory().countItem(Items.OAK_LOG) + " of 10 logs after giving to a full vault");
				helper.assertTrue(inVault(helper, s.vault(), Items.OAK_LOG) == 0, "logs in a full vault: " + inVault(helper, s.vault(), Items.OAK_LOG));
				helper.assertTrue(inVault(helper, s.vault(), Items.COBBLESTONE) == stone, "the vault's cobblestone: "
					+ inVault(helper, s.vault(), Items.COBBLESTONE) + " of " + stone);
			} finally {
				Village.RADIUS = 0;
			}
			helper.succeed();
		});
	}

	/**
	 * The vault has room for only a few logs (its last slot part full of logs): Give all puts in what fits and the rest
	 * stays in the pack; logs in the pack plus logs in the vault are what there were.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_e1116_give_vault_room"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_e1116_give_vault_room")
	public void aCreateVaultWithRoomForAFewTakesThoseAndNothingIsLost(GameTestHelper helper) {
		Setup s = setup(helper);
		int stone = fillUp(helper, s.vault());
		// Make room for exactly a few logs: take one stack of stone out, then put 61 logs in its place.
		int out = SupplyContainers.extract(helper.getLevel(), List.of(s.vault()), Items.COBBLESTONE, 64);
		helper.assertTrue(out == 64, "(control) took " + out + " stone out");
		int logs = put(helper, s.vault(), Items.OAK_LOG, 61);
		helper.assertTrue(logs == 61, "(control) the vault took " + logs + " of 61 logs");
		helper.runAfterDelay(20, () -> {
			Village.RADIUS = 48;
			try {
				ask(s.builder(), Items.OAK_LOG, 16);
				s.player().getInventory().setItem(0, new ItemStack(Items.OAK_LOG, 10));
				ChoiceMenu board = StorehouseBoard.boardForTest(s.player(), helper.absolutePos(STOREHOUSE));
				board.press(StorehouseBoard.GIVE_ALL_SLOT, s.player());
				long inPack = s.player().getInventory().countItem(Items.OAK_LOG);
				long inside = inVault(helper, s.vault(), Items.OAK_LOG);
				helper.assertTrue(inPack + inside == 71, "logs: " + inPack + " in the pack + " + inside + " in the vault, not 71");
				helper.assertTrue(inside == 64 && inPack == 7, "the vault's room for 3 logs: " + inside + " in it, " + inPack + " kept");
			} finally {
				Village.RADIUS = 0;
			}
			helper.succeed();
		});
	}
}
