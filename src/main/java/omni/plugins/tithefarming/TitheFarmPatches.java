/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.tithefarming;

import java.util.Set;

/**
 * Which patch object id means which growth stage. Pure lookups on ids, so the plant logic can be tested.
 */
public final class TitheFarmPatches
{
	/** An empty, unplanted patch. */
	public static final int EMPTY = 27383;

	private static final Set<Integer> PATCH_IDS = Set.of(27383, 27384, 27385, 27387, 27388, 27390, 27391, 27393, 27395,
		27396, 27398, 27399, 27401, 27402, 27404, 27406, 27407, 27409, 27410, 27412, 27413, 27415);
	private static final Set<Integer> SEEDLINGS = Set.of(27383, 27384, 27395, 27406);
	private static final Set<Integer> STAGE_1 = Set.of(27387, 27398, 27409);
	private static final Set<Integer> STAGE_2 = Set.of(27390, 27401, 27412);
	private static final Set<Integer> WATERED = Set.of(27385, 27388, 27391, 27396, 27399, 27402, 27407, 27410, 27413);

	private TitheFarmPatches()
	{
	}

	public static boolean isPatchId(int id)
	{
		return PATCH_IDS.contains(id);
	}

	public static boolean isEmpty(int id)
	{
		return id == EMPTY;
	}

	/** An empty patch or a seedling; only counts while planting. */
	public static boolean isEmptyOrSeedling(int id, TitheFarmState state)
	{
		return state == TitheFarmState.PLANTING_SEEDS && SEEDLINGS.contains(id);
	}

	public static boolean isStage1(int id)
	{
		return STAGE_1.contains(id);
	}

	public static boolean isStage2(int id)
	{
		return STAGE_2.contains(id);
	}

	public static boolean isWatered(int id)
	{
		return WATERED.contains(id);
	}

	/** This seed's young plant, which is waiting for water; only counts while planting. */
	public static boolean isWaitingForWater(int id, TitheFarmMaterial seed, TitheFarmState state)
	{
		if (state != TitheFarmState.PLANTING_SEEDS)
		{
			return false;
		}
		switch (seed)
		{
			case BOLOGANO_SEED:
				return id == 27395 || id == 27398 || id == 27401;
			case LOGAVANO_SEED:
				return id == 27406 || id == 27409 || id == 27412;
			case GOLOVANOVA_SEED:
				return id == 27384 || id == 27387 || id == 27390;
			default:
				return false;
		}
	}

	public static boolean isValidToWater(int id, TitheFarmMaterial seed, TitheFarmState state)
	{
		return isWaitingForWater(id, seed, state) || isStage1(id) || isStage2(id);
	}

	/** The ripe plant's object id for the seed. */
	public static int harvestId(TitheFarmMaterial seed)
	{
		switch (seed)
		{
			case BOLOGANO_SEED:
				return 27404;
			case LOGAVANO_SEED:
				return 27415;
			case GOLOVANOVA_SEED:
				return 27393;
			default:
				return -1;
		}
	}

	public static boolean isValidToHarvest(int id, TitheFarmMaterial seed)
	{
		return id == harvestId(seed);
	}
}
