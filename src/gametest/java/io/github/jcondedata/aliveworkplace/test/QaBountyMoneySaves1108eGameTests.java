package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * QA (lane-e-1108-1913), B1060 from its spec on the plain path (no CobbleDollars) and across saves: every bounty line
 * names the money paid, which without CobbleDollars is emeralds ("18 emeralds", never "18 emeralds emeralds"), and
 * chronicle lines saved by 0.151.8 under the old keys keep their old wording when the hall is loaded by this jar, while
 * the new {@code .money} lines read the same after a save and a load (docs/wiki/bounties.md, B1060).
 */
public class QaBountyMoneySaves1108eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		entity.setRank(VillageRanks.Rank.TOWN);
		return entity;
	}

	/** The hall as a save and a load of the world would bring it back. */
	private static VillageHallBlockEntity reload(GameTestHelper helper, VillageHallBlockEntity entity) {
		ServerLevel level = helper.getLevel();
		BlockPos at = entity.getBlockPos();
		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		level.removeBlockEntity(at);
		BlockEntity back = BlockEntity.loadStatic(at, level.getBlockState(at), tag, level.registryAccess());
		helper.assertTrue(back instanceof VillageHallBlockEntity, "the hall didn't load: " + back);
		level.setBlockEntity(back);
		return (VillageHallBlockEntity) back;
	}

	private static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	/**
	 * Without CobbleDollars a Town's bounty (18 emeralds) is posted as "... Reward: 18 emeralds." and its Wanted Poster
	 * reads "Reward: 18 emeralds": the money named once, in emeralds.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaBountyMoneyPlain"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaBountyMoneyPlain")
	public void withoutCobbleDollarsThePostingAndPosterSayEmeraldsOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(!Money.cobbleDollars(), "this suite runs without CobbleDollars");
		int radius = VillageHalls.RADIUS;
		boolean enabled = Bounties.ENABLED;
		VillageHalls.RADIUS = 16;
		Bounties.ENABLED = true;
		Bounties.forget(level);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Bounties.ENABLED = enabled;
			Bounties.forget(level);
		});
		hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Component> heard = new ArrayList<>();
		ServerPlayer me = RealmGameTests.listener(helper, heard, "qa-money");
		me.teleportTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 0.5);
		Bounties.Bounty b = Bounties.post(level, hall, AliveWorkplace.id("fleetfoot"), 0, 0, hall.offset(160, 0, 0));
		helper.assertTrue(b != null && b.reward == 18, "12 emeralds times the Town factor: " + (b == null ? "none" : b.reward));
		List<String> said = heard.stream().map(Component::getString).filter(s -> s.contains("posts a bounty")).toList();
		helper.assertTrue(said.size() == 1 && said.get(0).endsWith(" Reward: 18 emeralds."), "the posting: " + said);
		ItemStack poster = Bounties.poster(level, b);
		ItemLore lore = poster.get(DataComponents.LORE);
		List<String> lines = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
		helper.assertTrue(lines.contains("Reward: 18 emeralds"), "the poster: " + lines);
		helper.assertTrue(Bounties.lines(level, b).stream().map(Component::getString).filter(s -> s.startsWith("Reward:")).toList()
			.equals(List.of("Reward: 18 emeralds")), "the journal's lines: " + Bounties.lines(level, b).stream().map(Component::getString).toList());
		helper.succeed();
	}

	/**
	 * A chronicle written by 0.151.8: the old keys with the plain number ("... was paid %s emeralds."). Loaded by this jar
	 * each reads as it did: "was paid 18 emeralds", "in battle and was paid 20 emeralds", "caught ... and was paid 20
	 * emeralds".
	 */
	//$ gametest_ticks_batch AREA '20' '"qaBountyMoneyOldLines"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaBountyMoneyOldLines")
	public void chronicleLinesSavedBeforeB1060ReadAsTheyDid(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = entity.getBlockPos();
		Component who = Component.literal("qa-hunter");
		Chronicle.record(level, hall, Chronicle.Kind.BOUNTY, Component.translatable("chronicle.aliveworkplace.bounty.claimed", who,
			Component.literal("Grimwald the Red"), 18));
		Chronicle.record(level, hall, Chronicle.Kind.BOUNTY, Component.translatable("chronicle.aliveworkplace.bounty.rogue_beaten", who,
			Component.literal("Rogue Tauros"), 20));
		Chronicle.record(level, hall, Chronicle.Kind.BOUNTY, Component.translatable("chronicle.aliveworkplace.bounty.rogue_caught", who,
			Component.literal("Rogue Eevee"), 20));
		VillageHallBlockEntity back = reload(helper, entity);
		List<String> want = List.of("qa-hunter handed in the insignia of Grimwald the Red and was paid 18 emeralds.",
			"qa-hunter beat Rogue Tauros in battle and was paid 20 emeralds.", "qa-hunter caught Rogue Eevee and was paid 20 emeralds.");
		helper.assertTrue(chronicle(back).containsAll(want), "the old lines after the load: " + chronicle(back));
		helper.succeed();
	}

	/**
	 * The new lines (the money as its own part, B1060) are saved and loaded with the hall and read the same: "was paid 18
	 * emeralds." and "was paid 1 emerald." for a single one.
	 */
	//$ gametest_ticks_batch AREA '20' '"qaBountyMoneyNewLines"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaBountyMoneyNewLines")
	public void chronicleLinesWithTheMoneyKeepItOverASave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = entity.getBlockPos();
		Component who = Component.literal("qa-hunter");
		Chronicle.record(level, hall, Chronicle.Kind.BOUNTY, Component.translatable("chronicle.aliveworkplace.bounty.claimed.money", who,
			Component.literal("Grimwald the Red"), Money.describe(18L * Money.DOLLARS_PER_EMERALD, 18)));
		Chronicle.record(level, hall, Chronicle.Kind.BOUNTY, Component.translatable("chronicle.aliveworkplace.bounty.rogue_caught.money", who,
			Component.literal("Rogue Eevee"), Money.describe(Money.DOLLARS_PER_EMERALD, 1)));
		List<String> before = chronicle(entity);
		VillageHallBlockEntity back = reload(helper, entity);
		List<String> want = List.of("qa-hunter handed in the insignia of Grimwald the Red and was paid 18 emeralds.",
			"qa-hunter caught Rogue Eevee and was paid 1 emerald.");
		helper.assertTrue(before.containsAll(want), "the new lines: " + before);
		helper.assertTrue(chronicle(back).equals(before), "after the load: " + chronicle(back) + ", before: " + before);
		helper.succeed();
	}
}
