package name.abuchen.portfolio.ui.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;
import org.junit.After;
import org.junit.Test;

@SuppressWarnings("nls")
public class ValueColorSchemeTest
{
    /**
     * Resets active scheme after each test run.
     */
    @After
    public void tearDown()
    {
        ValueColorScheme.initialize(ValueColorScheme.STANDARD_SCHEME);
    }

    /**
     * Tests available color schemes including the custom scheme.
     */
    @Test
    public void testAvailableSchemes()
    {
        var schemes = ValueColorScheme.getAvailableSchemes();
        assertThat(schemes.size(), is(5));

        var ids = schemes.stream().map(ValueColorScheme::getIdentifier).toList();
        assertTrue(ids.contains(ValueColorScheme.STANDARD_SCHEME));
        assertTrue(ids.contains("blueorange"));
        assertTrue(ids.contains("asia"));
        assertTrue(ids.contains("vivid"));
        assertTrue(ids.contains(ValueColorScheme.CUSTOM_SCHEME));
    }

    /**
     * Tests custom scheme default properties.
     */
    @Test
    public void testCustomSchemeProperties()
    {
        var custom = ValueColorScheme.getCustomScheme();
        assertThat(custom, is(notNullValue()));
        assertTrue(custom.isCustom());
        assertThat(custom.getIdentifier(), is(ValueColorScheme.CUSTOM_SCHEME));
    }

    /**
     * Tests that default custom colors have valid hex and RGB formats and satisfy accessibility standards.
     */
    @Test
    public void testAccessibleThemeDefaults()
    {
        assertThat(ValueColorScheme.DEFAULT_LIGHT_CUSTOM_POSITIVE, is("#007A00"));
        assertThat(ValueColorScheme.DEFAULT_LIGHT_CUSTOM_NEGATIVE, is("#C62828"));
        assertThat(ValueColorScheme.DEFAULT_DARK_CUSTOM_POSITIVE, is("#00E000"));
        assertThat(ValueColorScheme.DEFAULT_DARK_CUSTOM_NEGATIVE, is("#FF6B6B"));

        assertThat(ValueColorScheme.getDefaultCustomPositiveHex(), is(notNullValue()));
        assertThat(ValueColorScheme.getDefaultCustomNegativeHex(), is(notNullValue()));
        assertThat(ValueColorScheme.getDefaultCustomPositiveRGB(), is(notNullValue()));
        assertThat(ValueColorScheme.getDefaultCustomNegativeRGB(), is(notNullValue()));
    }

    /**
     * Tests setting and getting custom foreground colors.
     */
    @Test
    public void testSetCustomColors()
    {
        var custom = ValueColorScheme.getCustomScheme();
        RGB testPos = new RGB(10, 200, 50);
        RGB testNeg = new RGB(220, 30, 40);

        custom.setPositiveForeground(testPos);
        custom.setNegativeForeground(testNeg);

        assertThat(custom.positiveForeground().getRGB(), is(testPos));
        assertThat(custom.negativeForeground().getRGB(), is(testNeg));
    }

    /**
     * Tests setting and getting custom arrow colors.
     */
    @Test
    public void testSetCustomArrowColors()
    {
        var custom = ValueColorScheme.getCustomScheme();
        RGB testUp = new RGB(0, 255, 128);
        RGB testDown = new RGB(255, 64, 64);

        custom.setUpArrowColor(testUp);
        custom.setDownArrowColor(testDown);

        assertThat(custom.getUpArrowColor(), is(testUp));
        assertThat(custom.getDownArrowColor(), is(testDown));

        assertThat(custom.upArrow(), is(notNullValue()));
        assertThat(custom.downArrow(), is(notNullValue()));
    }

    /**
     * Tests generating indicator arrow images for arbitrary colors.
     */
    @Test
    public void testGetArrowImage()
    {
        RGB testColor = new RGB(255, 100, 0);
        var upImg = ValueColorScheme.getArrowImage(testColor, true);
        var downImg = ValueColorScheme.getArrowImage(testColor, false);

        assertThat(upImg, is(notNullValue()));
        assertThat(downImg, is(notNullValue()));
    }

    /**
     * Tests that unpinned images evicted from the cache are disposed.
     */
    @Test
    public void testArrowImageCacheDisposesOldImages()
    {
        Image firstImage = ValueColorScheme.getArrowImage(new RGB(1, 254, 1), true);

        for (int i = 2; i < 80; i++)
        {
            RGB color = new RGB(i, 255 - i, i);
            ValueColorScheme.getArrowImage(color, true);
            ValueColorScheme.getArrowImage(color, false);
        }

        assertTrue(firstImage.isDisposed());
    }

    /**
     * Tests that pinned arrow colors are protected from eviction and disposal.
     */
    @Test
    public void testPinnedArrowColorNotDisposedOnEviction()
    {
        RGB pinnedColor = new RGB(2, 253, 2);
        ValueColorScheme.pinArrowColor(pinnedColor, true);

        Image pinnedImage = ValueColorScheme.getArrowImage(pinnedColor, true);

        for (int i = 3; i < 80; i++)
        {
            RGB color = new RGB(i, 255 - i, i);
            ValueColorScheme.getArrowImage(color, true);
            ValueColorScheme.getArrowImage(color, false);
        }

        assertFalse(pinnedImage.isDisposed());

        ValueColorScheme.unpinArrowColor(pinnedColor, true);

        for (int i = 80; i < 160; i++)
        {
            RGB color = new RGB(i % 256, (255 - i) % 256, (i * 2) % 256);
            ValueColorScheme.getArrowImage(color, true);
        }

        assertTrue(pinnedImage.isDisposed());
    }

    /**
     * Tests initializing the custom scheme as current.
     */
    @Test
    public void testInitializeCustomScheme()
    {
        ValueColorScheme.initialize(ValueColorScheme.CUSTOM_SCHEME);
        assertThat(ValueColorScheme.current(), is(ValueColorScheme.getCustomScheme()));
    }
}
