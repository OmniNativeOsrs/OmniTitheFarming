/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.tithefarming;

/**
 * The three Tithe Farm seeds, by the Farming level they need, and the item ids of the watering cans.
 */
public enum TitheFarmMaterial
{
	GOLOVANOVA_SEED("Golovanova seed", 34, 13426),
	BOLOGANO_SEED("Bologano seed", 54, 13427),
	LOGAVANO_SEED("Logavano seed", 74, 13428);

	/** Gricoller's can. */
	public static final int GRICOLLERS_CAN = 13353;
	/** A full watering can(8). */
	public static final int FULL_WATERING_CAN = 5340;
	/** Watering cans that can still be filled, fullest first. */
	public static final int[] CANS_TO_FILL = {5339, 5338, 5337, 5336, 5335, 5334, 5333, 5331};

	private final String itemName;
	private final int levelRequired;
	private final int fruitId;

	TitheFarmMaterial(String itemName, int levelRequired, int fruitId)
	{
		this.itemName = itemName;
		this.levelRequired = levelRequired;
		this.fruitId = fruitId;
	}

	/**
	 * The best seed a player can plant.
	 *
	 * @param farmingLevel the real Farming level
	 * @return the seed; Logavano below level 34
	 */
	public static TitheFarmMaterial forLevel(int farmingLevel)
	{
		if (farmingLevel >= LOGAVANO_SEED.levelRequired)
		{
			return LOGAVANO_SEED;
		}
		if (farmingLevel >= BOLOGANO_SEED.levelRequired)
		{
			return BOLOGANO_SEED;
		}
		if (farmingLevel >= GOLOVANOVA_SEED.levelRequired)
		{
			return GOLOVANOVA_SEED;
		}
		return LOGAVANO_SEED;
	}

	public String getName()
	{
		return itemName;
	}

	public int getLevelRequired()
	{
		return levelRequired;
	}

	public int getFruitId()
	{
		return fruitId;
	}

	@Override
	public String toString()
	{
		return itemName;
	}
}
