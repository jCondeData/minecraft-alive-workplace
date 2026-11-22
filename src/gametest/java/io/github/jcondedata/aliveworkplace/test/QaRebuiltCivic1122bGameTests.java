package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.grave.GraveBlockEntity;
import io.github.jcondedata.aliveworkplace.grave.Graveyards;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (2026-11-22, lane b) of ROADMAP 51.6 (Chapel, Graveyard I-II, Clock Tower and Post Office rebuilt), written from
 * their blind briefs ({@code tools/blueprints/briefs/<family>.md}). The drawings' slots, paths and job blocks as placed
 * are already checked by {@code QaRebuiltCivic1121cGameTests} and {@code QaGraveyard1122cGameTests}; this class covers the
 * build side a player meets:
 * <ul>
 * <li>a real Builder builds the Chapel, Graveyard I, the Post Office and (without Create) the Clock Tower from exactly
 * the Material List the player reads on the held blueprint, across a server restart mid-walls, ending with nothing
 * skipped and every block as drawn within the brief's time and stack budget, and the finished build does what its brief
 * pins (the Seer's floor at the Chapel's middle and no job or bed in it; every grave slot of the built yard free, one
 * Undertaker's site with a chest by it and a candle; one Mailbox a player makes a Postman at with paper, and a bed; one
 * cartography table as a job site);</li>
 * <li>the live upgrade Graveyard I to II over a standing yard with the Undertaker's brewing stand claimed, the player's
 * revival items in the chest and three graves already resting in its slots: all kept, every slot still free;</li>
 * <li>breakage: the Post Office one material short waits, then finishes once the player brings it; its Mailbox broken
 * mid-build is put back;</li>
 * <li>every name reads in American English.</li>
 * </ul>
 */
public class QaRebuiltCivic1122bGameTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	/** QaDecorations1104fGameTests.exact's supply chests. */
	private static final List<BlockPos> CHESTS = List.of(new BlockPos(2, 2, 4), new BlockPos(4, 2, 4), new BlockPos(1, 2, 5), new BlockPos(4, 2, 1));
	private static final BlockPos[] BARRELS = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4)};
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);

	// ------------------------------------------------------------------------------------------------------ helpers

	private static String n(StarterBlueprints.Entry entry) {
		return entry.id().getPath();
	}

	private static String where(BuildSite live) {
		return live == null ? "done" : live.stage() + " " + live.status() + ", placed " + live.placed() + ", skipped " + live.skipped() + ", missing " + live.missing();
	}

	/** Sets the builder rules a player's game uses in these tests (delay 2, no help, no free materials) and restores them. */
	private static void rules(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
	}

	/** Keeps the clock in working hours (builders go home at night). */
	private static void holdDay(GameTestHelper helper) {
		if (helper.getLevel().getDayTime() % 24000 > 11000) {
			helper.setDayTime(2000);
		}
	}

	/** A server restart: the build sites saved and loaded, the builder saved, removed and loaded back. */
	private static Villager restart(GameTestHelper helper, Villager old) {
		ServerLevel level = helper.getLevel();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		return (Villager) loaded;
	}

	private static List<BlockPos> inBox(ServerLevel level, BlockPos origin, net.minecraft.core.Vec3i size, java.util.function.Predicate<BlockState> what) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
			if (what.test(level.getBlockState(p))) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	private static boolean bedHead(BlockState s) {
		return s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD;
	}

	/** Whether {@code s} is the job site of some villager job (vanilla's or the mod's): not a bed, bell, lightning rod or hive alone. */
	private static boolean jobSite(BlockState s) {
		return PoiTypes.forState(s).map(holder -> BuiltInRegistries.VILLAGER_PROFESSION.stream()
			.anyMatch(job -> job != VillagerProfession.NONE && job.heldJobSite().test(holder))).orElse(false);
	}

	/** Job sites of any job in the finished build (a bell, a meeting point, allowed where {@code bellOk}). */
	private static List<String> jobSites(ServerLevel level, BlockPos origin, StarterBlueprints.Entry e, boolean bellOk) {
		List<String> out = new ArrayList<>();
		for (BlockPos p : inBox(level, origin, e.size(), s -> (jobSite(s) || !bellOk && s.is(Blocks.BELL)) && !(bellOk && s.is(Blocks.BELL)))) {
			out.add(BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath() + " " + p.subtract(origin).toShortString());
		}
		return out;
	}

	/** Stacks a list takes, each item rounded up to whole stacks (how the briefs count their budgets). */
	private static int stacks(Map<Item, Integer> list) {
		int out = 0;
		for (Map.Entry<Item, Integer> e : list.entrySet()) {
			int max = e.getKey().getDefaultMaxStackSize();
			out += (e.getValue() + max - 1) / max;
		}
		return out;
	}

	/** A grave slot as the Graveyard brief defines it: air or replaceable, no water, a sturdy non-grave top under it, room above. */
	private static boolean slotFree(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		BlockState under = level.getBlockState(p.below());
		BlockState above = level.getBlockState(p.above());
		return (s.isAir() || s.canBeReplaced()) && level.getFluidState(p).isEmpty() && under.isFaceSturdy(level, p.below(), Direction.UP)
			&& !under.is(ModBlocks.GRAVE) && (above.isAir() || above.canBeReplaced());
	}

	/** The grave slots of the Graveyard standing at {@code origin} (13 x 13), as the village finds them. */
	private static List<BlockPos> slots(ServerLevel level, BlockPos origin) {
		BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(12, 15, 12));
		return Graveyards.resting(level, origin.offset(6, 0, 6), false).slots().stream().filter(box::isInside).toList();
	}

	/**
	 * A real Builder builds {@code entry} at {@code origin} from exactly the Material List read off the held blueprint
	 * (QaDecorations1104fGameTests.exact: bench, four supply chests), with a server restart once its walls are going up
	 * (STRUCTURE, at least 20 placed). The list must fit {@code maxStacks} stacks. It must end DONE with every block as
	 * drawn and 0 skipped within {@code budget} ticks of the test's start; then {@code after} runs on the finished build's
	 * placement (inside succeedWhen, so it may run more than once).
	 */
	private static void exactAcrossARestart(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin, int budget, int maxStacks,
		Consumer<BlueprintData.Placement> after) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		rules(helper);
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, entry, origin);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(job.placement()));
		int listStacks = stacks(job.list());
		helper.assertTrue(listStacks <= maxStacks, n(entry) + "'s Material List takes " + listStacks + " stacks, its brief allows " + maxStacks);
		UUID id = job.site().id();
		Villager[] who = {job.villager()};
		boolean[] restarted = {false};
		int[] skipped = {0};
		long[] doneAt = {-1};
		helper.onEachTick(() -> {
			holdDay(helper);
			BuildSite live = BuildSiteManager.get(level).get(id);
			if (live == null) {
				if (doneAt[0] < 0) {
					doneAt[0] = helper.getTick();
					LOGGER.info("qa civic 1122b: {} done at tick {} (budget {}, {} stacks)", n(entry), doneAt[0], budget, listStacks);
				}
				return;
			}
			skipped[0] = live.skipped();
			if (!restarted[0] && live.stage() == BuildPlan.Stage.STRUCTURE && live.placed() >= 20) {
				restarted[0] = true;
				who[0] = restart(helper, who[0]);
				helper.assertTrue(BuildSiteManager.get(level).get(id) != null, n(entry) + ": the site didn't come back from the restart");
				LOGGER.info("qa civic 1122b: restarted {} at {}", n(entry), where(BuildSiteManager.get(level).get(id)));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0], n(entry) + " never reached its walls (STRUCTURE with 20 placed) to restart in");
			BuildSite live = BuildSiteManager.get(level).get(id);
			helper.assertTrue(live == null, n(entry) + " from exactly its Material List is still building after the restart: " + where(live)
				+ ", builder at " + helper.relativePos(who[0].blockPosition()) + (who[0].isAlive() ? "" : " (dead)"));
			List<BlockPos> unfinished = job.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), n(entry) + ": " + unfinished.size() + " block(s) not as drawn after the build, e.g. "
				+ unfinished.stream().limit(5).map(p -> p.subtract(job.placement().origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(skipped[0] == 0, n(entry) + ": the build skipped " + skipped[0] + " block(s)");
			helper.assertTrue(doneAt[0] >= 0 && doneAt[0] <= budget, n(entry) + " took " + doneAt[0] + " ticks, its brief gives " + budget);
			after.accept(job.placement());
		});
	}

	// ----------------------------------------------------------------- 1. built from exactly its list, restarted

	/**
	 * chapel.md: built from exactly its list (at most 108 stacks) across a restart, in the brief's plot (big_area at
	 * (8, 2, 5)), within the brief's 40,000 ticks. Built: no job site of any job (a bell may stand), no bed; at or right
	 * beside the middle of its declared box at y = 1 there is a floor cell the Seer can appear on (a sturdy top under it,
	 * feet and head with no collision and no fluid) under the roof (some block above it), so the guest and the weddings
	 * are inside, not on the lawn.
	 */
	//$ gametest_ticks_batch BIG '42000' '"qaCiv1122b_chapel"'
	@GameTest(template = BIG, timeoutTicks = 42000, batch = "qaCiv1122b_chapel")
	public void qaTheChapelIsBuiltFromExactlyItsListAcrossARestartWithTheSeersFloorAtItsMiddle(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry e = StarterBlueprints.CHAPEL;
		exactAcrossARestart(helper, e, new BlockPos(8, 2, 5), 40000, 108, placement -> {
			BlockPos o = placement.origin();
			List<String> sites = jobSites(level, o, e, true);
			helper.assertTrue(sites.isEmpty(), "the built Chapel holds villager job sites: " + sites);
			helper.assertTrue(inBox(level, o, e.size(), QaRebuiltCivic1122bGameTests::bedHead).isEmpty(), "the built Chapel has a bed");
			BlockPos middle = o.offset(e.size().getX() / 2, 1, e.size().getZ() / 2);
			List<String> near = new ArrayList<>();
			boolean found = false;
			for (BlockPos p : BlockPos.betweenClosed(middle.offset(-1, -1, -1), middle.offset(1, 1, 1))) {
				BlockState under = level.getBlockState(p.below());
				BlockState feet = level.getBlockState(p);
				BlockState head = level.getBlockState(p.above());
				boolean stand = under.isFaceSturdy(level, p.below(), Direction.UP) && feet.getCollisionShape(level, p).isEmpty() && feet.getFluidState().isEmpty()
					&& head.getCollisionShape(level, p.above()).isEmpty() && head.getFluidState().isEmpty();
				boolean roofed = false;
				for (int y = p.getY() + 2; y < o.getY() + e.size().getY(); y++) {
					if (!level.getBlockState(new BlockPos(p.getX(), y, p.getZ())).isAir()) {
						roofed = true;
						break;
					}
				}
				if (stand && roofed) {
					found = true;
				}
				if (p.getY() == middle.getY()) {
					near.add(p.subtract(o).toShortString() + "=" + BuiltInRegistries.BLOCK.getKey(feet.getBlock()).getPath() + "/" + BuiltInRegistries.BLOCK.getKey(under.getBlock()).getPath());
				}
			}
			helper.assertTrue(found, "no floor cell with head room under the roof at or beside the built Chapel's middle " + middle.subtract(o).toShortString()
				+ " (the Seer would appear outside): " + near);
		});
	}

	/**
	 * graveyard.md, Level I: built from exactly its list (at most 108 stacks) across a restart in the brief's plot
	 * (big_area at (9, 2, 9)) within 30,000 ticks. Built: at least 32 grave slots, every one free on the builder's own
	 * ground (a slot covered by a drawn floor, path or lamp is lost for good); exactly one Undertaker's job site (an
	 * Undertaker's Table or a brewing stand) and no other job block; a chest within 8 across and 4 up or down of it; at
	 * least one plain candle; no grave block of its own.
	 */
	//$ gametest_ticks_batch BIG '32000' '"qaCiv1122b_graveyard"'
	@GameTest(template = BIG, timeoutTicks = 32000, batch = "qaCiv1122b_graveyard")
	public void qaGraveyardIIsBuiltFromExactlyItsListAcrossARestartWithEverySlotFree(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry e = StarterBlueprints.GRAVEYARD;
		exactAcrossARestart(helper, e, new BlockPos(9, 2, 9), 30000, 108, placement -> {
			BlockPos o = placement.origin();
			List<BlockPos> slots = slots(level, o);
			helper.assertTrue(slots.size() >= 32, "the built Graveyard has " + slots.size() + " grave slots, the brief says at least 32");
			List<String> taken = slots.stream().filter(p -> !slotFree(level, p))
				.map(p -> p.subtract(o).toShortString() + "=" + level.getBlockState(p) + " on " + level.getBlockState(p.below())).toList();
			helper.assertTrue(taken.isEmpty(), "grave slots of the built Graveyard a grave can't be laid in: " + taken);
			List<BlockPos> sites = inBox(level, o, e.size(), s -> s.is(ModBlocks.UNDERTAKERS_TABLE) || s.is(Blocks.BREWING_STAND));
			helper.assertTrue(sites.size() == 1, "the built Graveyard has " + sites.size() + " Undertaker's sites, the brief says exactly 1");
			List<String> others = new ArrayList<>(jobSites(level, o, e, false));
			others.removeIf(s -> s.startsWith("brewing_stand ") || s.startsWith("undertakers_table "));
			helper.assertTrue(others.isEmpty(), "the built Graveyard has other job blocks: " + others);
			BlockPos site = sites.get(0);
			helper.assertTrue(PoiTypes.forState(level.getBlockState(site)).isPresent() && level.getPoiManager().getType(site).isPresent(),
				"the built Undertaker's site at " + site.subtract(o).toShortString() + " is no job site in the game's records");
			helper.assertTrue(inBox(level, o, e.size(), s -> s.getBlock() instanceof ChestBlock).stream()
					.anyMatch(c -> Math.abs(c.getX() - site.getX()) <= 8 && Math.abs(c.getZ() - site.getZ()) <= 8 && Math.abs(c.getY() - site.getY()) <= 4),
				"no built chest within 8 / 4 of the Undertaker's site (he could only ever post requests for revival items)");
			helper.assertTrue(!inBox(level, o, e.size(), s -> s.is(Blocks.CANDLE)).isEmpty(), "the built Graveyard has no plain candle");
			helper.assertTrue(inBox(level, o, e.size(), s -> s.is(ModBlocks.GRAVE)).isEmpty(), "the built Graveyard has a grave block of its own");
		});
	}

	/**
	 * post_office.md: built from exactly its list (at most 81 stacks) across a restart in the brief's plot (huge_area at
	 * (12, 2, 10)) within 12,000 ticks. Built: exactly one Mailbox and no other job block (no Postal Desk, no barrel); at
	 * least one bed, a home; a jobless villager standing on a free cell right beside the Mailbox, sneak-right-clicked by a
	 * survival player holding paper (the questbook's way), becomes a Postman working at that Mailbox.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qaCiv1122b_postOffice"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qaCiv1122b_postOffice")
	public void qaThePostOfficeIsBuiltFromExactlyItsListAcrossARestartAndPaperMakesAPostmanAtItsMailbox(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry e = StarterBlueprints.POST_OFFICE;
		Villager[] clerk = {null};
		BlockPos[] box = {null};
		List<Component> told = new ArrayList<>();
		exactAcrossARestart(helper, e, new BlockPos(12, 2, 10), 12000, 81, placement -> {
			BlockPos o = placement.origin();
			if (clerk[0] == null) {
				List<BlockPos> boxes = inBox(level, o, e.size(), s -> s.is(ModBlocks.MAILBOX));
				helper.assertTrue(boxes.size() == 1, "the built Post Office has " + boxes.size() + " Mailboxes, the brief says exactly 1");
				List<String> others = new ArrayList<>(jobSites(level, o, e, false));
				others.removeIf(s -> s.startsWith("mailbox "));
				helper.assertTrue(others.isEmpty(), "the built Post Office has other job blocks: " + others);
				List<BlockPos> beds = inBox(level, o, e.size(), QaRebuiltCivic1122bGameTests::bedHead);
				helper.assertTrue(!beds.isEmpty(), "the built Post Office has no bed for the Postman");
				for (BlockPos b : beds) {
					helper.assertTrue(level.getPoiManager().existsAtPosition(PoiTypes.HOME, b), "the built bed at " + b.subtract(o).toShortString() + " is no home");
				}
				box[0] = boxes.get(0);
				BlockPos stand = null;
				for (Direction d : Direction.Plane.HORIZONTAL) {
					BlockPos p = box[0].relative(d);
					if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP) && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
						&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) {
						stand = p;
						break;
					}
				}
				helper.assertTrue(stand != null, "no free cell to stand on right beside the built Mailbox at " + box[0].subtract(o).toShortString());
				Villager v = EntityType.VILLAGER.create(level);
				helper.assertTrue(v != null, "(setup) no villager made");
				v.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
				level.addFreshEntity(v);
				ServerPlayer player = QaServiceJobs1031GameTests.listener(helper, told);
				StationsSpecGameTests.rightClick(player, v, new ItemStack(Items.PAPER), true);
				clerk[0] = v;
				LOGGER.info("qa civic 1122b: paper on a villager at {} by the mailbox {}", stand.subtract(o).toShortString(), box[0].subtract(o).toShortString());
			}
			Villager v = clerk[0];
			helper.assertTrue(v.getVillagerData().getProfession() == ModVillagers.POSTMAN, "paper by the built Mailbox made the villager a "
				+ StationsSpecGameTests.name(v.getVillagerData().getProfession()) + "; the player was told " + told.stream().map(Component::getString).toList()
				+ " (Mailbox POI " + level.getPoiManager().getType(box[0]).map(h -> h.unwrapKey().map(Object::toString).orElse("?")).orElse("none")
				+ ", free tickets " + level.getPoiManager().getFreeTickets(box[0]) + ", villager " + v.blockPosition().subtract(box[0]).toShortString()
				+ " from the Mailbox)");
			helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(box[0]::equals).isPresent(),
				"the new Postman works at " + v.getBrain().getMemory(MemoryModuleType.JOB_SITE) + ", not the built Mailbox");
		});
	}

	/**
	 * clock_tower.md ("still loads by id without them: a blueprint already handed out keeps working"): with no Create
	 * installed (this suite), a Clock Tower blueprint is built from exactly its list across a restart (tall_area: it is 26
	 * tall), within 30,000 ticks, nothing skipped, every block as drawn; built, exactly one cartography table and it is a
	 * job site, no other job block (the brief: "the old drawing's barrel is a Fisherman's job site").
	 */
	//$ gametest_ticks_batch TALL '32000' '"qaCiv1122b_clockTower"'
	@GameTest(template = TALL, timeoutTicks = 32000, batch = "qaCiv1122b_clockTower")
	public void qaTheClockTowerWithoutCreateIsBuiltFromExactlyItsListAcrossARestartWithItsCartographersTable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry e = StarterBlueprints.CLOCK_TOWER;
		exactAcrossARestart(helper, e, new BlockPos(9, 2, 9), 30000, 108, placement -> {
			BlockPos o = placement.origin();
			List<BlockPos> tables = inBox(level, o, e.size(), s -> s.is(Blocks.CARTOGRAPHY_TABLE));
			helper.assertTrue(tables.size() == 1, "the built Clock Tower has " + tables.size() + " cartography tables, the brief says 1");
			helper.assertTrue(level.getPoiManager().existsAtPosition(PoiTypes.CARTOGRAPHER, tables.get(0)), "the built cartography table is no job site");
			List<String> others = new ArrayList<>(jobSites(level, o, e, false));
			others.removeIf(s -> s.startsWith("cartography_table "));
			helper.assertTrue(others.isEmpty(), "the built Clock Tower has other job blocks: " + others);
		});
	}

	// --------------------------------------------------------------------------------------- 2. live upgrade

	private static String contents(Container c) {
		List<String> out = new ArrayList<>();
		for (int slot = 0; slot < c.getContainerSize(); slot++) {
			if (!c.getItem(slot).isEmpty()) {
				out.add(slot + ":" + c.getItem(slot).getCount() + "x" + BuiltInRegistries.ITEM.getKey(c.getItem(slot).getItem()).getPath());
			}
		}
		return out.toString();
	}

	/**
	 * graveyard.md, "Level II ... built over a finished Level I with the same origin and front", "Level II must leave every
	 * slot of Level I free": a Graveyard I stands finished; the Undertaker's brewing stand (6, 1, 11) is claimed; the
	 * player's revival items (2 golden apples, a totem) are in the chest (8, 1, 11); three villagers already rest in the
	 * yard's first three slots, each grave holding its villager. A Builder stocked with exactly what the upgrade still
	 * needs builds Graveyard II over it. When DONE: every block as drawn, nothing skipped, the stand still claimed, the
	 * items still in the chest slot for slot, the three graves still there with their villagers, and every other slot
	 * still free.
	 */
	//$ gametest_ticks_batch HUGE '30000' '"qaCiv1122b_graveyardUpgrade"'
	@GameTest(template = HUGE, timeoutTicks = 30000, batch = "qaCiv1122b_graveyardUpgrade")
	public void qaGraveyardIIOverIKeepsTheUndertakersStandHisChestAndTheGravesResting(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		rules(helper);
		StarterBlueprints.Entry lower = StarterBlueprints.GRAVEYARD;
		StarterBlueprints.Entry upper = StarterBlueprints.GRAVEYARD_2;
		BlockPos origin = helper.absolutePos(new BlockPos(10, 2, 10));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		level.getStructureManager().get(lower.id()).orElseThrow()
			.placeInWorld(level, origin, origin, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1122L), 2);
		BuildSiteManager.get(level).recordFinished(lower.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BlockPos stand = origin.offset(6, 1, 11);
		helper.assertTrue(level.getBlockState(stand).is(Blocks.BREWING_STAND), "(setup) no brewing stand at (6, 1, 11) of Graveyard I: " + level.getBlockState(stand));
		helper.assertTrue(level.getPoiManager().take(h -> h.is(PoiTypes.CLERIC), (h, p) -> p.equals(stand), stand, 1).isPresent(),
			"(setup) the brewing stand couldn't be claimed");
		BlockPos chest = origin.offset(8, 1, 11);
		helper.assertTrue(level.getBlockEntity(chest) instanceof Container, "(setup) no chest at (8, 1, 11) of Graveyard I");
		Container supplies = (Container) level.getBlockEntity(chest);
		supplies.setItem(0, new ItemStack(Items.GOLDEN_APPLE, 2));
		supplies.setItem(9, new ItemStack(Items.TOTEM_OF_UNDYING));
		String before = contents(supplies);
		List<BlockPos> slots = slots(level, origin);
		helper.assertTrue(slots.size() >= 32, "(setup) Graveyard I has " + slots.size() + " slots");
		String[] names = {"Mira", "Tobin", "Ansel"};
		List<BlockPos> graves = new ArrayList<>();
		for (int i = 0; i < names.length; i++) {
			BlockPos g = slots.get(i);
			helper.assertTrue(slotFree(level, g), "(setup) slot " + g.subtract(origin).toShortString() + " isn't free in the placed Graveyard I");
			level.setBlockAndUpdate(g, ModBlocks.GRAVE.defaultBlockState());
			CompoundTag who = new CompoundTag();
			who.putString("qa_name", names[i]);
			((GraveBlockEntity) level.getBlockEntity(g)).fill(who, Component.literal(names[i]), "minecraft:farmer", 2, 100L + i);
			graves.add(g);
		}
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		var report = BlueprintSupplies.check(level, new BlueprintData(upper.id(), Optional.of(upper.size()), Optional.of(placement)))
			.orElseThrow(() -> new AssertionError("no supply report for " + n(upper)));
		List<ItemStack> stock = new ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(!stock.isEmpty(), "(setup) the upgrade needs nothing: " + report);
		helper.assertTrue(stock.size() <= BARRELS.length * 27, "(setup) the test needs more barrels: " + stock.size() + " stacks");
		for (int i = 0; i < BARRELS.length; i++) {
			helper.setBlock(BARRELS[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(BARRELS[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, upper.id(), placement);
		helper.assertTrue(site != null, n(upper) + " didn't start over the finished " + n(lower));
		UUID id = site.id();
		int[] skipped = {0};
		helper.onEachTick(() -> {
			holdDay(helper);
			BuildSite s = BuildSiteManager.get(level).get(id);
			if (s != null) {
				skipped[0] = s.skipped();
			}
		});
		helper.succeedWhen(() -> {
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(s == null, n(upper) + " over " + n(lower) + " is still building: " + where(s));
			// the graves resting in the slots are the village's, not the drawing's: the plan reads their cells as air
			List<BlockPos> wrong = BuildPlan.create(BlueprintLibrary.get(level, upper.id()).orElseThrow(), placement).unfinished(level).stream()
				.filter(p -> !graves.contains(p)).toList();
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of " + n(upper) + " wrong after the upgrade (the graves aside), e.g. "
				+ wrong.stream().limit(5).map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(skipped[0] == 0, "the upgrade to " + n(upper) + " skipped " + skipped[0] + " block(s)");
			helper.assertTrue(level.getBlockState(stand).is(Blocks.BREWING_STAND), "the Undertaker's brewing stand is gone after the upgrade");
			helper.assertTrue(level.getPoiManager().existsAtPosition(PoiTypes.CLERIC, stand) && level.getPoiManager().getFreeTickets(stand) == 0,
				"the Undertaker's claimed brewing stand was freed by the upgrade (broken and re-placed): he lost his post");
			helper.assertTrue(level.getBlockEntity(chest) instanceof Container, "the Undertaker's chest is gone after the upgrade");
			String now = contents((Container) level.getBlockEntity(chest));
			helper.assertTrue(now.equals(before), "the revival items in the Undertaker's chest changed in the upgrade: " + before + " -> " + now);
			for (int i = 0; i < graves.size(); i++) {
				BlockPos g = graves.get(i);
				helper.assertTrue(level.getBlockEntity(g) instanceof GraveBlockEntity grave && grave.villager().getString("qa_name").equals(names[i]),
					names[i] + "'s grave at " + g.subtract(origin).toShortString() + " is gone after the upgrade: " + level.getBlockState(g));
			}
			List<String> taken = slots(level, origin).stream().filter(p -> !graves.contains(p) && !slotFree(level, p))
				.map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList();
			helper.assertTrue(taken.isEmpty(), "grave slots lost by the upgrade to Graveyard II: " + taken);
		});
	}

	// ----------------------------------------------------------------------------------------------- 3. breakage

	/**
	 * post_office.md, built from its list: with the item its walls need most left out of the chests it doesn't finish,
	 * and its site says it is missing that item; once the player puts exactly the missing count in a chest it finishes as
	 * drawn with nothing skipped.
	 */
	//$ gametest_ticks_batch HUGE '24000' '"qaCiv1122b_postShort"'
	@GameTest(template = HUGE, timeoutTicks = 24000, batch = "qaCiv1122b_postShort")
	public void qaThePostOfficeShortOfOneMaterialWaitsThenFinishesOnceSupplied(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		rules(helper);
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, StarterBlueprints.POST_OFFICE, new BlockPos(12, 2, 10));
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(job.placement()));
		Map<Item, Integer> structure = new LinkedHashMap<>();
		for (BuildPlan.Step s : job.plan().steps(BuildPlan.Stage.STRUCTURE)) {
			Item item = s.state().getBlock().asItem();
			if (job.list().containsKey(item)) {
				structure.merge(item, 1, Integer::sum);
			}
		}
		helper.assertTrue(!structure.isEmpty(), "the Post Office's list has nothing its STRUCTURE stage places");
		Item short_ = structure.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
		int count = job.list().get(short_);
		for (BlockPos chest : CHESTS) {
			Container c = helper.getBlockEntity(chest);
			for (int slot = 0; slot < c.getContainerSize(); slot++) {
				if (c.getItem(slot).is(short_)) {
					c.setItem(slot, ItemStack.EMPTY);
				}
			}
		}
		LOGGER.info("qa civic 1122b: post office short of {} x{}", short_, count);
		boolean[] sawWait = {false};
		boolean[] restocked = {false};
		int[] skipped = {0};
		helper.onEachTick(() -> {
			holdDay(helper);
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			if (live != null) {
				skipped[0] = live.skipped();
				if (!restocked[0] && live.missing().containsKey(short_)) {
					sawWait[0] = true;
				}
			}
			if (!restocked[0] && helper.getTick() >= 5000) {
				helper.assertTrue(live != null, "the Post Office finished without its " + short_ + " (" + count + " asked for)");
				helper.assertTrue(sawWait[0], "the Post Office never said it was missing " + short_ + ": " + where(live));
				restocked[0] = true;
				Container c = helper.getBlockEntity(CHESTS.get(0));
				int left = count;
				for (int slot = 0; slot < c.getContainerSize() && left > 0; slot++) {
					if (c.getItem(slot).isEmpty()) {
						int take = Math.min(left, short_.getDefaultMaxStackSize());
						c.setItem(slot, new ItemStack(short_, take));
						left -= take;
					}
				}
				for (int i = 1; i < CHESTS.size() && left > 0; i++) {
					Container more = helper.getBlockEntity(CHESTS.get(i));
					for (int slot = 0; slot < more.getContainerSize() && left > 0; slot++) {
						if (more.getItem(slot).isEmpty()) {
							int take = Math.min(left, short_.getDefaultMaxStackSize());
							more.setItem(slot, new ItemStack(short_, take));
							left -= take;
						}
					}
				}
				helper.assertTrue(left == 0, "(setup) no room in the chests to restock");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restocked[0], "not restocked yet");
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			helper.assertTrue(live == null, "restocked, the Post Office is still building: " + where(live));
			List<BlockPos> unfinished = job.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), "restocked, " + unfinished.size() + " block(s) of the Post Office are not as drawn");
			helper.assertTrue(skipped[0] == 0, "restocked, the Post Office skipped " + skipped[0] + " block(s)");
		});
	}

	/**
	 * post_office.md ("exactly one Mailbox ... his workstation and the players' counter"): built from exactly its list
	 * plus one spare Mailbox in a chest; the moment its Mailbox is placed (the site still building) it is broken. The
	 * builder puts it back: the Post Office ends DONE with every block as drawn, nothing skipped, the Mailbox in its cell.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qaCiv1122b_mailboxBroken"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qaCiv1122b_mailboxBroken")
	public void qaThePostOfficesMailboxBrokenMidBuildIsPutBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		rules(helper);
		StarterBlueprints.Entry e = StarterBlueprints.POST_OFFICE;
		QaDecorations1104fGameTests.Job job = QaDecorations1104fGameTests.exact(helper, e, new BlockPos(12, 2, 10));
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(job.placement()));
		List<BlockPos> cells = new ArrayList<>();
		for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
			job.plan().steps(stage).stream().filter(s -> s.state().is(ModBlocks.MAILBOX) && !cells.contains(s.pos())).forEach(s -> cells.add(s.pos().immutable()));
		}
		helper.assertTrue(cells.size() == 1, "(setup) the plan places " + cells.size() + " Mailboxes");
		BlockPos box = cells.get(0);
		boolean spare = false;
		for (BlockPos chest : CHESTS) {
			Container c = helper.getBlockEntity(chest);
			for (int slot = 0; slot < c.getContainerSize() && !spare; slot++) {
				if (c.getItem(slot).isEmpty()) {
					c.setItem(slot, new ItemStack(ModBlocks.MAILBOX));
					spare = true;
				}
			}
		}
		helper.assertTrue(spare, "(setup) no room for the spare Mailbox");
		boolean[] broken = {false};
		int[] skipped = {0};
		helper.onEachTick(() -> {
			holdDay(helper);
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			if (live == null) {
				return;
			}
			skipped[0] = live.skipped();
			if (!broken[0] && level.getBlockState(box).is(ModBlocks.MAILBOX)) {
				broken[0] = true;
				level.setBlockAndUpdate(box, Blocks.AIR.defaultBlockState());
				LOGGER.info("qa civic 1122b: broke the post office's mailbox at {}", where(live));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0], "the Mailbox was never placed while the Post Office was building");
			BuildSite live = BuildSiteManager.get(level).get(job.site().id());
			helper.assertTrue(live == null, "after its Mailbox was broken the Post Office is still building: " + where(live));
			helper.assertTrue(level.getBlockState(box).is(ModBlocks.MAILBOX), "the broken Mailbox wasn't put back: " + level.getBlockState(box));
			List<BlockPos> unfinished = job.plan().unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) of the Post Office not as drawn, e.g. "
				+ unfinished.stream().limit(5).map(p -> p.subtract(job.placement().origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(skipped[0] == 0, "the Post Office skipped " + skipped[0] + " block(s)");
		});
	}

	// ------------------------------------------------------------------------------------------------- 4. words

	/**
	 * Every name these builds bring reads on the Blueprint Table as its brief names it (Chapel, Graveyard, Graveyard II,
	 * Clock Tower, Post Office), each from en_us.json, and the Chapel's service reads "Chapel"; no line of en_us.json that
	 * names one of these builds uses British spelling (owner, 2026-10-26: American English).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryCivicNameReadsInAmericanEnglish(GameTestHelper helper) {
		Map<StarterBlueprints.Entry, String> names = new LinkedHashMap<>();
		names.put(StarterBlueprints.CHAPEL, "Chapel");
		names.put(StarterBlueprints.GRAVEYARD, "Graveyard");
		names.put(StarterBlueprints.GRAVEYARD_2, "Graveyard II");
		names.put(StarterBlueprints.CLOCK_TOWER, "Clock Tower");
		names.put(StarterBlueprints.POST_OFFICE, "Post Office");
		com.google.gson.JsonObject lang;
		try (InputStream in = QaRebuiltCivic1122bGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			helper.assertTrue(in != null, "en_us.json not found");
			lang = com.google.gson.JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException ex) {
			throw new AssertionError(ex);
		}
		List<String> bad = new ArrayList<>();
		names.forEach((entry, want) -> {
			String key = "blueprint.aliveworkplace." + n(entry);
			if (!lang.has(key)) {
				bad.add(key + " missing from en_us.json");
			} else if (!lang.get(key).getAsString().equals(want)) {
				bad.add(key + " reads \"" + lang.get(key).getAsString() + "\", the brief names it \"" + want + "\"");
			}
			String shown = Blueprints.displayName(entry.id()).getString();
			if (!shown.equals(want)) {
				bad.add(n(entry) + " shows on the Table as \"" + shown + "\", want \"" + want + "\"");
			}
		});
		if (!lang.has("service.aliveworkplace.chapel") || !lang.get("service.aliveworkplace.chapel").getAsString().equals("Chapel")) {
			bad.add("service.aliveworkplace.chapel reads " + lang.get("service.aliveworkplace.chapel") + ", the brief says \"Chapel\"");
		}
		java.util.regex.Pattern british = java.util.regex.Pattern.compile("(?i)\\b(colour|colours|centre|honour|grey|cancelled|jeweller|neighbour|organise|realise|travelling|parlour|metre|metres)\\b");
		java.util.regex.Pattern ours = java.util.regex.Pattern.compile("(?i)chapel|graveyard|clock tower|post office");
		for (Map.Entry<String, com.google.gson.JsonElement> line : lang.entrySet()) {
			String v = line.getValue().getAsString();
			if (ours.matcher(v).find() && british.matcher(v).find()) {
				bad.add(line.getKey() + " = \"" + v + "\" (British spelling)");
			}
		}
		helper.assertTrue(bad.isEmpty(), "names: " + bad);
		helper.succeed();
	}
}
