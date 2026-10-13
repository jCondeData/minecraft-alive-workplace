package io.github.jcondedata.aliveworkplace.wonder.powers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * {@code sentries} (35.16, the Great Wall's): while the Wonder stands, iron golem sentries keep its walk
 * ({@code wonder/GreatWallSentries}), one at each of its {@code posts} (where each stands, {x, y, z} in the Wonder's own
 * blueprint: the Great Wall's tower alcoves), at most {@code count} of them (default: one a post). They fight anything
 * within {@code range} blocks of the wall (default 16, 4 to {@link #MAX_RANGE}) and go back to their posts; a lost one
 * is replaced the next morning from the stockpile.
 */
public record SentriesPower(int count, double range, List<BlockPos> posts) implements WonderPower {
	public static final String TYPE = "sentries";
	/** Most sentries one Wonder may keep. */
	public static final int MAX = 16;
	/** The farthest a file may send them. */
	public static final double MAX_RANGE = 48;

	static SentriesPower read(JsonObject json) {
		List<BlockPos> posts = new ArrayList<>();
		JsonArray list = json.has("posts") ? json.getAsJsonArray("posts") : new JsonArray();
		for (JsonElement e : list) {
			JsonArray xyz = e.getAsJsonArray();
			if (xyz.size() != 3) {
				throw new IllegalArgumentException("each of 'posts' must be [x, y, z], not " + xyz);
			}
			posts.add(new BlockPos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()));
		}
		int count = json.has("count") ? json.get("count").getAsInt() : posts.size();
		if (count < 0 || count > MAX) {
			throw new IllegalArgumentException("'count' must be 0 to " + MAX + ", not " + count);
		}
		if (count > posts.size()) {
			throw new IllegalArgumentException("'count' is " + count + " but only " + posts.size() + " 'posts' are given");
		}
		double range = json.has("range") ? json.get("range").getAsDouble() : 16;
		if (!(range >= 4 && range <= MAX_RANGE)) {
			throw new IllegalArgumentException("'range' must be 4 to " + (int) MAX_RANGE + ", not " + range);
		}
		return new SentriesPower(count, range, List.copyOf(posts.subList(0, count)));
	}

	@Override
	public String type() {
		return TYPE;
	}
}
