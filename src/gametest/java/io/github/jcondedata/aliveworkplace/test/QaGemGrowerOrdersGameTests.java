package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.gem.GemBeds;
import io.github.jcondedata.aliveworkplace.gem.GemGrowers;
import io.github.jcondedata.aliveworkplace.mixin.VillagerAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane d, 2026-10-25) of ROADMAP 28.11, the Gem Grower, from the spec. Not covered before: "only full clusters are
 * picked" against a large and a medium bud on the same budding block; "with nothing picked, every bed she has the
 * makings for" (a bed she can't plant doesn't hold up the others); "sneak-right-click to pick which beds she keeps"
 * through the real click and screen, behaviourally (she leaves the beds not picked, and keeps them again once unpicked);
 * her job, orders, beds and count surviving a save and reload; the Novice trade (amethyst shards) and no empty trade at
 * any level without Cobblemon. Random ticks are off in every test so no bud grows on its own.
 */
public class QaGemGrowerOrdersGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos BUDDING = new BlockPos(8, 2, 2);
	/** A test bed's block (as a data pack could add): crying obsidian, planted with budding amethyst; 4 blocks from the amethyst bed. */
	private static final BlockPos OBSIDIAN = new BlockPos(8, 2, 6);
	private static final ResourceLocation TEST_BED = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "qa_planted");

	private static void noRandomTicks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameRules.IntegerValue ticking = level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING);
		int was = ticking.get();
		ticking.set(0, level.getServer());
		Leftovers.after(helper, () -> ticking.set(was, level.getServer()));
	}

	private static void withTestBed(GameTestHelper helper) {
		List<GemBeds.Bed> shipped = GemBeds.beds();
		List<GemBeds.Bed> beds = new ArrayList<>(shipped);
		beds.add(new GemBeds.Bed(TEST_BED, Optional.of(ResourceLocation.withDefaultNamespace("budding_amethyst")),
			new GemBeds.Touch(ResourceLocation.withDefaultNamespace("crying_obsidian"), null),
			List.of(ResourceLocation.withDefaultNamespace("amethyst_cluster")), ResourceLocation.withDefaultNamespace("amethyst_cluster"),
			Map.of(), 1, Set.of(), 50));
		GemBeds.set(beds);
		Leftovers.after(helper, () -> GemBeds.set(shipped));
		helper.setBlock(OBSIDIAN, Blocks.CRYING_OBSIDIAN);
	}

	private static void cluster(GameTestHelper helper, BlockPos pos, Direction facing) {
		helper.setBlock(pos, Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, facing));
	}

	private static String said(GameTestHelper helper, Villager grower) {
		WorkerStatus.Entry e = WorkerStatus.get(grower, helper.getLevel().getGameTime());
		return (e == null ? "no status" : e.line().getString()) + " at " + helper.relativePos(grower.blockPosition()) + ", beds "
			+ GemGrowers.beds(grower) + ", orders " + GemGrowers.orders(grower);
	}

	/**
	 * A budding block with a large bud on top, a medium bud on its south side and a full cluster on its east side: she
	 * picks the full cluster only (one pick, its shards in her chest) and, 400 ticks later, the buds and the budding block
	 * are all still there.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaGemBuds"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaGemBuds")
	public void qaLargeAndMediumBudsAreLeftOnlyTheFullClusterPicked(GameTestHelper helper) {
		Leftovers.clear(helper);
		noRandomTicks(helper);
		helper.setBlock(BUDDING, Blocks.BUDDING_AMETHYST);
		BlockPos large = BUDDING.above();
		BlockPos medium = BUDDING.south();
		BlockPos full = BUDDING.east();
		helper.setBlock(large, Blocks.LARGE_AMETHYST_BUD.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
		helper.setBlock(medium, Blocks.MEDIUM_AMETHYST_BUD.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.SOUTH));
		cluster(helper, full, Direction.EAST);
		Villager grower = GemGrowerGameTests.grower(helper);
		long[] picked = {-1};
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getBlockState(BUDDING).is(Blocks.BUDDING_AMETHYST), "the budding block was broken");
			helper.assertTrue(helper.getBlockState(large).is(Blocks.LARGE_AMETHYST_BUD), "the large bud was picked: " + helper.getBlockState(large));
			helper.assertTrue(helper.getBlockState(medium).is(Blocks.MEDIUM_AMETHYST_BUD), "the medium bud was picked: " + helper.getBlockState(medium));
			helper.assertFalse(helper.getBlockState(full).is(Blocks.AMETHYST_CLUSTER), "the full cluster wasn't picked; she says " + said(helper, grower));
			long now = helper.getTick();
			if (picked[0] < 0) {
				picked[0] = now;
			}
			helper.assertTrue(now >= picked[0] + 400, "waiting to see the buds are left");
			int picks = ModAttachments.GEMS_PICKED.getOrElse(grower, 0);
			helper.assertTrue(picks == 1, "picks counted: " + picks + " (only one cluster was full)");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.AMETHYST_SHARD) >= 4, "the cluster's shards aren't in her chest: " + chest.countItem(Items.AMETHYST_SHARD));
		});
	}

	/**
	 * Nothing picked, a bed she has no makings for (nothing to plant the crying obsidian with) and a ripe amethyst cluster:
	 * she picks the amethyst all the same, plants nothing, and says the other bed wants planting.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaGemNoMakings"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaGemNoMakings")
	public void qaABedWithoutItsMakingsDoesntHoldUpTheOthers(GameTestHelper helper) {
		Leftovers.clear(helper);
		noRandomTicks(helper);
		withTestBed(helper);
		helper.setBlock(BUDDING, Blocks.BUDDING_AMETHYST);
		cluster(helper, BUDDING.above(), Direction.UP);
		Villager grower = GemGrowerGameTests.grower(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(GemGrowers.orders(grower).isEmpty(), "she has orders: " + GemGrowers.orders(grower));
			helper.assertFalse(helper.getBlockState(BUDDING.above()).is(Blocks.AMETHYST_CLUSTER),
				"the amethyst wasn't picked while another bed lacked its makings; she says " + said(helper, grower));
			helper.assertTrue(helper.getBlockState(BUDDING).is(Blocks.BUDDING_AMETHYST), "the budding block was broken");
			for (Direction d : Direction.values()) {
				helper.assertFalse(helper.getBlockState(OBSIDIAN.relative(d)).is(Blocks.BUDDING_AMETHYST), "planted with nothing in her chests");
			}
			String status = said(helper, grower);
			helper.assertTrue(status.startsWith(Language.getInstance().getOrDefault("message.aliveworkplace.gem_grower.state.needs_planting")),
				"she doesn't say the other bed wants planting: " + status);
		});
	}

	/**
	 * Orders through the player's own click: a sneak-right-click with an empty hand opens her screen, which says she keeps
	 * every bed; picking the test bed there makes her plant it and leave the ripe amethyst (for 400 ticks after planting);
	 * unpicking it puts her back to every bed, and she picks the amethyst.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaGemOrders"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaGemOrders")
	public void qaPickedBedsAreTheOnlyOnesSheKeepsUntilUnpicked(GameTestHelper helper) {
		Leftovers.clear(helper);
		noRandomTicks(helper);
		withTestBed(helper);
		helper.setBlock(BUDDING, Blocks.BUDDING_AMETHYST);
		BlockPos amethyst = BUDDING.above();
		cluster(helper, amethyst, Direction.UP);
		Villager grower = GemGrowerGameTests.grower(helper, new ItemStack(Items.BUDDING_AMETHYST));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		InteractionResult r = QaLuxuryJobsGameTests.click(player, grower, true);
		helper.assertTrue(r.consumesAction(), "a sneak-right-click with an empty hand did nothing: " + r);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "her orders screen didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		String lore = String.valueOf(menu.icon(4).get(net.minecraft.core.component.DataComponents.LORE));
		helper.assertTrue(lore.contains("screen.aliveworkplace.gem_grower.every"), "with nothing picked the screen says: " + lore);
		int index = GemBeds.beds().indexOf(GemBeds.byName(TEST_BED));
		int slot = GemGrowers.FIRST_BED_SLOT + index;
		helper.assertTrue(index >= 0 && menu.icon(slot).is(Items.AMETHYST_CLUSTER), "no button for the test bed at " + slot + ": " + menu.icon(slot));
		menu.press(slot, player);
		helper.assertTrue(GemGrowers.orders(grower).equals(List.of(TEST_BED)), "orders after the press: " + GemGrowers.orders(grower));
		helper.assertTrue(String.valueOf(menu.icon(4).get(net.minecraft.core.component.DataComponents.LORE)).contains("screen.aliveworkplace.gem_grower.only"),
			"the screen doesn't say she keeps only the picked beds");
		long[] planted = {-1};
		boolean[] unpicked = {false};
		helper.onEachTick(() -> {
			if (unpicked[0]) {
				return;
			}
			helper.assertTrue(helper.getBlockState(amethyst).is(Blocks.AMETHYST_CLUSTER), "she picked the amethyst, a bed she wasn't asked to keep");
			helper.assertTrue(helper.getBlockState(BUDDING).is(Blocks.BUDDING_AMETHYST), "the budding block was broken");
			if (planted[0] < 0 && helper.getBlockState(OBSIDIAN.above()).is(Blocks.BUDDING_AMETHYST)) {
				planted[0] = helper.getTick();
			}
			if (planted[0] >= 0 && helper.getTick() >= planted[0] + 400) {
				menu.press(slot, player);
				helper.assertTrue(GemGrowers.orders(grower).isEmpty(), "unpicking left orders: " + GemGrowers.orders(grower));
				unpicked[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(planted[0] >= 0, "the picked bed wasn't planted; she says " + said(helper, grower));
			helper.assertTrue(unpicked[0], "not yet unpicked");
			helper.assertFalse(helper.getBlockState(amethyst).is(Blocks.AMETHYST_CLUSTER),
				"with nothing picked again she left the amethyst; she says " + said(helper, grower));
			helper.assertTrue(helper.getBlockState(BUDDING).is(Blocks.BUDDING_AMETHYST), "the budding block was broken");
		});
	}

	/**
	 * Save and reload: after her first pick she is saved and loaded back (the old one gone). The copy is still a Gem
	 * Grower with the same orders, beds and pick count, and carries on: a new ripe cluster is picked and counted.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaGemReload"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaGemReload")
	public void qaHerOrdersBedsAndCountSurviveASaveAndReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		noRandomTicks(helper);
		helper.setBlock(BUDDING, Blocks.BUDDING_AMETHYST);
		cluster(helper, BUDDING.above(), Direction.UP);
		Villager grower = GemGrowerGameTests.grower(helper);
		ResourceLocation amethystBed = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "amethyst");
		GemGrowers.toggle(grower, amethystBed);
		Villager[] copy = {null};
		helper.onEachTick(() -> {
			if (copy[0] != null || ModAttachments.GEMS_PICKED.getOrElse(grower, 0) < 1) {
				return;
			}
			List<BlockPos> beds = GemGrowers.beds(grower);
			CompoundTag saved = grower.saveWithoutId(new CompoundTag());
			grower.discard();
			Villager loaded = EntityType.VILLAGER.create(helper.getLevel());
			loaded.load(saved);
			loaded.setUUID(UUID.randomUUID());
			helper.getLevel().addFreshEntity(loaded);
			helper.assertTrue(loaded.getVillagerData().getProfession() == ModVillagers.GEM_GROWER, "reloaded as " + loaded.getVillagerData().getProfession());
			helper.assertTrue(GemGrowers.orders(loaded).equals(List.of(amethystBed)), "orders after a reload: " + GemGrowers.orders(loaded));
			helper.assertTrue(GemGrowers.beds(loaded).equals(beds), "beds after a reload: " + GemGrowers.beds(loaded) + ", before " + beds);
			helper.assertTrue(ModAttachments.GEMS_PICKED.getOrElse(loaded, 0) == 1, "count after a reload: " + ModAttachments.GEMS_PICKED.getOrElse(loaded, 0));
			copy[0] = loaded;
			cluster(helper, BUDDING.east(), Direction.EAST);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(copy[0] != null, "the first cluster wasn't picked; she says " + said(helper, grower));
			helper.assertFalse(helper.getBlockState(BUDDING.east()).is(Blocks.AMETHYST_CLUSTER), "the reloaded grower didn't pick the new cluster; she says "
				+ said(helper, copy[0]));
			helper.assertTrue(ModAttachments.GEMS_PICKED.getOrElse(copy[0], 0) == 2, "count: " + ModAttachments.GEMS_PICKED.getOrElse(copy[0], 0));
			helper.assertTrue(helper.getBlockState(BUDDING).is(Blocks.BUDDING_AMETHYST), "the budding block was broken");
		});
	}

	/**
	 * Trades without Cobblemon: a Novice sells amethyst shards for emeralds; levelled up to Master, no trade is ever empty
	 * (the tumblestone and Type Gem trades, Cobblemon's items, are left out rather than sold as nothing).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void qaANoviceSellsShardsAndNoTradeIsEmptyWithoutCobblemon(GameTestHelper helper) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.GEM_GROWER).setLevel(1));
		v.setVillagerXp(1);
		helper.assertTrue(v.getOffers().stream().anyMatch(o -> o.getResult().is(Items.AMETHYST_SHARD) && o.getCostA().is(Items.EMERALD)),
			"a Novice Gem Grower doesn't sell amethyst shards: " + offers(v));
		for (int level = 2; level <= 5; level++) {
			((VillagerAccessor) v).aliveworkplace$increaseMerchantCareer();
			helper.assertTrue(v.getVillagerData().getLevel() == level, "level " + v.getVillagerData().getLevel());
		}
		for (MerchantOffer o : v.getOffers()) {
			helper.assertFalse(o.getResult().isEmpty() || o.getCostA().isEmpty(), "an empty trade: " + offers(v));
		}
		helper.succeed();
	}

	private static List<String> offers(Villager v) {
		return v.getOffers().stream().map(o -> o.getCostA() + " -> " + o.getResult()).toList();
	}
}
