package com.ashy0019.localeventbridge.runelite;

import com.ashy0019.localeventbridge.event.LootReceivedEvent;
import java.util.Collection;
import java.util.Objects;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;

/** Translates RuneLite loot stacks and market prices into a neutral event. */
public final class RuneLiteLootEventAdapter
{
	private static final String SOURCE = RuneLiteEventBridge.SOURCE_ID;

	private final ItemManager itemManager;

	public RuneLiteLootEventAdapter(ItemManager itemManager)
	{
		this.itemManager = Objects.requireNonNull(itemManager, "itemManager");
	}

	public LootReceivedEvent adapt(Collection<ItemStack> items)
	{
		Objects.requireNonNull(items, "items");
		long totalValue = 0L;
		int stackCount = 0;
		for (ItemStack item : items)
		{
			ItemStack required = Objects.requireNonNull(item, "item");
			int unitPrice = Math.max(0, itemManager.getItemPrice(required.getId()));
			int quantity = Math.max(0, required.getQuantity());
			totalValue = saturatedAdd(totalValue, (long) unitPrice * quantity);
			stackCount++;
		}
		return new LootReceivedEvent(SOURCE, stackCount, totalValue);
	}

	static long saturatedAdd(long left, long right)
	{
		if (right > 0L && left > Long.MAX_VALUE - right)
		{
			return Long.MAX_VALUE;
		}
		return left + right;
	}
}
