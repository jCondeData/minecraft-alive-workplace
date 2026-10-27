package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.habitat.HabitatKeepers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * QA lane E (2026-10-27), saves: the Habitat Keeper's marked lure spots ({@code lure_spots}) were the one piece of saved
 * villager data no test saved and reloaded (tools/modtest/inventory.py). The README: "Field Markers for her lure spots",
 * "up to three". A spot a player marks is hers after a save, in the order marked (so a fourth still gives up the oldest),
 * and the reloaded keeper sets her snack out on it.
 */
public class QaLureSpotsSaveCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	/** HabitatKeeperCompatTests' marked block; the snack goes on top of it. */
	private static final BlockPos MARKED = new BlockPos(9, 1, 9);
	private static final BlockPos SPOT = MARKED.above();
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** Three spots marked, a save: the same three, oldest first, and a fourth marked after the load gives up the oldest. */
	//$ gametest_ticks_batch AREA '100' '"qa_lure_save_order"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_lure_save_order")
	public void threeMarkedSpotsKeepTheirOrderOverASave(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		BlockPos a = helper.absolutePos(new BlockPos(6, 2, 9));
		BlockPos b = helper.absolutePos(new BlockPos(8, 2, 9));
		BlockPos c = helper.absolutePos(new BlockPos(10, 2, 9));
		BlockPos d = helper.absolutePos(new BlockPos(12, 2, 9));
		HabitatKeepers.addSpot(keeper, a);
		HabitatKeepers.addSpot(keeper, b);
		HabitatKeepers.addSpot(keeper, c);
		Villager loaded = reload(helper, keeper);
		helper.assertTrue(HabitatKeepers.isKeeper(loaded), "she isn't a Habitat Keeper after the save");
		helper.assertTrue(HabitatKeepers.spots(loaded).equals(List.of(a, b, c)),
			"her spots after a save: " + HabitatKeepers.spots(loaded) + ", saved " + List.of(a, b, c));
		HabitatKeepers.addSpot(loaded, d);
		helper.assertTrue(HabitatKeepers.spots(loaded).equals(List.of(b, c, d)),
			"a fourth spot after the save kept " + HabitatKeepers.spots(loaded) + ", expected the oldest (" + a + ") given up");
		// A second save after that change keeps the change, not the first save's list.
		Villager again = reload(helper, loaded);
		helper.assertTrue(HabitatKeepers.spots(again).equals(List.of(b, c, d)), "after a second save: " + HabitatKeepers.spots(again));
		loaded.discard();
		again.discard();
		helper.succeed();
	}

	/** A spot handed to her with a Field Marker, then a save and load (as a chunk does): the loaded keeper sets her snack out on it. */
	//$ gametest_ticks_batch AREA '2400' '"qa_lure_save_snack"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_lure_save_snack")
	public void theLoadedKeeperSetsHerSnackOnTheMarkedSpot(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper, new ItemStack(cobblemon("poke_snack"), 1));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		player.setItemInHand(InteractionHand.MAIN_HAND, HabitatKeeperCompatTests.marker(helper, MARKED));
		helper.assertTrue(HabitatKeepers.markSpot(player, keeper, player.getMainHandItem()).consumesAction(), "handing her the marker did nothing");
		helper.assertTrue(!snackAt(helper), "a snack was out before the save");
		Villager loaded = reload(helper, keeper);
		keeper.discard();
		helper.assertTrue(helper.getLevel().addFreshEntity(loaded), "the loaded keeper couldn't be added to the world");
		helper.assertTrue(HabitatKeepers.spots(loaded).equals(List.of(helper.absolutePos(SPOT))), "her spots after the save: " + HabitatKeepers.spots(loaded));
		helper.succeedWhen(() -> {
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(snackAt(helper), "the loaded keeper set no snack on her marked spot " + helper.absolutePos(SPOT)
				+ " (spots " + HabitatKeepers.spots(loaded) + ", snacks in the chest " + chest.countItem(cobblemon("poke_snack")) + ")");
			helper.assertTrue(chest.countItem(cobblemon("poke_snack")) == 0, "the snack set out didn't come from her chest");
		});
	}

	/** An empty list saved (every spot gone): she has none after the load, and falls back to grass (not an old list). */
	//$ gametest_ticks_batch AREA '100' '"qa_lure_save_empty"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_lure_save_empty")
	public void noSpotsSavedLoadsAsNoSpots(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setNoAi(true);
		HabitatKeepers.addSpot(keeper, helper.absolutePos(SPOT));
		ModAttachments.LURE_SPOTS.set(keeper, List.of());
		Villager loaded = reload(helper, keeper);
		helper.assertTrue(HabitatKeepers.spots(loaded).isEmpty(), "spots came back after an empty list was saved: " + HabitatKeepers.spots(loaded));
		loaded.discard();
		helper.succeed();
	}

	private static net.minecraft.world.item.Item cobblemon(String path) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("cobblemon", path));
	}

	private static boolean snackAt(GameTestHelper helper) {
		return BuiltInRegistries.BLOCK.getKey(helper.getBlockState(SPOT).getBlock()).equals(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_snack"));
	}

	/** Saves the villager and loads a new one from the save, as a chunk does (not added to the world). */
	private static Villager reload(GameTestHelper helper, Villager villager) {
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(villager.save(tag), "the villager wasn't saved");
		Entity loaded = EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
		helper.assertTrue(loaded instanceof Villager && loaded != villager, "the save didn't load as a new villager: " + loaded);
		return (Villager) loaded;
	}
}
