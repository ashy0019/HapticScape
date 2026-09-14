package com.ashy0019.hapticscape.integration.desktop;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WasapiAudioEndpointCatalogTest
{
	@Test
	public void expandsRegistryGuidIntoAWasapiRenderEndpointId()
	{
		assertEquals(
			"{0.0.0.00000000}.{A1B2C3D4-E5F6-47A8-9012-3456789ABCDE}",
			WasapiAudioEndpointCatalog.windowsRenderEndpointId(
				"{A1B2C3D4-E5F6-47A8-9012-3456789ABCDE}"
			)
		);
	}

	@Test
	public void preservesAnAlreadyCanonicalWasapiEndpointId()
	{
		String id = "{0.0.0.00000000}.{A1B2C3D4-E5F6-47A8-9012-3456789ABCDE}";
		assertEquals(id, WasapiAudioEndpointCatalog.windowsRenderEndpointId(id));
	}

	@Test
	public void usesThePrimaryAndSecondaryNamesWindowsDisplays()
	{
		Map<String, Object> properties = new HashMap<>();
		properties.put(
			"{A45C254E-DF1C-4EFD-8020-67D146A850E0},2",
			"Speakers"
		);
		properties.put(
			"{a45c254e-df1c-4efd-8020-67d146a850e0},14",
			"Speakers (High Definition Audio)"
		);

		assertEquals(
			"Speakers — High Definition Audio",
			WasapiAudioEndpointCatalog.windowsDisplayName(properties)
		);
	}

	@Test
	public void fallsBackAcrossWindowsEndpointPropertyVariants()
	{
		Map<String, Object> properties = new HashMap<>();
		properties.put(
			"{a45c254e-df1c-4efd-8020-67d146a850e0},2",
			"Display monitor"
		);
		properties.put(
			"{026e516e-b814-414b-83cd-856d6fef4822},2",
			"Display audio"
		);

		assertEquals(
			"Display monitor — Display audio",
			WasapiAudioEndpointCatalog.windowsDisplayName(properties)
		);
	}
}
