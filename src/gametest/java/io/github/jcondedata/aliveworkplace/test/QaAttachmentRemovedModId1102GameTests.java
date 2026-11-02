package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.platform.Attachment;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane E, saves and optional mods, 2026-11-02; B791 for every attachment, so one added later is covered too): Fabric's
 * attachment loader drops a whole attachment when its codec returns any error, so a strict codec loses a villager's whole
 * record for one id of a mod taken out of the pack (B791: a builder's whole bag). Every attachment in
 * {@code ModAttachments} is set to a made value with every part filled ({@link QaEveryRecordField1101GameTests}'s
 * generator), the villager is saved, each place in the attachment's saved tag that names an item or a block is in turn
 * renamed to an id no mod registers, and the tag is loaded into a new villager. The attachment must still be there: only
 * the gone thing is lost, as a chest keeps its other slots. {@link #BY_DESIGN} lists the ones that rightly go whole.
 */
public class QaAttachmentRemovedModId1102GameTests implements FabricGameTest {

	/** Attachment names (as in the save) that rightly go whole when the renamed place is gone, each with why. */
	static final Map<String, String> BY_DESIGN = new LinkedHashMap<>();

	static {
		BY_DESIGN.put("plunder", "one stack: the gone item is the whole value (vanilla drops an unknown stack too)");
		BY_DESIGN.put("brigade_held", "one stack: the gone item is the whole value (vanilla drops an unknown stack too)");
		BY_DESIGN.put("crafter_job", "a job to make or from a gone item can't be done; its makings are in the bag, which is kept"
			+ " and tidied back into the Crafter's chest (QaCrafterJobRemovedMod1102GameTests)");
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void aRemovedModsIdOnAVillagerLosesOnlyThatThing(GameTestHelper helper) throws Exception {
		QaEveryRecordField1101GameTests gen = new QaEveryRecordField1101GameTests();
		gen.registries = helper.getLevel().registryAccess();
		List<String> lost = new ArrayList<>();
		List<String> renamedAt = new ArrayList<>();
		Set<String> used = new LinkedHashSet<>();
		int attachments = 0;
		for (Field f : ModAttachments.class.getFields()) {
			if (!Modifier.isStatic(f.getModifiers()) || !(f.get(null) instanceof Attachment<?>) || !(f.getGenericType() instanceof ParameterizedType p)) {
				continue;
			}
			@SuppressWarnings("unchecked")
			Attachment<Object> a = (Attachment<Object>) f.get(null);
			Object value;
			try {
				value = gen.make(p.getActualTypeArguments()[0], f.getName());
			} catch (QaEveryRecordField1101GameTests.Unbuildable e) {
				continue; // QaEveryRecordField1101GameTests names any it can't make
			}
			attachments++;
			Villager v = EntityType.VILLAGER.create(helper.getLevel());
			a.set(v, value);
			CompoundTag saved = v.saveWithoutId(new CompoundTag());
			CompoundTag all = saved.getCompound("fabric:attachments");
			String key = null;
			for (String k : all.getAllKeys()) {
				if (k.startsWith("aliveworkplace:")) {
					key = k;
				}
			}
			if (key == null) {
				continue; // nothing saved (an empty value)
			}
			String name = key.substring("aliveworkplace:".length());
			List<List<Object>> found = new ArrayList<>();
			QaSavedDataRemovedModId1102GameTests.ids(all.get(key), new ArrayList<>(List.of("fabric:attachments", key)), found);
			Set<String> seen = new LinkedHashSet<>();
			for (List<Object> place : found) {
				String where = QaSavedDataRemovedModId1102GameTests.show(place);
				if (!seen.add(where)) {
					continue;
				}
				renamedAt.add(where);
				CompoundTag hurt = QaSavedDataRemovedModId1102GameTests.renamed(saved, place);
				Villager back = EntityType.VILLAGER.create(helper.getLevel());
				try {
					back.load(hurt);
				} catch (RuntimeException x) {
					lost.add(where + ": the villager's load threw " + x);
					continue;
				}
				if (a.get(back) == null) {
					if (BY_DESIGN.containsKey(name)) {
						used.add(name);
					} else {
						lost.add(where + ": the whole " + name + " was dropped (" + value + ")");
					}
				}
			}
		}
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA attachment removed mod ids: {} attachments, renamed at {}", attachments, renamedAt);
		lost.forEach(l -> io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.error("QA attachment removed mod id lost: {}", l));
		List<String> stale = BY_DESIGN.keySet().stream().filter(k -> !used.contains(k)).toList();
		helper.assertTrue(attachments >= 100, "only " + attachments + " attachments made");
		helper.assertTrue(renamedAt.size() >= 5, "only " + renamedAt.size() + " item or block ids found on the villager: " + renamedAt);
		helper.assertTrue(lost.isEmpty(), QaSavedDataCollections1101GameTests.cut(lost.size()
			+ " renamed id(s) cost a whole attachment (each in the log): " + lost));
		helper.assertTrue(stale.isEmpty(), "BY_DESIGN names attachments that keep their value now (take them out): " + stale);
		helper.succeed();
	}

}
