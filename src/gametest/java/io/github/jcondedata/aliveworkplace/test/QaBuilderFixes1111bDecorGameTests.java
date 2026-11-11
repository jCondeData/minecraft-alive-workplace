package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (qa-lane-b-1111-0637) of B1159, from docs/wiki/candles-leather.md: while a finished build waits by the bench for a
 * decoration (item frame, painting, armor stand), the lines under the builder's head show what he's short of and whom
 * to hire, as during the build. Beyond the bug's own test: each decoration names its own job (Shepherd for paintings,
 * Carpenter for armor stands); the hire line goes once a Leatherworker is hired and he makes the frame; the frame
 * arriving in the chest ends the wait with it hung; and after a save and reload mid-wait the line is still there.
 * Helpers are {@link QaBuilderDecorations1110bGameTests}'s.
 */
public class QaBuilderFixes1111bDecorGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 8);
	private static final BlockPos FACE = ROW.south();
	private static final String NEEDS_FRAME = "Needs: 1× Item Frame";

	private static String hire(String job, String things) {
		return String.format(QaTinkererShortGameTests.lang("message.aliveworkplace.job_chain.hire"), job, things);
	}

	/** The builder's overhead status as sent now, or null when the site isn't waiting for its decorations. */
	private static BuilderStatusSync.Status waiting(ServerLevel level, UUID siteId, Villager builder) {
		BuildSite open = BuildSiteManager.get(level).get(siteId);
		if (open == null || !open.isGettingDecorations() || open.status() != BuildSite.Status.WAITING_FOR_MATERIALS) {
			return null;
		}
		return BuilderStatusSync.status(level, open, builder);
	}

	private static List<String> more(BuilderStatusSync.Status status) {
		return status.more().stream().map(Component::getString).toList();
	}

	/**
	 * A frame and a painting, nobody in the village makes either: while the finished build waits, the lines say whom to
	 * hire for each, the Leatherworker for the frame and the Shepherd for the painting.
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1111bHireEach"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1111bHireEach")
	public void eachDecorationsWaitNamesItsOwnJobToHire(GameTestHelper helper) {
		hireLines(helper, "two", 2, level -> List.of(
			new ItemFrame(level, helper.absolutePos(FACE), Direction.SOUTH),
			new Painting(level, helper.absolutePos(ROW.east(1).south()), Direction.SOUTH,
				level.registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).getHolderOrThrow(PaintingVariants.KEBAB))),
			List.of(hire("Leatherworker", "Item Frames"), hire("Shepherd", "Paintings")));
	}

	/** An armor stand alone, no Carpenter: while the finished build waits, the lines say to hire a Carpenter for it. */
	//$ gametest_ticks_batch AREA '2400' '"qa1111bHireStand"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1111bHireStand")
	public void anArmorStandsWaitNamesTheCarpenterToHire(GameTestHelper helper) {
		hireLines(helper, "stand", 1, level -> List.of(
			new ArmorStand(level, helper.absolutePos(ROW.above()).getX() + 0.5, helper.absolutePos(ROW.above()).getY(),
				helper.absolutePos(ROW.above()).getZ() + 0.5)),
			List.of(hire("Carpenter", "Armor Stands")));
	}

	private static void hireLines(GameTestHelper helper, String name, int width, Function<ServerLevel, List<Entity>> decorations, List<String> wanted) {
		QaBuilderDecorations1110bGameTests.prepare(helper);
		QaBuilderDecorations1110bGameTests.shortWait(helper, 2000);
		ResourceLocation id = blueprint(helper, name, width, 3, decorations);
		QaBuilderDecorations1110bGameTests.chest(helper, new ItemStack(Items.OAK_PLANKS, 8));
		Villager builder = QaBuilderDecorations1110bGameTests.builder(helper);
		BuildSite site = QaBuilderDecorations1110bGameTests.start(helper, builder, id);
		forgetAfter(helper, site);
		ServerLevel level = helper.getLevel();
		AtomicReference<List<String>> more = new AtomicReference<>(List.of());
		helper.onEachTick(() -> {
			if (level.getGameTime() % 20 == 0) {
				BuilderStatusSync.Status s = waiting(level, site.id(), builder);
				if (s != null) {
					more.set(more(s));
				}
			}
		});
		helper.succeedWhen(() -> {
			String all = String.join(" | ", more.get());
			for (String w : wanted) {
				helper.assertTrue(all.contains(w), "while waiting for the " + name + " decorations the lines don't say '" + w + "': " + more.get());
			}
		});
	}

	/**
	 * The hire line shows; then a Leatherworker is hired (sticks and leather in the bench's chest): from then on the
	 * lines no longer say to hire one, and he makes the frame, which is hung as the build ends.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qa1111bHired"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qa1111bHired")
	public void theHireLineGoesOnceALeatherworkerIsHiredAndTheFrameIsHung(GameTestHelper helper) {
		QaBuilderDecorations1110bGameTests.prepare(helper);
		QaBuilderDecorations1110bGameTests.shortWait(helper, 3000);
		ResourceLocation id = QaBuilderDecorations1110bGameTests.frameBlueprint(helper, false);
		QaBuilderDecorations1110bGameTests.chest(helper, new ItemStack(Items.OAK_PLANKS, 4), new ItemStack(Items.STICK, 16), new ItemStack(Items.LEATHER, 2));
		Villager builder = QaBuilderDecorations1110bGameTests.builder(helper);
		BuildSite site = QaBuilderDecorations1110bGameTests.start(helper, builder, id);
		forgetAfter(helper, site);
		ServerLevel level = helper.getLevel();
		String hire = hire("Leatherworker", "Item Frames");
		AtomicLong hiredAt = new AtomicLong(-1);
		AtomicReference<String> stale = new AtomicReference<>();
		AtomicBoolean ended = new AtomicBoolean();
		helper.onEachTick(() -> {
			if (BuildSiteManager.get(level).get(site.id()) == null) {
				ended.set(true);
				return;
			}
			if (level.getGameTime() % 20 != 0) {
				return;
			}
			BuilderStatusSync.Status s = waiting(level, site.id(), builder);
			if (s == null) {
				return;
			}
			List<String> lines = more(s);
			if (hiredAt.get() < 0 && lines.contains(hire)) {
				helper.setBlock(STATION, Blocks.CAULDRON);
				Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
				Jobs.employ(level, v, helper.absolutePos(STATION), PoiTypes.LEATHERWORKER, VillagerProfession.LEATHERWORKER);
				hiredAt.set(level.getGameTime());
			} else if (hiredAt.get() >= 0 && level.getGameTime() - hiredAt.get() > BuilderStatusSync.SUPPLY_INTERVAL + 20 && lines.contains(hire)) {
				stale.compareAndSet(null, (level.getGameTime() - hiredAt.get()) + " ticks after the hire: " + lines);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(hiredAt.get() >= 0, "the hire line never showed while waiting");
			helper.assertTrue(stale.get() == null, "with a Leatherworker hired the lines still say to hire one, " + stale.get());
			helper.assertTrue(ended.get(), "the build hasn't ended");
			helper.assertTrue(frames(helper) == 1, frames(helper) + " frames hang on the plank after the Leatherworker was hired");
		});
	}

	/** The hire line shows; then a frame is put in the bench's chest: the wait ends soon after with the frame hung. */
	//$ gametest_ticks_batch AREA '2400' '"qa1111bArrives"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1111bArrives")
	public void aFrameArrivingInTheChestEndsTheWaitWithItHung(GameTestHelper helper) {
		QaBuilderDecorations1110bGameTests.prepare(helper);
		QaBuilderDecorations1110bGameTests.shortWait(helper, 2000);
		ResourceLocation id = QaBuilderDecorations1110bGameTests.frameBlueprint(helper, false);
		Container chest = QaBuilderDecorations1110bGameTests.chest(helper, new ItemStack(Items.OAK_PLANKS, 4));
		Villager builder = QaBuilderDecorations1110bGameTests.builder(helper);
		BuildSite site = QaBuilderDecorations1110bGameTests.start(helper, builder, id);
		forgetAfter(helper, site);
		ServerLevel level = helper.getLevel();
		String hire = hire("Leatherworker", "Item Frames");
		AtomicLong arrived = new AtomicLong(-1);
		AtomicLong ended = new AtomicLong(-1);
		helper.onEachTick(() -> {
			if (BuildSiteManager.get(level).get(site.id()) == null) {
				ended.compareAndSet(-1, level.getGameTime());
				return;
			}
			if (arrived.get() >= 0 || level.getGameTime() % 20 != 0) {
				return;
			}
			BuilderStatusSync.Status s = waiting(level, site.id(), builder);
			if (s != null && more(s).contains(hire)) {
				helper.assertTrue(NEEDS_FRAME.equals(s.line().getString()), "the waiting line reads " + s.line().getString());
				chest.setItem(5, new ItemStack(Items.ITEM_FRAME));
				arrived.set(level.getGameTime());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(arrived.get() >= 0, "the hire line never showed while waiting");
			helper.assertTrue(ended.get() >= 0, "the build hasn't ended since the frame arrived");
			helper.assertTrue(ended.get() - arrived.get() < 600, "the build ended " + (ended.get() - arrived.get()) + " ticks after the frame arrived");
			helper.assertTrue(frames(helper) == 1, frames(helper) + " frames hang on the plank after one arrived");
		});
	}

	/** Saved and loaded mid-wait (the builder too): the loaded site is still waiting and its lines still name the job. */
	//$ gametest_ticks_batch AREA '2400' '"qa1111bReload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1111bReload")
	public void afterASaveAndReloadMidWaitTheHireLineStillShows(GameTestHelper helper) {
		QaBuilderDecorations1110bGameTests.prepare(helper);
		QaBuilderDecorations1110bGameTests.shortWait(helper, 2000);
		ResourceLocation id = QaBuilderDecorations1110bGameTests.frameBlueprint(helper, false);
		QaBuilderDecorations1110bGameTests.chest(helper, new ItemStack(Items.OAK_PLANKS, 4));
		Villager[] builder = {QaBuilderDecorations1110bGameTests.builder(helper)};
		BuildSite site = QaBuilderDecorations1110bGameTests.start(helper, builder[0], id);
		forgetAfter(helper, site);
		ServerLevel level = helper.getLevel();
		String hire = hire("Leatherworker", "Item Frames");
		AtomicLong reloadedAt = new AtomicLong(-1);
		AtomicReference<String> after = new AtomicReference<>();
		helper.onEachTick(() -> {
			if (level.getGameTime() % 20 != 0 || after.get() != null) {
				return;
			}
			BuilderStatusSync.Status s = waiting(level, site.id(), builder[0]);
			if (s == null) {
				return;
			}
			if (reloadedAt.get() < 0) {
				if (more(s).contains(hire)) {
					CompoundTag saved = BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess());
					BuildSiteManager loaded = BuildSiteManager.load(saved, level.registryAccess());
					level.getDataStorage().set(BuildSiteManager.NAME, loaded);
					BuildSite back = loaded.get(site.id());
					helper.assertTrue(back != null && back.isGettingDecorations(), "the waiting site didn't load still getting its decorations: "
						+ (back == null ? "gone" : back.stage() + " " + back.status()));
					builder[0] = QaBuilderTerrain1102GameTests.reload(helper, level, builder[0]);
					reloadedAt.set(level.getGameTime());
				}
			} else if (level.getGameTime() - reloadedAt.get() > 40) {
				after.set(s.line().getString() + " / " + more(s));
				helper.assertTrue(NEEDS_FRAME.equals(s.line().getString()), "after the reload the waiting line reads " + s.line().getString());
				helper.assertTrue(more(s).contains(hire), "after the reload the lines don't say '" + hire + "': " + more(s));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt.get() >= 0, "never reloaded mid-wait");
			helper.assertTrue(after.get() != null, "the loaded site's lines were never read while it waited");
		});
	}

	/** A test that passes mid-wait leaves no waiting site behind for later tests that count the saved sites. */
	private static void forgetAfter(GameTestHelper helper, BuildSite site) {
		Leftovers.after(helper, () -> BuildSiteManager.get(helper.getLevel()).remove(site.id()));
	}

	private static int frames(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemFrame.class, new AABB(helper.absolutePos(FACE)).inflate(0.5), Entity::isAlive).size();
	}

	/** As QaBuilderDecorations1110bGameTests' own: {@code width} planks east from ROW with the decorations, saved from the world. */
	private static ResourceLocation blueprint(GameTestHelper helper, String name, int width, int height, Function<ServerLevel, List<Entity>> decorations) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < width; x++) {
			helper.setBlock(ROW.east(x), Blocks.OAK_PLANKS);
		}
		List<Entity> made = decorations.apply(level);
		for (Entity e : made) {
			helper.assertTrue(!(e instanceof net.minecraft.world.entity.decoration.HangingEntity h) || h.survives(), "setup: the " + name + " can't hang");
			level.addFreshEntity(e);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"qa1111b_" + name + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(width, height, 2), true, Blocks.STRUCTURE_VOID);
		made.forEach(Entity::discard);
		for (int x = 0; x < width; x++) {
			helper.setBlock(ROW.east(x), Blocks.AIR);
		}
		return id;
	}
}
