package name.abuchen.portfolio.ui.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.swt.widgets.Display;

import name.abuchen.portfolio.ui.Images;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.PortfolioPlugin;
import name.abuchen.portfolio.util.ColorConversion;

/**
 * ValueColorScheme provides color schemes for positive/negative value
 * indicators. Users can choose between different schemes (e.g., green/red or
 * blue/orange) for improved accessibility.
 */
public final class ValueColorScheme
{
    public static final String STANDARD_SCHEME = "standard"; //$NON-NLS-1$
    public static final String CUSTOM_SCHEME = "custom"; //$NON-NLS-1$

    public static final String DEFAULT_LIGHT_CUSTOM_POSITIVE = "#007A00"; //$NON-NLS-1$
    public static final String DEFAULT_LIGHT_CUSTOM_NEGATIVE = "#C62828"; //$NON-NLS-1$
    public static final String DEFAULT_DARK_CUSTOM_POSITIVE = "#00E000"; //$NON-NLS-1$
    public static final String DEFAULT_DARK_CUSTOM_NEGATIVE = "#FF6B6B"; //$NON-NLS-1$

    private static final List<ValueColorScheme> schemes;
    private static ValueColorScheme currentScheme;
    private static ValueColorScheme customScheme;

    /**
     * Checks whether the dark theme is currently active.
     *
     * @return {@code true} if dark theme is active, {@code false} otherwise
     */
    public static boolean isDarkTheme()
    {
        try
        {
            Color bg = Colors.theme().defaultBackground();
            if (bg != null)
            {
                RGB rgb = bg.getRGB();
                double luminance = (0.2126 * rgb.red + 0.7152 * rgb.green + 0.0722 * rgb.blue) / 255.0;
                return luminance < 0.5;
            }
        }
        catch (Exception ignored)
        {
        }

        try
        {
            return Display.isSystemDarkTheme();
        }
        catch (Exception ignored)
        {
            return false;
        }
    }

    /**
     * Returns the default positive color (hex string) for the custom scheme,
     * chosen according to the active theme to satisfy WCAG contrast requirements.
     *
     * @return hex color code (e.g. {@code #007A00} or {@code #00E000})
     */
    public static String getDefaultCustomPositiveHex()
    {
        return isDarkTheme() ? DEFAULT_DARK_CUSTOM_POSITIVE : DEFAULT_LIGHT_CUSTOM_POSITIVE;
    }

    /**
     * Returns the default negative color (hex string) for the custom scheme,
     * chosen according to the active theme to satisfy WCAG contrast requirements.
     *
     * @return hex color code (e.g. {@code #C62828} or {@code #FF6B6B})
     */
    public static String getDefaultCustomNegativeHex()
    {
        return isDarkTheme() ? DEFAULT_DARK_CUSTOM_NEGATIVE : DEFAULT_LIGHT_CUSTOM_NEGATIVE;
    }

    /**
     * Returns the default positive RGB color for the custom scheme matching the active theme.
     *
     * @return the positive {@link RGB} color
     */
    public static RGB getDefaultCustomPositiveRGB()
    {
        return ColorConversion.hex2RGB(getDefaultCustomPositiveHex());
    }

    /**
     * Returns the default negative RGB color for the custom scheme matching the active theme.
     *
     * @return the negative {@link RGB} color
     */
    public static RGB getDefaultCustomNegativeRGB()
    {
        return ColorConversion.hex2RGB(getDefaultCustomNegativeHex());
    }

    static
    {
        // initialize value color scheme with values in case CSS cannot be read

        var standard = new ValueColorScheme(STANDARD_SCHEME, Messages.ValueColorSchemeStandard);
        standard.setPositiveForeground(Colors.DARK_GREEN.getRGBA());
        standard.setNegativeForeground(Colors.DARK_RED.getRGBA());
        standard.setUpArrow("scheme/standard/light/up_arrow.svg"); //$NON-NLS-1$
        standard.setDownArrow("scheme/standard/light/down_arrow.svg"); //$NON-NLS-1$

        var blueOrange = new ValueColorScheme("blueorange", Messages.ValueColorSchemeBlueOrange); //$NON-NLS-1$
        blueOrange.setPositiveForeground(ColorConversion.hex2RGBA("#0066CC")); //$NON-NLS-1$
        blueOrange.setNegativeForeground(ColorConversion.hex2RGBA("#B36E3A")); //$NON-NLS-1$
        blueOrange.setUpArrow("scheme/blueorange/light/up_arrow.svg"); //$NON-NLS-1$
        blueOrange.setDownArrow("scheme/blueorange/light/down_arrow.svg"); //$NON-NLS-1$

        var asia = new ValueColorScheme("asia", Messages.ValueColorSchemeAsia); //$NON-NLS-1$
        asia.setPositiveForeground(Colors.DARK_RED.getRGBA());
        asia.setNegativeForeground(Colors.DARK_GREEN.getRGBA());
        asia.setUpArrow("scheme/asia/light/up_arrow.svg"); //$NON-NLS-1$
        asia.setDownArrow("scheme/asia/light/down_arrow.svg"); //$NON-NLS-1$

        var vivid = new ValueColorScheme("vivid", Messages.ValueColorSchemeVividContrast); //$NON-NLS-1$
        vivid.setPositiveForeground(ColorConversion.hex2RGBA("#00E000")); //$NON-NLS-1$
        vivid.setNegativeForeground(ColorConversion.hex2RGBA("#FE2B05")); //$NON-NLS-1$
        vivid.setUpArrow("scheme/vivid/light/up_arrow.svg"); //$NON-NLS-1$
        vivid.setDownArrow("scheme/vivid/light/down_arrow.svg"); //$NON-NLS-1$

        var custom = new ValueColorScheme(CUSTOM_SCHEME, Messages.ValueColorSchemeCustom);
        RGB defaultPos = getDefaultCustomPositiveRGB();
        RGB defaultNeg = getDefaultCustomNegativeRGB();
        custom.setPositiveForeground(defaultPos);
        custom.setNegativeForeground(defaultNeg);
        custom.setUpArrowColor(defaultPos);
        custom.setDownArrowColor(defaultNeg);
        custom.setUpArrow("scheme/standard/light/up_arrow.svg"); //$NON-NLS-1$
        custom.setDownArrow("scheme/standard/light/down_arrow.svg"); //$NON-NLS-1$
        customScheme = custom;

        schemes = List.of(standard, blueOrange, asia, vivid, custom);
        currentScheme = standard;
    }

    private static final int MAX_CACHED_ARROW_IMAGES = 32;
    private static final Map<String, Image> ARROW_CACHE = new LinkedHashMap<>(MAX_CACHED_ARROW_IMAGES, 0.75f, true);

    private final String identifier;
    private final String name;

    private Color positiveForeground;
    private Color negativeForeground;
    private RGB upArrowColor;
    private RGB downArrowColor;
    private Image upArrow;
    private Image downArrow;

    private ValueColorScheme(String identifier, String name)
    {
        this.identifier = identifier;
        this.name = name;
    }

    /**
     * Returns the unique identifier for this color scheme.
     *
     * @return the scheme identifier
     */
    public String getIdentifier()
    {
        return identifier;
    }

    /**
     * Returns the human-readable display name for this color scheme.
     *
     * @return the scheme name
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns the foreground color for positive values.
     *
     * @return the positive foreground {@link Color}
     */
    public Color positiveForeground()
    {
        return positiveForeground;
    }

    /**
     * Sets the positive foreground color from RGBA.
     *
     * @param color the RGBA color
     */
    public void setPositiveForeground(RGBA color)
    {
        this.positiveForeground = Colors.getColor(color.rgb);
    }

    /**
     * Sets the positive foreground color from RGB.
     *
     * @param color the RGB color
     */
    public void setPositiveForeground(RGB color)
    {
        this.positiveForeground = Colors.getColor(color);
    }

    /**
     * Returns the foreground color for negative values.
     *
     * @return the negative foreground {@link Color}
     */
    public Color negativeForeground()
    {
        return negativeForeground;
    }

    /**
     * Sets the negative foreground color from RGBA.
     *
     * @param color the RGBA color
     */
    public void setNegativeForeground(RGBA color)
    {
        this.negativeForeground = Colors.getColor(color.rgb);
    }

    /**
     * Sets the negative foreground color from RGB.
     *
     * @param color the RGB color
     */
    public void setNegativeForeground(RGB color)
    {
        this.negativeForeground = Colors.getColor(color);
    }

    /**
     * Checks whether this color scheme is the customizable user scheme.
     *
     * @return {@code true} if this is the custom color scheme
     */
    public boolean isCustom()
    {
        return CUSTOM_SCHEME.equals(identifier);
    }

    /**
     * Returns the singleton custom value color scheme instance.
     *
     * @return the custom value color scheme
     */
    public static ValueColorScheme getCustomScheme()
    {
        return customScheme;
    }

    /**
     * Returns the indicator image for rising values.
     *
     * @return the up arrow image
     */
    public Image upArrow()
    {
        if (isCustom() && upArrowColor != null)
        {
            Image customArrow = getArrowImage(upArrowColor, true);
            if (customArrow != null)
                return customArrow;
        }
        return upArrow;
    }

    /**
     * Sets the icon path for the rising value indicator.
     *
     * @param icon the relative bundle path to the icon
     */
    public void setUpArrow(String icon)
    {
        this.upArrow = Images.resolve(icon, false);
    }

    /**
     * Returns the indicator image for falling values.
     *
     * @return the down arrow image
     */
    public Image downArrow()
    {
        if (isCustom() && downArrowColor != null)
        {
            Image customArrow = getArrowImage(downArrowColor, false);
            if (customArrow != null)
                return customArrow;
        }
        return downArrow;
    }

    /**
     * Sets the icon path for the falling value indicator.
     *
     * @param icon the relative bundle path to the icon
     */
    public void setDownArrow(String icon)
    {
        this.downArrow = Images.resolve(icon, false);
    }

    /**
     * Returns the custom color configured for the rising value indicator arrow.
     *
     * @return the RGB color of the up arrow, or {@code null}
     */
    public RGB getUpArrowColor()
    {
        return upArrowColor;
    }

    /**
     * Sets the custom color for the rising value indicator arrow.
     *
     * @param rgb the RGB color
     */
    public void setUpArrowColor(RGB rgb)
    {
        this.upArrowColor = rgb;
    }

    /**
     * Returns the custom color configured for the falling value indicator arrow.
     *
     * @return the RGB color of the down arrow, or {@code null}
     */
    public RGB getDownArrowColor()
    {
        return downArrowColor;
    }

    /**
     * Sets the custom color for the falling value indicator arrow.
     *
     * @param rgb the RGB color
     */
    public void setDownArrowColor(RGB rgb)
    {
        this.downArrowColor = rgb;
    }

    /**
     * Retrieves or generates a directional indicator arrow image for the given color.
     *
     * @param rgb the color for the arrow
     * @param up {@code true} for an up arrow, {@code false} for a down arrow
     * @return the matching arrow {@link Image}, or standard fallback if rendering fails
     */
    public static Image getArrowImage(RGB rgb, boolean up)
    {
        if (rgb == null)
            return null;

        String hex = ColorConversion.toHex(rgb);
        String key = (up ? "up_" : "down_") + hex; //$NON-NLS-1$ //$NON-NLS-2$

        Image image = ARROW_CACHE.get(key);
        if (image != null && !image.isDisposed())
            return image;

        try
        {
            ImageDescriptor descriptor = ImageDescriptor
                            .createFromImageDataProvider(zoom -> createArrowImageData(rgb, up, zoom));
            image = descriptor.createImage();
            if (image != null)
                cacheArrowImage(key, image);
            return image;
        }
        catch (Exception e)
        {
            PortfolioPlugin.log(e);
            return up ? Images.resolve("scheme/standard/light/up_arrow.svg", false) //$NON-NLS-1$
                            : Images.resolve("scheme/standard/light/down_arrow.svg", false); //$NON-NLS-1$
        }
    }

    /**
     * Generates an in-memory {@link ImageData} buffer containing an anti-aliased indicator arrow.
     *
     * @param rgb the arrow color
     * @param up {@code true} for up arrow, {@code false} for down arrow
     * @param zoom the DPI zoom level (e.g. 100, 150, 200)
     * @return the generated {@link ImageData}
     */
    private static ImageData createArrowImageData(RGB rgb, boolean up, int zoom)
    {
        int size = Math.max(1, Math.round(16f * zoom / 100f));
        PaletteData palette = new PaletteData(0xFF0000, 0x00FF00, 0x0000FF);
        ImageData imageData = new ImageData(size, size, 32, palette);
        byte[] alpha = new byte[size * size];
        int pixel = palette.getPixel(rgb);

        // Scaled triangle vertices matching SVG viewBox [-6, -6, 36, 36]
        // Up arrow: (12, 3), (24, 21), (0, 21)
        // Down arrow: (12, 21), (0, 3), (24, 3)
        double scale = (double) size / 36.0;
        double ax, ay, bx, by, cx, cy;
        if (up)
        {
            ax = 18.0 * scale;
            ay = 9.0 * scale;
            bx = 30.0 * scale;
            by = 27.0 * scale;
            cx = 6.0 * scale;
            cy = 27.0 * scale;
        }
        else
        {
            ax = 18.0 * scale;
            ay = 27.0 * scale;
            bx = 6.0 * scale;
            by = 9.0 * scale;
            cx = 30.0 * scale;
            cy = 9.0 * scale;
        }

        int subSteps = 4;
        double subStep = 1.0 / subSteps;
        double subOffset = subStep / 2.0;

        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                int insideCount = 0;
                for (int sy = 0; sy < subSteps; sy++)
                {
                    double py = y + sy * subStep + subOffset;
                    for (int sx = 0; sx < subSteps; sx++)
                    {
                        double px = x + sx * subStep + subOffset;
                        if (isPointInTriangle(px, py, ax, ay, bx, by, cx, cy))
                            insideCount++;
                    }
                }

                if (insideCount > 0)
                {
                    imageData.setPixel(x, y, pixel);
                    alpha[y * size + x] = (byte) ((insideCount * 255) / (subSteps * subSteps));
                }
            }
        }
        imageData.alphaData = alpha;
        return imageData;
    }

    /**
     * Determines whether a point is inside a triangle using edge equations.
     *
     * @param px test point X coordinate
     * @param py test point Y coordinate
     * @param ax vertex A X coordinate
     * @param ay vertex A Y coordinate
     * @param bx vertex B X coordinate
     * @param by vertex B Y coordinate
     * @param cx vertex C X coordinate
     * @param cy vertex C Y coordinate
     * @return {@code true} if point is inside or on the boundary of the triangle
     */
    private static boolean isPointInTriangle(double px, double py, double ax, double ay, double bx, double by,
                    double cx, double cy)
    {
        double d1 = (px - ax) * (by - ay) - (py - ay) * (bx - ax);
        double d2 = (px - bx) * (cy - by) - (py - by) * (cx - bx);
        double d3 = (px - cx) * (ay - cy) - (py - cy) * (ax - cx);

        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);

        return !(hasNeg && hasPos);
    }

    private static final Map<String, Integer> PINNED_KEYS = new HashMap<>();

    /**
     * Pins an arrow color to prevent its cached image from being evicted and disposed.
     *
     * @param rgb the arrow RGB color
     * @param up {@code true} for up arrow, {@code false} for down arrow
     */
    public static synchronized void pinArrowColor(RGB rgb, boolean up)
    {
        if (rgb == null)
            return;
        String key = (up ? "up_" : "down_") + ColorConversion.toHex(rgb); //$NON-NLS-1$ //$NON-NLS-2$
        PINNED_KEYS.merge(key, 1, Integer::sum);
    }

    /**
     * Unpins an arrow color when no longer displayed by a widget.
     *
     * @param rgb the arrow RGB color
     * @param up {@code true} for up arrow, {@code false} for down arrow
     */
    public static synchronized void unpinArrowColor(RGB rgb, boolean up)
    {
        if (rgb == null)
            return;
        String key = (up ? "up_" : "down_") + ColorConversion.toHex(rgb); //$NON-NLS-1$ //$NON-NLS-2$
        PINNED_KEYS.computeIfPresent(key, (k, count) -> count > 1 ? count - 1 : null);
    }

    /**
     * Stores an arrow image in cache and disposes evicted images if capacity is exceeded.
     *
     * @param key the cache key
     * @param image the image to cache
     */
    private static synchronized void cacheArrowImage(String key, Image image)
    {
        ARROW_CACHE.put(key, image);

        Iterator<Map.Entry<String, Image>> iterator = ARROW_CACHE.entrySet().iterator();
        while (ARROW_CACHE.size() > MAX_CACHED_ARROW_IMAGES && iterator.hasNext())
        {
            Map.Entry<String, Image> entry = iterator.next();
            if (!isCurrentCustomArrow(entry.getKey()))
            {
                iterator.remove();
                if (!entry.getValue().isDisposed())
                    entry.getValue().dispose();
            }
        }
    }

    /**
     * Checks if a cache key belongs to an active or pinned arrow color.
     *
     * @param key the cache key
     * @return {@code true} if pinned or currently configured
     */
    private static synchronized boolean isCurrentCustomArrow(String key)
    {
        if (PINNED_KEYS.containsKey(key))
            return true;

        RGB upColor = customScheme.getUpArrowColor();
        RGB downColor = customScheme.getDownArrowColor();
        return (upColor != null && key.equals("up_" + ColorConversion.toHex(upColor))) //$NON-NLS-1$
                        || (downColor != null && key.equals("down_" + ColorConversion.toHex(downColor))); //$NON-NLS-1$
    }

    /**
     * Returns the currently active global value color scheme.
     *
     * @return the active value color scheme
     */
    public static ValueColorScheme current()
    {
        return currentScheme;
    }

    /**
     * Returns all registered value color schemes.
     *
     * @return unmodifiable list of available color schemes
     */
    public static List<ValueColorScheme> getAvailableSchemes()
    {
        return schemes;
    }

    /**
     * Activates the value color scheme identified by {@code schemeId}.
     *
     * @param schemeId the scheme identifier
     */
    public static void initialize(String schemeId)
    {
        if (schemeId == null)
            return;

        for (var scheme : schemes)
        {
            if (schemeId.equals(scheme.getIdentifier()))
                currentScheme = scheme;
        }
    }
}
