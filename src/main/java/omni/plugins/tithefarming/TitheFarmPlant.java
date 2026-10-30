/* Copyright (c) 2026, Omni. All rights reserved. */
package omni.plugins.tithefarming;

import omni.osrs.api.OmniObject;
import omni.osrs.model.OmniWorldPoint;

/**
 * One patch of the farm: its region tile, its planting order and the patch object last seen on it.
 */
public class TitheFarmPlant
{
	/** No patch object is known for the plant. */
	public static final int NO_OBJECT = -1;

	private final int regionX;
	private final int regionY;
	private final int index;
	private OmniObject object;

	public TitheFarmPlant(int regionX, int regionY, int index)
	{
		this.regionX = regionX;
		this.regionY = regionY;
		this.index = index;
	}

	public int getRegionX()
	{
		return regionX;
	}

	public int getRegionY()
	{
		return regionY;
	}

	public int getIndex()
	{
		return index;
	}

	/**
	 * Whether a patch object at {@code location} belongs to this plant (within one tile).
	 *
	 * @param location the object's tile
	 * @return whether it is this plant's patch
	 */
	public boolean covers(OmniWorldPoint location)
	{
		return location != null && Math.abs(location.getRegionX() - regionX) <= 1
			&& Math.abs(location.getRegionY() - regionY) <= 1;
	}

	public OmniObject getGameObject()
	{
		return object;
	}

	public void setGameObject(OmniObject object)
	{
		this.object = object;
	}

	/** The id of the patch object, or {@link #NO_OBJECT}. */
	public int objectId()
	{
		return object == null ? NO_OBJECT : object.getId();
	}

	public boolean isEmptyPatch()
	{
		return TitheFarmPatches.isEmpty(objectId());
	}

	public boolean isEmptyPatchOrSeedling(TitheFarmState state)
	{
		return object != null && TitheFarmPatches.isEmptyOrSeedling(objectId(), state);
	}

	public boolean isValidToWater(TitheFarmMaterial seed, TitheFarmState state)
	{
		return object != null && TitheFarmPatches.isValidToWater(objectId(), seed, state);
	}

	public boolean isValidToHarvest(TitheFarmMaterial seed)
	{
		return object != null && TitheFarmPatches.isValidToHarvest(objectId(), seed);
	}

	public boolean isStage1()
	{
		return object != null && TitheFarmPatches.isStage1(objectId());
	}

	public boolean isStage2()
	{
		return object != null && TitheFarmPatches.isStage2(objectId());
	}

	public boolean isWatered()
	{
		return object != null && TitheFarmPatches.isWatered(objectId());
	}
}
