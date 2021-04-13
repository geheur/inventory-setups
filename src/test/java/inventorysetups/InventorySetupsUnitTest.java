package inventorysetups;

import com.google.inject.Guice;
import com.google.inject.testing.fieldbinder.BoundFieldModule;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemID;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.client.account.SessionManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import com.google.inject.testing.fieldbinder.Bind;
import org.mockito.stubbing.OngoingStubbing;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(MockitoJUnitRunner.class)
public class InventorySetupsUnitTest
{
	@Mock
	@Bind
	private Client client;

	@Mock
	@Bind
	private ItemManager itemManager;

	@Mock
	@Bind
	private SessionManager sessionManager;

	@Mock
	@Bind
	private InventorySetupsConfig inventorySetupsConfig;

	@Mock
	@Bind
	private RuneLiteConfig runeLiteConfig;

	@Mock
	@Bind
	private ConfigManager configManager;

	@Inject
	private InventorySetupsPlugin inventorySetupsPlugin;

	private final ScriptCallbackEvent EVENT = new ScriptCallbackEvent();

	@Before
	public void before()
	{
		Guice.createInjector(BoundFieldModule.of(this)).injectMembers(this);

		EVENT.setEventName("bankSearchFilter");

		when(itemManager.canonicalize(ItemID.COAL)).thenReturn(ItemID.COAL);
		//when(client.getIntStackSize()).thenReturn(2);
		//when(client.getStringStackSize()).thenReturn(1);
	}

	@Test
	public void testInputText()
	{
		assertEquals(inventorySetupsPlugin.parseTextInputAmount("1"), 1);
		assertEquals(inventorySetupsPlugin.parseTextInputAmount("0"), 1);
		assertEquals(inventorySetupsPlugin.parseTextInputAmount("1K"), 1000);
		assertEquals(inventorySetupsPlugin.parseTextInputAmount("10K"), 10000);
		assertEquals(inventorySetupsPlugin.parseTextInputAmount("1k"), 1000);
		assertEquals(inventorySetupsPlugin.parseTextInputAmount("1m"), 1000000);
		assertEquals(inventorySetupsPlugin.parseTextInputAmount("1M"), 1000000);
		assertEquals(inventorySetupsPlugin.parseTextInputAmount("1b"), 1000000000);
		assertEquals(inventorySetupsPlugin.parseTextInputAmount("1B"), 1000000000);
		assertEquals(inventorySetupsPlugin.parseTextInputAmount("10b"), 2147483647);
		assertEquals(inventorySetupsPlugin.parseTextInputAmount("10000M"), 2147483647);
		assertEquals(inventorySetupsPlugin.parseTextInputAmount("102391273213291"), 2147483647);
	}

	@Test
	public void testSetupContainsItem()
	{
		ArrayList<InventorySetupsItem> inventory = inventorySetupsPlugin.getNormalizedContainer(InventoryID.INVENTORY);
		ArrayList<InventorySetupsItem> equipment = inventorySetupsPlugin.getNormalizedContainer(InventoryID.EQUIPMENT);
		ArrayList<InventorySetupsItem> runePouch = null;
		HashMap<Integer, InventorySetupsItem> addItems = new HashMap<>();
		InventorySetup setup = new InventorySetup(inventory, equipment, runePouch, addItems, "Test",
												"", inventorySetupsConfig.highlightColor(), false,
										false,false, 0, 0L);

		assertFalse(inventorySetupsPlugin.setupContainsItem(setup, ItemID.COAL));
	}

	@Test
	public void testBankTagLayoutsSerialization()
	{
		InventorySetup setup = new InventorySetup(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new HashMap<>(), "testname", "", null, false, false, false, 1, 1);
		String layout = "1:1,2:2,3:3";

		enableBankTagLayoutsPlugin(true);
		setBankTagLayout(setup, layout);
		String exportedSetup = inventorySetupsPlugin.serializeSetupForExport(setup);

		setBankTagLayout(setup, null);

		// Check if the deserialization works.
		InventorySetupsPlugin.InventorySetupAndBankTagLayout inventorySetupAndBankTagLayout = inventorySetupsPlugin.deserializeImportedSetup(exportedSetup);
		assertEquals(layout, inventorySetupAndBankTagLayout.getBankTagLayout());
//		assertEquals(setup, inventorySetupAndBankTagLayout.getSetup());

		// Check to see if the config value is set.
		inventorySetupsPlugin.importSetup(exportedSetup);
		ArgumentCaptor<String> argument = ArgumentCaptor.forClass(String.class);
		verify(configManager).setConfiguration(
				eq(InventorySetupsPlugin.BANK_TAG_LAYOUTS_PLUGIN_CONFIG_GROUP),
				eq(InventorySetupsPlugin.BANK_TAG_LAYOUTS_PLUGIN_INVENTORY_SETUPS_LAYOUT_CONFIG_KEY_PREFIX + setup.getName()),
				argument.capture()
		);
		assertEquals(layout, argument.getValue());
	}

	@Test
	public void testThatBankTagLayoutIsOptional()
	{
		InventorySetup setup = new InventorySetup(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new HashMap<>(), "testname", "", null, false, false, false, 1, 1);

		String exportedSetup = inventorySetupsPlugin.serializeSetupForExport(setup);

		System.out.println("exported: \"" + exportedSetup + "\"");
		InventorySetupsPlugin.InventorySetupAndBankTagLayout inventorySetupAndBankTagLayout = inventorySetupsPlugin.deserializeImportedSetup(exportedSetup);
		assertEquals(null, inventorySetupAndBankTagLayout.getBankTagLayout());
//		assertEquals(setup, inventorySetupAndBankTagLayout.getSetup());

		inventorySetupsPlugin.importSetup(exportedSetup);
		String configuration = configManager.getConfiguration(InventorySetupsPlugin.BANK_TAG_LAYOUTS_PLUGIN_CONFIG_GROUP, InventorySetupsPlugin.BANK_TAG_LAYOUTS_PLUGIN_INVENTORY_SETUPS_LAYOUT_CONFIG_KEY_PREFIX + setup.getName());
		assertEquals(null, configuration);
	}

	/**
	 * This just tests that json fields that are not in InventorySetup are ignored during deserialization. Only relevant
	 * during the transition period where someone might still be using an old version of Inventory Setups that can't
	 * handle the layout string.
	 */
	@Test
	public void testThatBankTagLayoutWontBreakOnOldVersions()
	{
//		InventorySetup setup = new InventorySetup(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), new HashMap<>(), "testname", "", null, null, false, false, false, 1, 1);
//		InventorySetup imported = inventorySetupsPlugin.deserializeSetup(exportedSetup);
//		assertEquals(imported, setup);
	}

	private void setBankTagLayout(InventorySetup example, String layout)
	{
		when(configManager.getConfiguration(
				InventorySetupsPlugin.BANK_TAG_LAYOUTS_PLUGIN_CONFIG_GROUP,
				InventorySetupsPlugin.BANK_TAG_LAYOUTS_PLUGIN_INVENTORY_SETUPS_LAYOUT_CONFIG_KEY_PREFIX + example.getName()
		)).thenReturn(layout);
	}

	private OngoingStubbing<String> enableBankTagLayoutsPlugin(boolean enable)
	{
		return when(configManager.getConfiguration("runelite", InventorySetupsPlugin.BANK_TAG_LAYOUTS_ENABLED_KEY)).thenReturn(enable ? "true" : null);
	}
}
