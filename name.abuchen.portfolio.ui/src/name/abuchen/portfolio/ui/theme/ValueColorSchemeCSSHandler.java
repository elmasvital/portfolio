package name.abuchen.portfolio.ui.theme;

import org.eclipse.e4.ui.css.core.dom.properties.ICSSPropertyHandler;
import org.eclipse.e4.ui.css.core.engine.CSSEngine;
import org.eclipse.e4.ui.css.swt.helpers.CSSSWTColorHelper;
import org.w3c.dom.css.CSSPrimitiveValue;
import org.w3c.dom.css.CSSValue;

import name.abuchen.portfolio.ui.util.ValueColorScheme;

@SuppressWarnings("restriction")
public class ValueColorSchemeCSSHandler implements ICSSPropertyHandler
{
    /**
     * Applies a CSS property value to the target element.
     */
    @Override
    public boolean applyCSSProperty(Object element, String property, CSSValue value, String pseudo, CSSEngine engine)
                    throws Exception
    {
        if (element instanceof ValueColorSchemeElementAdapter adapter)
        {
            var scheme = adapter.getScheme();
            return applyTo(scheme, property, value);
        }

        return false;
    }

    /**
     * Applies a specific CSS property to the given value color scheme.
     *
     * @param scheme the color scheme to update
     * @param property the CSS property name
     * @param value the CSS value to set
     * @return {@code true} if the property was handled
     */
    private boolean applyTo(ValueColorScheme scheme, String property, CSSValue value)
    {
        if (scheme.isCustom() && ("positive-foreground".equals(property) || "negative-foreground".equals(property))) //$NON-NLS-1$ //$NON-NLS-2$
            return true;

        switch (property)
        {
            case "positive-foreground": //$NON-NLS-1$
                scheme.setPositiveForeground(CSSSWTColorHelper.getRGBA(value));
                return true;
            case "negative-foreground": //$NON-NLS-1$
                scheme.setNegativeForeground(CSSSWTColorHelper.getRGBA(value));
                return true;
            case "up-arrow": //$NON-NLS-1$
                var upArrow = getText(value);
                if (upArrow != null)
                    scheme.setUpArrow(upArrow);
                return true;
            case "down-arrow": //$NON-NLS-1$
                var downArrow = getText(value);
                if (downArrow != null)
                    scheme.setDownArrow(downArrow);
                return true;
            default:
                return false;
        }
    }

    /**
     * Extracts a string representation from a primitive CSS value.
     *
     * @param value the CSS value
     * @return the string value or {@code null}
     */
    private String getText(CSSValue value)
    {
        if (value.getCssValueType() != CSSValue.CSS_PRIMITIVE_VALUE)
            return null;
        var primitiveValue = (CSSPrimitiveValue) value;
        if (primitiveValue.getPrimitiveType() == CSSPrimitiveValue.CSS_STRING)
            return primitiveValue.getStringValue();
        else
            return null;
    }
}
