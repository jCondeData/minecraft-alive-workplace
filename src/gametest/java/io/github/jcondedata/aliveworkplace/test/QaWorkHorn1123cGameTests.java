package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.WorkHorn;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * QA from the spec (docs/wiki/work-horn.md, lane-c-1123-0653): what {@code WorkHornGameTests} doesn't check. The
 * recipe in any order; the village's answer counts the grown villagers in the hall's circle only (one: "1 villager"),
 * and a rush lasts 5 minutes; a villager outside the circle neither rushes nor is worn out; one who walks in during the
 * rush rushes but isn't worn out after; a friend of the owner and an operator may call a rush through the item, a
 * stranger's refusal names the owner and the village; let go early, the horn doesn't sound a rush; {@code workHorns}
 * off: nobody is worn out; every sentence the horn shows resolves, with no raw placeholders.
 */
public class QaWorkHorn1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final Pattern RAW = Pattern.compile("%[sd0-9$]|\\{\\}|aliveworkplace\\.");
	private static final List<String> BRITISH = List.of("colour", "armour", "defence", "grey", "cancelled", "favour", "honour", "jeweller");

	/** Shapeless: a goat horn, a gold ingot and an emerald make one Work Horn in any order; without the emerald, nothing. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theRecipeIsShapelessAndNeedsAllThree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ItemStack one = craft(level, new ItemStack(Items.GOAT_HORN), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.EMERALD));
		ItemStack two = craft(level, new ItemStack(Items.EMERALD), new ItemStack(Items.GOAT_HORN), new ItemStack(Items.GOLD_INGOT));
		helper.assertTrue(one.is(ModItems.WORK_HORN) && one.getCount() == 1, "goat horn, gold, emerald: " + one);
		helper.assertTrue(two.is(ModItems.WORK_HORN), "another order: " + two);
		ItemStack none = craft(level, new ItemStack(Items.GOAT_HORN), new ItemStack(Items.GOLD_INGOT));
		helper.assertTrue(!none.is(ModItems.WORK_HORN), "without the emerald: " + none);
		helper.succeed();
	}

	/**
	 * Blown through the item: the answer names the village and counts the two grown villagers in the circle (not the
	 * child, not the one outside), the rush lasts 6000 ticks; the outsider isn't rushed or worn out. With one grown
	 * villager the next day: "1 villager".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHornRange"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHornRange")
	public void theAnswerCountsOnlyTheGrownInTheCircle(GameTestHelper helper) {
		Village v = village(helper);
		boolean moods = Moods.ENABLED;
		Leftovers.after(helper, () -> Moods.ENABLED = moods);
		Villager a = grown(helper, new BlockPos(4, 2, 4));
		Villager b = grown(helper, new BlockPos(16, 2, 16));
		Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 4));
		child.setAge(-24000);
		child.setNoAi(true);
		ServerLevel level = helper.getLevel();
		Villager outsider = EntityType.VILLAGER.spawn(level, helper.absolutePos(HALL).offset(26, 0, 0), MobSpawnType.COMMAND);
		helper.assertTrue(outsider != null, "no outsider");
		outsider.setNoAi(true);
		outsider.setVillagerData(outsider.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		Leftovers.after(helper, outsider::discard);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = v.hall();
			Moods.ENABLED = false;
			helper.assertTrue(!VillageHalls.contains(hall.getBlockPos(), outsider.blockPosition()), "the outsider is in the circle");
			helper.assertTrue(WorkHorn.RUSH_TICKS == 6000, "a rush isn't 5 minutes: " + WorkHorn.RUSH_TICKS);
			long now = level.getGameTime();
			blowWithItem(v.player(), level);
			helper.assertTrue(hall.rushUntil() == now + 6000, "rush until " + hall.rushUntil() + " from " + now);
			String said = v.said().isEmpty() ? "" : v.said().get(v.said().size() - 1);
			String village = VillageHalls.name(level, hall.getBlockPos()).getString();
			helper.assertTrue(said.equals(village + " answers the horn: 2 villagers rush to work!"), "said: '" + said + "' in " + village);
			forget();
			helper.assertTrue(part(a) < 1f && part(b) < 1f, "the villagers in the circle don't rush");
			helper.assertTrue(part(outsider) == 1f, "the outsider rushes: " + Pace.of(outsider));
			helper.assertTrue(!ModAttachments.WORN_OUT.has(outsider) && !ModAttachments.WORN_OUT.has(child), "the outsider or the child answered");
			// Tomorrow, with one grown villager: the singular.
			b.discard();
			level.setDayTime(level.getDayTime() + VillageNeeds.DAY);
			WorkHorn.Result one = WorkHorn.blow(level, v.player(), v.player().blockPosition());
			helper.assertTrue(one.outcome() == WorkHorn.Outcome.RUSH && one.message().getString().contains(" answers the horn: 1 villager ")
				&& !one.message().getString().contains("villagers"), "one: " + one.message().getString());
			helper.succeed();
		});
	}

	/** A villager who comes into the village during the rush rushes too, but only those there when it sounded are worn out (Known limits). */
	//$ gametest_ticks_batch AREA '200' '"qaHornLate"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHornLate")
	public void aLatecomerRushesButIsNotWornOut(GameTestHelper helper) {
		Village v = village(helper);
		long rush = WorkHorn.RUSH_TICKS;
		boolean moods = Moods.ENABLED;
		Leftovers.after(helper, () -> {
			WorkHorn.RUSH_TICKS = rush;
			Moods.ENABLED = moods;
		});
		Villager early = grown(helper, new BlockPos(4, 2, 4));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			Moods.ENABLED = false;
			WorkHorn.RUSH_TICKS = 40;
			blowWithItem(v.player(), level);
			Villager late = grown(helper, new BlockPos(16, 2, 4));
			forget();
			helper.assertTrue(part(late) < 1f && part(early) < 1f, "the latecomer doesn't rush: " + Pace.of(late));
			helper.runAfterDelay(50, () -> {
				forget();
				helper.assertTrue(part(late) == 1f && part(early) == 1f, "still rushing after the end");
				helper.assertTrue(WorkHorn.wornOut(level, early), "the one there isn't worn out");
				helper.assertTrue(!WorkHorn.wornOut(level, late) && !ModAttachments.WORN_OUT.has(late), "the latecomer is worn out");
				helper.succeed();
			});
		});
	}

	/**
	 * In an owned village a friend of the owner and an operator may call a rush through the item; a stranger is told
	 * "Only <owner> and their friends can call a rush in <village>." with both names filled in.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHornFriend1123c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHornFriend1123c")
	public void aFriendAndAnOperatorMayCallARush(GameTestHelper helper) {
		Village v = village(helper);
		Villager farmer = grown(helper, new BlockPos(4, 2, 4));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = v.hall();
			UUID owner = UUID.randomUUID();
			hall.setOwner(owner, "Jesse");
			helper.assertTrue(!v.player().hasPermissions(2), "the test player is an operator already");
			// A stranger first: refused, and the sentence names the owner and the village.
			blowWithItem(v.player(), level);
			String village = VillageHalls.name(level, hall.getBlockPos()).getString();
			String refused = v.said().isEmpty() ? "" : v.said().get(v.said().size() - 1);
			helper.assertTrue(refused.equals("Only Jesse and their friends can call a rush in " + village + "."), "refused: '" + refused + "'");
			helper.assertTrue(hall.hornDay() == -1 && !ModAttachments.WORN_OUT.has(farmer), "a stranger's rush");
			// The owner's friend.
			Friends friends = Friends.get(level.getServer());
			friends.add(owner, v.player().getUUID(), v.player().getGameProfile().getName());
			Leftovers.after(helper, () -> friends.remove(owner, v.player().getUUID()));
			v.player().getCooldowns().removeCooldown(ModItems.WORK_HORN);
			blowWithItem(v.player(), level);
			helper.assertTrue(hall.hornDay() == Chronicle.day(level) && ModAttachments.WORN_OUT.has(farmer), "the friend called no rush: " + v.said());
			friends.remove(owner, v.player().getUUID());
			// An operator, the next day.
			level.setDayTime(level.getDayTime() + VillageNeeds.DAY);
			ModAttachments.WORN_OUT.remove(farmer);
			v.op()[0] = 2;
			helper.assertTrue(v.player().hasPermissions(2), "not an operator");
			v.player().getCooldowns().removeCooldown(ModItems.WORK_HORN);
			blowWithItem(v.player(), level);
			helper.assertTrue(hall.hornDay() == Chronicle.day(level) && ModAttachments.WORN_OUT.has(farmer), "the operator called no rush: " + v.said());
			helper.succeed();
		});
	}

	/** Held up and let go before it sounds (under a second and a half), the horn calls no rush, and it can be blown after. */
	//$ gametest_ticks_batch AREA '100' '"qaHornEarly"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHornEarly")
	public void letGoEarlyItDoesNotSound(GameTestHelper helper) {
		Village v = village(helper);
		Villager farmer = grown(helper, new BlockPos(4, 2, 4));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = v.hall();
			ServerPlayer player = v.player();
			ItemStack horn = new ItemStack(ModItems.WORK_HORN);
			player.setItemInHand(InteractionHand.MAIN_HAND, horn);
			horn.use(level, player, InteractionHand.MAIN_HAND);
			helper.assertTrue(player.isUsingItem(), "not held up");
			player.releaseUsingItem();
			helper.assertTrue(!player.isUsingItem() && hall.hornDay() == -1 && hall.rushUntil() == 0 && !ModAttachments.WORN_OUT.has(farmer),
				"a rush when let go early");
			helper.assertTrue(!player.getCooldowns().isOnCooldown(ModItems.WORK_HORN), "a cooldown when it didn't sound");
			helper.succeed();
		});
	}

	/** {@code workHorns} off: the horn sounds (the item still works) and nobody is worn out; the hall keeps no day. */
	//$ gametest_ticks_batch AREA '100' '"qaHornOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHornOff")
	public void offNobodyIsWornOut(GameTestHelper helper) {
		Village v = village(helper);
		boolean enabled = WorkHorn.ENABLED;
		Leftovers.after(helper, () -> WorkHorn.ENABLED = enabled);
		Villager farmer = grown(helper, new BlockPos(4, 2, 4));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			VillageHallBlockEntity hall = v.hall();
			WorkHorn.ENABLED = false;
			blowWithItem(v.player(), level);
			helper.assertTrue(!ModAttachments.WORN_OUT.has(farmer) && hall.hornDay() == -1 && hall.rushUntil() == 0, "off: worn out or a rush");
			helper.assertTrue(v.said().stream().noneMatch(s -> s.contains("rush to work")), "off: an answer " + v.said());
			// Switched back on the same day, the horn still has its blow (off didn't use the day).
			WorkHorn.ENABLED = true;
			v.player().getCooldowns().removeCooldown(ModItems.WORK_HORN);
			helper.assertTrue(WorkHorn.blow(level, v.player(), v.player().blockPosition()).outcome() == WorkHorn.Outcome.RUSH, "the day was used while off");
			helper.succeed();
		});
	}

	/** Every sentence the horn shows a player reads whole: names, tooltip, answers and refusals, with no raw keys or placeholders, in American English. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everySentenceReadsWhole(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> keys = List.of("item.aliveworkplace.work_horn", "tooltip.aliveworkplace.work_horn", "tooltip.aliveworkplace.work_horn.once",
			"message.aliveworkplace.work_horn.rush", "message.aliveworkplace.work_horn.villagers", "message.aliveworkplace.work_horn.villagers.one",
			"message.aliveworkplace.work_horn.used", "message.aliveworkplace.work_horn.not_allowed", "message.aliveworkplace.work_horn.no_village",
			"mood.aliveworkplace.reason.worn_out", "pace.aliveworkplace.source.work_horn");
		for (String key : keys) {
			helper.assertTrue(lang.has(key), "missing: " + key);
		}
		List<String> shown = new ArrayList<>();
		shown.add(new ItemStack(ModItems.WORK_HORN).getHoverName().getString());
		List<Component> tooltip = new ArrayList<>();
		new ItemStack(ModItems.WORK_HORN).getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL).forEach(tooltip::add);
		tooltip.forEach(c -> shown.add(c.getString()));
		shown.add(Component.translatable("message.aliveworkplace.work_horn.rush", "Oakvale",
			Component.translatable("message.aliveworkplace.work_horn.villagers", 12)).getString());
		shown.add(Component.translatable("message.aliveworkplace.work_horn.not_allowed", "Jesse", "Oakvale").getString());
		shown.add(Component.translatable("message.aliveworkplace.work_horn.used").getString());
		shown.add(Component.translatable("message.aliveworkplace.work_horn.no_village").getString());
		helper.assertTrue(shown.contains("Oakvale answers the horn: 12 villagers rush to work!"), "the answer: " + shown);
		helper.assertTrue(tooltip.size() >= 3 && shown.stream().anyMatch(s -> s.contains("50% faster") && s.contains("5 minutes")),
			"the tooltip: " + shown);
		for (String s : shown) {
			helper.assertTrue(!s.isBlank() && !RAW.matcher(s).find(), "raw: '" + s + "'");
			String lower = s.toLowerCase();
			helper.assertTrue(BRITISH.stream().noneMatch(lower::contains), "British spelling: '" + s + "'");
		}
		helper.succeed();
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	/** {@code op[0]}: the player's permission level, as ops.json would give it (the GameTest server keeps no ops list). */
	private record Village(VillageHallBlockEntity hall, ServerPlayer player, List<String> said, int[] op) {
	}

	/** A hall of radius 16 in the area's middle (nobody owns it) and a player by it whose action bar is kept, in the morning. */
	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = level.getDayTime();
		level.setDayTime((Chronicle.day(level) - 1) * VillageNeeds.DAY + 1000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		List<String> said = new ArrayList<>();
		int[] op = {0};
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa-horn-player"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				said.add(message.getString());
				super.displayClientMessage(message, overlay);
			}

			@Override
			public int getPermissionLevel() {
				return Math.max(super.getPermissionLevel(), op[0]);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		Leftovers.after(helper, () -> {
			level.getServer().getPlayerList().remove(player);
			level.removeBlock(hall, false);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			forget();
			VillageNeeds.forget();
		});
		return new Village((VillageHallBlockEntity) level.getBlockEntity(hall), player, said, op);
	}

	/** The player's own way: the horn held up until it sounds; what it said is kept from the result. */
	private static void blowWithItem(ServerPlayer player, ServerLevel level) {
		ItemStack horn = new ItemStack(ModItems.WORK_HORN);
		player.setItemInHand(InteractionHand.MAIN_HAND, horn);
		ModItems.WORK_HORN.finishUsingItem(horn, level, player);
	}

	private static Villager grown(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		villager.setVillagerXp(10);
		villager.setNoAi(true);
		return villager;
	}

	private static float part(Villager villager) {
		return Pace.of(villager).faster().stream().filter(p -> p.source() == Pace.WORK_HORN).map(Pace.Part::factor).findFirst().orElse(1f);
	}

	private static ItemStack craft(ServerLevel level, ItemStack... grid) {
		CraftingInput input = CraftingInput.of(grid.length, 1, List.of(grid));
		return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
			.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
	}

	private static void forget() {
		CivicEffects.forget();
		Moods.forget();
	}
}
