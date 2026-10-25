package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.platform.Attachment;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.threat.Plague;
import io.github.jcondedata.aliveworkplace.trainer.MegaShrines;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.work.FirewardenWork;
import io.github.jcondedata.aliveworkplace.work.WellKeeperWork;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

/**
 * QA (lane E, saves): every simple value the mod saves on a villager (each {@code Attachment<Integer>}, {@code Long},
 * {@code Boolean}, {@code String} and {@code Double} in ModAttachments, found by reflection, so one added later is
 * covered too) comes back after the villager is saved and loaded as a chunk does. Each gets its own value, so two
 * values sharing a save name, or one not saved at all, fail here. Then the few that no other test reloaded, read back
 * through what the game asks of them: a Firewarden's empty buckets, a Well Keeper's full bucket, a Physician's seen
 * plague case and the Mega Shrine show's good mood.
 */
public class QaEverySavedValueGameTests implements FabricGameTest {

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everySimpleSavedValueComesBackAfterASave(GameTestHelper helper) throws Exception {
		Villager v = villager(helper);
		Map<String, Object> wrote = new LinkedHashMap<>();
		Map<String, Attachment<Object>> attachments = new LinkedHashMap<>();
		int n = 0;
		for (Field f : ModAttachments.class.getFields()) {
			if (!Modifier.isStatic(f.getModifiers()) || !(f.get(null) instanceof Attachment<?>)) {
				continue;
			}
			Type type = f.getGenericType();
			if (!(type instanceof ParameterizedType p) || !(p.getActualTypeArguments()[0] instanceof Class<?> kind)) {
				continue;
			}
			n++;
			Object value;
			if (kind == Integer.class) {
				value = 1000 + n;
			} else if (kind == Long.class) {
				value = 5_000_000_000L + n;
			} else if (kind == Boolean.class) {
				value = Boolean.TRUE;
			} else if (kind == String.class) {
				value = "qa_value_" + n;
			} else if (kind == Double.class) {
				value = 0.125 * n;
			} else {
				continue;
			}
			@SuppressWarnings("unchecked")
			Attachment<Object> a = (Attachment<Object>) f.get(null);
			a.set(v, value);
			wrote.put(f.getName(), value);
			attachments.put(f.getName(), a);
		}
		helper.assertTrue(wrote.size() >= 80, "only " + wrote.size() + " simple saved values found in ModAttachments");

		Villager back = reload(helper, v);
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, Object> e : wrote.entrySet()) {
			Object read = attachments.get(e.getKey()).get(back);
			if (!e.getValue().equals(read)) {
				wrong.add(e.getKey() + " saved " + e.getValue() + ", read " + read);
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + wrote.size() + " values changed over a save: " + wrong);
		helper.succeed();
	}

	/** A Firewarden who emptied both buckets on a fire, saved before refilling: no free water from the reload. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aFirewardensEmptyBucketsStayEmptyOverASave(GameTestHelper helper) {
		Villager warden = villager(helper);
		FirewardenWork.setWater(warden, 0);
		Villager back = reload(helper, warden);
		helper.assertTrue(FirewardenWork.water(back) == 0, "buckets full after a reload: " + FirewardenWork.water(back));
		String title = FirewardenWork.title(back).getString();
		helper.assertTrue(title.equals("Firewarden · 0 of 2 buckets full"), "the line above the head: " + title);

		Villager half = villager(helper);
		FirewardenWork.setWater(half, 1);
		Villager halfBack = reload(helper, half);
		helper.assertTrue(FirewardenWork.water(halfBack) == 1, "one bucket became " + FirewardenWork.water(halfBack));

		Villager fresh = reload(helper, villager(helper));
		helper.assertTrue(FirewardenWork.water(fresh) == FirewardenWork.BUCKETS, "a new firewarden starts with " + FirewardenWork.water(fresh));
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aWellKeepersFullBucketStaysFullOverASave(GameTestHelper helper) {
		Villager keeper = villager(helper);
		WellKeeperWork.setWater(keeper, true);
		helper.assertTrue(WellKeeperWork.hasWater(reload(helper, keeper)), "the full bucket was emptied by a reload");
		Villager empty = villager(helper);
		WellKeeperWork.setWater(empty, false);
		helper.assertFalse(WellKeeperWork.hasWater(reload(helper, empty)), "an empty bucket was filled by a reload");
		helper.succeed();
	}

	/** A plague case the Physician saw keeps to their house after the chunk reloads; a well villager isn't marked. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSeenPlagueCaseIsStillSeenAfterASave(GameTestHelper helper) {
		Villager ill = villager(helper);
		ModAttachments.PLAGUE_SEEN.set(ill, true);
		helper.assertTrue(Plague.seen(reload(helper, ill)), "the Physician's visit was forgotten over a save");
		helper.assertFalse(Plague.seen(reload(helper, villager(helper))), "a villager nobody saw is seen after a save");
		helper.succeed();
	}

	/** The show's good mood lasts its days whether or not the chunk was saved in between. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theMegaShowsGoodMoodLastsOverASave(GameTestHelper helper) {
		Villager watcher = villager(helper);
		long today = Chronicle.day(helper.getLevel());
		ModAttachments.MEGA_SHOW_DAY.set(watcher, today);
		Villager back = reload(helper, watcher);
		helper.assertTrue(MegaShrines.mood(helper.getLevel(), back) == MegaShrines.MOOD,
			"the show's mood after a save: " + MegaShrines.mood(helper.getLevel(), back));
		helper.assertTrue(MegaShrines.mood(helper.getLevel(), reload(helper, villager(helper))) == 0, "a villager who never watched is in a show's mood");
		helper.succeed();
	}

	private static Villager villager(GameTestHelper helper) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
		return v;
	}

	/** Saves the villager and loads a new one from the save, as a chunk does. */
	private static Villager reload(GameTestHelper helper, Villager villager) {
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(villager.save(tag), "the villager wasn't saved");
		Entity loaded = EntityType.loadEntityRecursive(tag, helper.getLevel(), e -> e);
		helper.assertTrue(loaded instanceof Villager && loaded != villager, "the save didn't load as a new villager: " + loaded);
		return (Villager) loaded;
	}
}
