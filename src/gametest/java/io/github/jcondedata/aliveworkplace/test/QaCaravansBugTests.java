package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static io.github.jcondedata.aliveworkplace.test.QaCaravansGameTests.*;

/**
 * QA (lane-c-1025-0352) for 33.6 and 33.7: bug tests that fail on main today. NOT registered in fabric.mod.json, so
 * main stays green; the builder registers each with its fix. Setup helpers are QaCaravansGameTests'.
 * <ul>
 * <li>{@code qaEmeraldsDoNotVanishWhenTheSellersTreasuryIsFull}: with the selling village's treasury at its cap, the
 * buyer's treasury still pays for the goods and the money reaches nobody (2.57 emeralds gone).</li>
 * <li>{@code qaKillingThePartyLosesNoGoodsAndDropsNothing}: a pack llama killed by damage that goes through
 * invulnerability (/kill, the void) drops a chest and leather: free items out of "only a sight".</li>
 * </ul>
 */
public class QaCaravansBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/**
	 * The seller's treasury already full: the spec says B's treasury pays A's, so the emeralds B pays must reach A, or B
	 * must not pay them. With A at its cap the total of both treasuries must not fall.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCaravanSellerFull"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCaravanSellerFull")
	public void qaEmeraldsDoNotVanishWhenTheSellersTreasuryIsFull(GameTestHelper helper) {
		Two two = build(helper, false);
		int[] start = new int[1];
		helper.runAfterDelay(2, () -> {
			int cap = io.github.jcondedata.aliveworkplace.hall.Treasury.cap(two.hallA()) * 100;
			open(helper, two, cap, 1000, List.of());
			market(two, two.a(), List.of(TIMBER), List.of(), Map.of(TIMBER, 100));
			market(two, two.b(), List.of(), List.of(TIMBER), Map.of(TIMBER, 130));
			two.ours().setItem(0, new ItemStack(Items.OAK_LOG, 48));
			start[0] = two.money();
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.OAK_LOG) == 16, "loaded: " + two.ours().countItem(Items.OAK_LOG));
		});
		helper.runAfterDelay(20, () -> {
			arrive(two, two.b());
			helper.assertTrue(two.money() >= start[0], "emeralds vanished: B paid " + (1000 - two.hallB().treasury()) + " cents, A's full treasury took "
				+ (two.money() - start[0] + (1000 - two.hallB().treasury())) + "; the two treasuries went from " + start[0] + " to " + two.money());
			helper.succeed();
		});
	}

	/**
	 * Killed as /kill does (damage that goes through invulnerability), the party loses no goods and leaves nothing for a
	 * player to pick up (no chest, carpet, lead or leather: "nothing can be lost or stolen"); the rest of the party is
	 * taken away and the caravan still arrives whole.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCaravanSightKilled"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCaravanSightKilled")
	public void qaKillingThePartyLosesNoGoodsAndDropsNothing(GameTestHelper helper) {
		Two two = build(helper, true);
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		BlockPos stand = helper.absolutePos(new BlockPos(10, 2, 3));
		player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
		helper.runAfterDelay(2, () -> {
			open(helper, two, 0, 0, List.of(new Caravans.Want(Items.BREAD, 16)));
			two.ours().setItem(0, new ItemStack(Items.BREAD, 40));
			Caravans.round(two.level(), two.a(), null);
			CaravanSights.Party party = CaravanSights.party(two.level(), two.a());
			helper.assertTrue(party != null, "no party");
			for (Llama llama : party.llamas()) {
				// what the llama's chest holds, as the save writes it ("Items")
				net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
				llama.saveWithoutId(saved);
				helper.assertTrue(saved.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(), "the llama's chest holds goods to be stolen: " + saved.get("Items"));
			}
			party.llamas().get(0).kill();
			party.carter().kill();
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == null, "the party is still out");
			helper.assertTrue(tagged(helper).stream().noneMatch(Entity::isAlive), "the rest of the party stayed: " + tagged(helper));
			helper.assertTrue(dropped(helper).isEmpty(), "the killed party left things to pick up: " + dropped(helper));
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 24 && two.road().size() == 1, "the goods on the road: " + two.road());
			arrive(two, two.b());
			helper.assertTrue(two.theirs().countItem(Items.BREAD) == 16, "the bread should arrive whole: " + two.theirs().countItem(Items.BREAD));
			helper.succeed();
		});
	}

}
