/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.tithefarming;

/**
 * What the bot is doing in the minigame loop.
 */
public enum TitheFarmState
{
	STARTING,
	RECHARING_RUN_ENERGY,
	TAKE_SEEDS,
	REFILL_WATERCANS,
	PLANTING_SEEDS,
	HARVEST,
	LEAVE
}
