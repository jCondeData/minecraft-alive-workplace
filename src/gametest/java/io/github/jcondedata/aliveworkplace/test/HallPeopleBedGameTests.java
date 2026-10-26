package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.hall.HallBeds;
import io.github.jcondedata.aliveworkplace.hall.HallJobs;
import io.github.jcondedata.aliveworkplace.hall.HallPeople;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * ROADMAP 40.4: Bed ▾ in the hall's People table. Every check goes through the player's own entry points: a right-click
 * on the hall, the sidebar's People, then the clicks the table sends (Bed ▾ on a row, a bed on its list). A free bed, a
 * swap, no beds at all, the chosen bed broken before night, a bed another villager claims first, a save and reload, and
 * the villager asleep in the chosen bed at night.
 */
public class HallPeopleBedGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final BlockPos NEAR = new BlockPos(10, 2, 10);
	private static final BlockPos FAR = new BlockPos(22, 2, 22);

	private static void village(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		HallJobs.forget();
		HallBeds.forget();
		int was = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 14;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = was);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	/** A red bed with its foot at {@code foot}, head to the south; returns its head (where its point of interest is), absolute. */
	private static BlockPos bed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	private static Villager villager(GameTestHelper helper, String name, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static ChoiceMenu openPeople(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.clickMenuButton(player, Section.PEOPLE.ordinal()), "the sidebar didn't take People");
		return menu;
	}

	private static int slotOf(GameTestHelper helper, ChoiceMenu menu, Villager villager) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			CompoundTag row = HallPeople.row(menu.icon(slot));
			if (row != null && row.getString("uuid").equals(villager.getStringUUID())) {
				return slot;
			}
		}
		helper.fail("no row for " + villager.getName().getString());
		return -1;
	}

	private static CompoundTag row(GameTestHelper helper, ChoiceMenu menu, Villager villager) {
		return HallPeople.row(menu.icon(slotOf(helper, menu, villager)));
	}

	/** Bed ▾ on {@code villager}'s row, as the client sends it; returns the list's lines now on their row. */
	private static List<ItemStack> bedDropdown(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, Villager villager) {
		helper.assertTrue(menu.clickMenuButton(player, HallBeds.OPEN + slotOf(helper, menu, villager)), "Bed ▾ wasn't taken");
		return lines(helper, menu, villager);
	}

	private static List<ItemStack> lines(GameTestHelper helper, ChoiceMenu menu, Villager villager) {
		return HallBeds.lines(menu.icon(slotOf(helper, menu, villager)));
	}

	private static String state(ItemStack line) {
		return HallBeds.mark(line).getString("state");
	}

	private static String name(ItemStack line) {
		return line.getHoverName().getString();
	}

	private static List<String> names(List<ItemStack> lines) {
		return lines.stream().map(HallPeopleBedGameTests::name).toList();
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	/** The first line in {@code state}. */
	private static ItemStack first(GameTestHelper helper, List<ItemStack> lines, String state) {
		for (ItemStack line : lines) {
			if (state(line).equals(state)) {
				return line;
			}
		}
		helper.fail("no " + state + " line in " + names(lines));
		return ItemStack.EMPTY;
	}

	/** Clicks {@code line} on the open list, as the client sends it. */
	private static void pick(GameTestHelper helper, ServerPlayer player, ChoiceMenu menu, ItemStack line) {
		helper.assertTrue(menu.clickMenuButton(player, HallBeds.PICK + HallBeds.mark(line).getInt("id")), "the pick wasn't taken");
	}

	private static BlockPos home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos).orElse(null);
	}

	private static int housed(GameTestHelper helper) {
		return VillageNeeds.count(helper.getLevel(), helper.absolutePos(HALL)).housed();
	}

	/**
	 * A free bed: Dara has none; Bed ▾ lists the two free beds nearest the hall first, each with its distance and
	 * direction ("free · 6 blocks north-west"), and the note on wagon beds and cradles last. A click makes the nearer hers
	 * at once (her home memory, the bed's place taken), the census counts her housed, the list closes. Choosing the other
	 * frees the first.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallBedFree"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallBedFree")
	public void aFreeBed(GameTestHelper helper) {
		village(helper);
		BlockPos near = bed(helper, NEAR);
		BlockPos far = bed(helper, FAR);
		Villager dara = villager(helper, "Dara", new BlockPos(12, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			helper.assertTrue(!row(helper, menu, dara).getBoolean("beds_open") && housed(helper) == 0, "before Bed ▾");
			List<ItemStack> lines = bedDropdown(helper, player, menu, dara);
			helper.assertTrue(row(helper, menu, dara).getBoolean("beds_open") && !lines.isEmpty(), "Bed ▾ didn't open the list");
			helper.assertTrue(name(lines.get(0)).equals("A bed for Dara") && lore(lines.get(0)).contains("Dara has no bed now."),
				"the header: " + name(lines.get(0)) + " " + lore(lines.get(0)));
			helper.assertTrue(state(lines.get(1)).equals("free") && state(lines.get(2)).equals("free"), "the free beds aren't first: " + names(lines));
			helper.assertTrue(name(lines.get(1)).equals("free · 6 blocks north-west") && name(lines.get(2)).equals("free · 11 blocks south-east"),
				"where the beds are: " + names(lines));
			helper.assertTrue(lore(lines.get(1)).contains("Click: Dara's home from now on. They sleep here tonight."), "the hint: " + lore(lines.get(1)));
			ItemStack last = lines.get(lines.size() - 1);
			helper.assertTrue(state(last).equals("note") && name(last).equals("Wagon beds and cradles aren't listed"), "the note isn't last: " + names(lines));
			helper.assertTrue(lines.stream().noneMatch(l -> state(l).equals("plan")), "Plan a house shown with free beds");

			pick(helper, player, menu, lines.get(1));
			helper.assertTrue(near.equals(home(dara)) && near.equals(VillageNeeds.bed(helper.getLevel(), dara)), "her home: " + home(dara));
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(near) == 0, "the bed isn't claimed");
			helper.assertTrue(housed(helper) == 1, "the census doesn't count her housed: " + housed(helper));
			helper.assertTrue(!row(helper, menu, dara).getBoolean("beds_open"), "the list stayed open");
			helper.assertTrue(seen.stream().anyMatch(s -> s.startsWith("Dara sleeps at ") && s.endsWith(" from tonight.")), "no word: " + seen);

			// Now hers is on top as hers, and the far one is free: choosing it frees the first.
			lines = bedDropdown(helper, player, menu, dara);
			helper.assertTrue(lore(lines.get(0)).contains("Sleeps in: 6 blocks north-west"), "the header now: " + lore(lines.get(0)));
			helper.assertTrue(first(helper, lines, "current").getHoverName().getString().equals("6 blocks north-west · their bed now"), "her bed: " + names(lines));
			pick(helper, player, menu, first(helper, lines, "free"));
			helper.assertTrue(far.equals(home(dara)), "she didn't move: " + home(dara));
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(near) == 1 && helper.getLevel().getPoiManager().getFreeTickets(far) == 0,
				"the old bed wasn't freed");
			helper.assertTrue(housed(helper) == 1, "housed twice: " + housed(helper));
			helper.succeed();
		});
	}

	/** A swap: Dara and Jem each have a bed; Jem's says "swap with Jem", and a click moves both, nobody's bed freed. */
	//$ gametest_ticks_batch AREA '200' '"hallBedSwap"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallBedSwap")
	public void aSwap(GameTestHelper helper) {
		village(helper);
		BlockPos near = bed(helper, NEAR);
		BlockPos far = bed(helper, FAR);
		Villager dara = villager(helper, "Dara", new BlockPos(12, 2, 15));
		Villager jem = villager(helper, "Jem", new BlockPos(18, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(CampBeds.claim(helper.getLevel(), dara, near) && CampBeds.claim(helper.getLevel(), jem, far), "the beds weren't taken");
			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = bedDropdown(helper, player, menu, dara);
			ItemStack swap = first(helper, lines, "swap");
			helper.assertTrue(name(swap).equals("11 blocks south-east · swap with Jem") && lore(swap).contains("Click: Dara and Jem swap beds."),
				"the swap line: " + name(swap) + " " + lore(swap));
			helper.assertTrue(lines.stream().anyMatch(l -> state(l).equals("plan")), "no free bed, but no Plan a house: " + names(lines));
			pick(helper, player, menu, swap);
			helper.assertTrue(far.equals(home(dara)) && near.equals(home(jem)), "not swapped: Dara " + home(dara) + ", Jem " + home(jem));
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(near) == 0 && helper.getLevel().getPoiManager().getFreeTickets(far) == 0,
				"a bed was freed in the swap");
			helper.assertTrue(housed(helper) == 2 && seen.contains("Dara and Jem swapped beds."), "after the swap: " + housed(helper) + " " + seen);
			helper.succeed();
		});
	}

	/** No beds at all: the list says so, offers "Short of beds? Plan a house ›", and the note. Nothing to click changes a bed. */
	//$ gametest_ticks_batch AREA '200' '"hallBedNone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallBedNone")
	public void noBedsAtAll(GameTestHelper helper) {
		village(helper);
		Villager dara = villager(helper, "Dara", new BlockPos(12, 2, 15));
		ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = bedDropdown(helper, player, menu, dara);
			helper.assertTrue(names(lines).equals(List.of("A bed for Dara", "No beds in the village", "Short of beds? Plan a house ›",
				"Wagon beds and cradles aren't listed")), "the list: " + names(lines));
			helper.assertTrue(lore(first(helper, lines, "plan")).contains("Opens the Buildings section, where the village's next house is planned."), "the plan hint");
			// A number past the list does nothing.
			helper.assertTrue(menu.clickMenuButton(player, HallBeds.PICK + 3), "the click wasn't taken");
			helper.assertTrue(home(dara) == null, "a bed out of nothing");
			// Bed ▾ again closes it.
			helper.assertTrue(bedDropdown(helper, player, menu, dara).isEmpty() && !row(helper, menu, dara).getBoolean("beds_open"), "Bed ▾ again didn't close it");
			helper.succeed();
		});
	}

	/**
	 * The chosen bed broken before night (B362): from that moment she's housed nowhere, in the census and on her list, and
	 * at night she doesn't sleep in it.
	 */
	//$ gametest_ticks_batch AREA '200' '"hallBedBroken"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallBedBroken")
	public void theChosenBedBrokenBeforeNight(GameTestHelper helper) {
		village(helper);
		BlockPos near = bed(helper, NEAR);
		Villager dara = villager(helper, "Dara", new BlockPos(12, 2, 15));
		ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			pick(helper, player, menu, first(helper, bedDropdown(helper, player, menu, dara), "free"));
			helper.assertTrue(near.equals(home(dara)) && housed(helper) == 1, "not hers");
			helper.getLevel().destroyBlock(near, false);
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(VillageNeeds.bed(helper.getLevel(), dara) == null && housed(helper) == 0, "a broken bed still houses her");
				List<ItemStack> lines = bedDropdown(helper, player, menu, dara);
				helper.assertTrue(lore(lines.get(0)).contains("Dara has no bed now.") && lines.stream().anyMatch(l -> state(l).equals("none")),
					"the list after: " + names(lines) + " " + lore(lines.get(0)));
				helper.assertTrue(!dara.isSleeping(), "asleep in a broken bed");
				helper.succeed();
			});
		});
	}

	/** A bed another villager claims first: the click changes nothing, says so, and the list shows it as Kip's now. */
	//$ gametest_ticks_batch AREA '200' '"hallBedClaimedFirst"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallBedClaimedFirst")
	public void aBedAnotherVillagerClaimsFirst(GameTestHelper helper) {
		village(helper);
		BlockPos near = bed(helper, NEAR);
		Villager dara = villager(helper, "Dara", new BlockPos(12, 2, 15));
		Villager kip = villager(helper, "Kip", new BlockPos(18, 2, 15));
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listeningPlayer(helper, seen);
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			ItemStack free = first(helper, bedDropdown(helper, player, menu, dara), "free");
			helper.assertTrue(CampBeds.claim(helper.getLevel(), kip, near), "Kip couldn't claim it"); // as vanilla's own claim would
			pick(helper, player, menu, free);
			helper.assertTrue(home(dara) == null && near.equals(home(kip)), "Dara " + home(dara) + ", Kip " + home(kip));
			helper.assertTrue(seen.contains("Someone took that bed first."), "no word: " + seen);
			List<ItemStack> lines = lines(helper, menu, dara);
			helper.assertTrue(row(helper, menu, dara).getBoolean("beds_open"), "the list closed");
			ItemStack taken = first(helper, lines, "taken");
			helper.assertTrue(name(taken).equals("6 blocks north-west · Kip's bed") && lore(taken).contains("Dara has no bed to give Kip in return."),
				"Kip's bed: " + name(taken) + " " + lore(taken));
			// Clicking it now (Dara has no bed to swap) changes nothing.
			pick(helper, player, menu, taken);
			helper.assertTrue(home(dara) == null && near.equals(home(kip)), "taken from Kip");
			helper.succeed();
		});
	}

	/** Saved and reloaded: the chosen bed is still her home. A new screen opens with the list closed. Every new sentence has its text. */
	//$ gametest_ticks_batch AREA '200' '"hallBedSave"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "hallBedSave")
	public void theChoiceSurvivesASaveAndReload(GameTestHelper helper) {
		village(helper);
		BlockPos far = bed(helper, FAR);
		Villager dara = villager(helper, "Dara", new BlockPos(12, 2, 15));
		ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			pick(helper, player, menu, first(helper, bedDropdown(helper, player, menu, dara), "free"));
			Villager copy = EntityType.VILLAGER.create(helper.getLevel());
			copy.load(dara.saveWithoutId(new CompoundTag()));
			helper.assertTrue(far.equals(home(copy)), "loaded with home " + home(copy));
			copy.discard();
			bedDropdown(helper, player, menu, dara);
			ChoiceMenu again = openPeople(helper, player);
			helper.assertTrue(!row(helper, again, dara).getBoolean("beds_open"), "a new screen opened with the list open");
			Language lang = Language.getInstance();
			for (String key : List.of("screen.aliveworkplace.hall.people.bed_hint", "screen.aliveworkplace.hall.people.bed_open", "screen.aliveworkplace.hall.bed_list.title",
				"screen.aliveworkplace.hall.bed_list.now", "screen.aliveworkplace.hall.bed_list.now_none", "screen.aliveworkplace.hall.bed_list.in_building",
				"screen.aliveworkplace.hall.bed_list.free_in", "screen.aliveworkplace.hall.bed_list.free_at", "screen.aliveworkplace.hall.bed_list.free_hint",
				"screen.aliveworkplace.hall.bed_list.current", "screen.aliveworkplace.hall.bed_list.swap", "screen.aliveworkplace.hall.bed_list.swap_hint",
				"screen.aliveworkplace.hall.bed_list.taken_by", "screen.aliveworkplace.hall.bed_list.no_swap", "screen.aliveworkplace.hall.bed_list.taken",
				"screen.aliveworkplace.hall.bed_list.taken_hint", "screen.aliveworkplace.hall.bed_list.none", "screen.aliveworkplace.hall.bed_list.none_hint",
				"screen.aliveworkplace.hall.bed_list.plan", "screen.aliveworkplace.hall.bed_list.plan_hint", "screen.aliveworkplace.hall.bed_list.note",
				"screen.aliveworkplace.hall.bed_list.note_more", "message.aliveworkplace.hall.beds.moved", "message.aliveworkplace.hall.beds.swapped",
				"message.aliveworkplace.hall.beds.taken_first", "message.aliveworkplace.hall.beds.changed", "message.aliveworkplace.hall.beds.gone")) {
				helper.assertTrue(lang.has(key), "no text for " + key);
			}
			helper.succeed();
		});
	}

	/** The villager sleeps in the chosen bed that night: chosen from the table at dusk, she walks there and lies down in it. */
	//$ gametest_ticks_batch AREA '600' '"hallBedSleep"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "hallBedSleep")
	public void theySleepThereAtNight(GameTestHelper helper) {
		village(helper);
		// The bed by her is left free, so she can't just take the nearest herself: the chosen one is across the village.
		BlockPos far = bed(helper, NEAR);
		bed(helper, new BlockPos(20, 2, 10));
		Villager dara = villager(helper, "Dara", new BlockPos(19, 2, 13));
		ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
		long was = helper.getLevel().getDayTime();
		Leftovers.after(helper, () -> helper.getLevel().setDayTime(was));
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			List<ItemStack> lines = bedDropdown(helper, player, menu, dara);
			ItemStack chosen = lines.stream().filter(l -> state(l).equals("free") && name(l).contains("north-west")).findFirst().orElse(ItemStack.EMPTY);
			helper.assertTrue(!chosen.isEmpty(), "the north-west bed isn't free: " + names(lines));
			pick(helper, player, menu, chosen);
			helper.assertTrue(far.equals(home(dara)), "not hers: " + home(dara));
			player.closeContainer();
			long day = helper.getLevel().getDayTime();
			helper.getLevel().setDayTime(day - Math.floorMod(day, 24000L) + 13000L); // dusk
			dara.setNoAi(false);
			helper.succeedWhen(() -> {
				helper.assertTrue(dara.isSleeping() && dara.getSleepingPos().filter(far::equals).isPresent(),
					"not asleep in her bed: sleeping " + dara.isSleeping() + " at " + dara.getSleepingPos() + ", home " + home(dara) + ", at " + dara.blockPosition());
			});
		});
	}
}
