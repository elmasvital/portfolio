package name.abuchen.portfolio.ui.preferences;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.ColorDialog;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;

import name.abuchen.portfolio.money.Values;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.util.Colors;
import name.abuchen.portfolio.ui.util.ValueColorScheme;
import name.abuchen.portfolio.util.ColorConversion;

public class ValueColorSchemeDialog extends Dialog
{
    private RGB positiveRGB;
    private RGB positiveArrowRGB;
    private RGB negativeRGB;
    private RGB negativeArrowRGB;

    private Canvas positiveTextSwatch;
    private Canvas positiveArrowSwatch;
    private Canvas negativeTextSwatch;
    private Canvas negativeArrowSwatch;

    private Table previewTable;
    private TableItem itemApple;
    private TableItem itemMicrosoft;
    private TableItem itemNvidia;
    private TableItem itemTesla;

    /**
     * Creates a new dialog for configuring custom value colors.
     *
     * @param parentShell the parent shell
     * @param positiveRGB the initial positive text RGB color
     * @param positiveArrowRGB the initial positive arrow RGB color
     * @param negativeRGB the initial negative text RGB color
     * @param negativeArrowRGB the initial negative arrow RGB color
     */
    public ValueColorSchemeDialog(Shell parentShell, RGB positiveRGB, RGB positiveArrowRGB, RGB negativeRGB,
                    RGB negativeArrowRGB)
    {
        super(parentShell);
        RGB defaultPos = ValueColorScheme.getDefaultCustomPositiveRGB();
        RGB defaultNeg = ValueColorScheme.getDefaultCustomNegativeRGB();
        this.positiveRGB = positiveRGB != null ? positiveRGB : defaultPos;
        this.positiveArrowRGB = positiveArrowRGB != null ? positiveArrowRGB : this.positiveRGB;
        this.negativeRGB = negativeRGB != null ? negativeRGB : defaultNeg;
        this.negativeArrowRGB = negativeArrowRGB != null ? negativeArrowRGB : this.negativeRGB;

        ValueColorScheme.pinArrowColor(this.positiveArrowRGB, true);
        ValueColorScheme.pinArrowColor(this.negativeArrowRGB, false);
    }

    /**
     * Configures the dialog shell properties such as window title.
     *
     * @param shell the dialog shell
     */
    @Override
    protected void configureShell(Shell shell)
    {
        super.configureShell(shell);
        shell.setText(Messages.ValueColorSchemeDialogTitle);
    }

    /**
     * Specifies that the dialog shell is resizable.
     *
     * @return {@code true}
     */
    @Override
    protected boolean isResizable()
    {
        return true;
    }

    /**
     * Returns the initial size for the dialog window.
     *
     * @return the initial size as a {@link Point}
     */
    @Override
    protected Point getInitialSize()
    {
        return new Point(640, 520);
    }

    /**
     * Creates and lays out the controls within the dialog area.
     *
     * @param parent the parent composite
     * @return the root control of the dialog area
     */
    @Override
    protected Control createDialogArea(Composite parent)
    {
        Composite container = (Composite) super.createDialogArea(parent);
        GridLayoutFactory.fillDefaults().margins(15, 10).spacing(10, 10).applyTo(container);

        // Color pickers: two groups side by side
        Composite groupsComposite = new Composite(container, SWT.NONE);
        GridLayoutFactory.fillDefaults().numColumns(2).equalWidth(true).spacing(15, 10).applyTo(groupsComposite);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(groupsComposite);

        // Group 1: Rising values
        Group upGroup = new Group(groupsComposite, SWT.NONE);
        upGroup.setText(Messages.ValueColorSchemeDialogUpValues);
        GridLayoutFactory.fillDefaults().numColumns(3).margins(10, 10).spacing(10, 8).applyTo(upGroup);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(upGroup);

        Label lblPosText = new Label(upGroup, SWT.NONE);
        lblPosText.setText(Messages.ValueColorSchemeDialogTextColor);
        GridDataFactory.swtDefaults().align(SWT.BEGINNING, SWT.CENTER).applyTo(lblPosText);
        positiveTextSwatch = createColorSwatch(upGroup, () -> positiveRGB, () -> chooseColor(positiveRGB, rgb -> positiveRGB = rgb));
        createColorButton(upGroup, () -> chooseColor(positiveRGB, rgb -> positiveRGB = rgb));

        Label lblPosArrow = new Label(upGroup, SWT.NONE);
        lblPosArrow.setText(Messages.ValueColorSchemeDialogArrowColor);
        GridDataFactory.swtDefaults().align(SWT.BEGINNING, SWT.CENTER).applyTo(lblPosArrow);
        positiveArrowSwatch = createArrowSwatch(upGroup, () -> ValueColorScheme.getArrowImage(positiveArrowRGB, true), () -> chooseColor(positiveArrowRGB, rgb -> {
            ValueColorScheme.unpinArrowColor(positiveArrowRGB, true);
            positiveArrowRGB = rgb;
            ValueColorScheme.pinArrowColor(positiveArrowRGB, true);
        }));
        createColorButton(upGroup, () -> chooseColor(positiveArrowRGB, rgb -> {
            ValueColorScheme.unpinArrowColor(positiveArrowRGB, true);
            positiveArrowRGB = rgb;
            ValueColorScheme.pinArrowColor(positiveArrowRGB, true);
        }));

        // Group 2: Falling values
        Group downGroup = new Group(groupsComposite, SWT.NONE);
        downGroup.setText(Messages.ValueColorSchemeDialogDownValues);
        GridLayoutFactory.fillDefaults().numColumns(3).margins(10, 10).spacing(10, 8).applyTo(downGroup);
        GridDataFactory.fillDefaults().grab(true, false).applyTo(downGroup);

        Label lblNegText = new Label(downGroup, SWT.NONE);
        lblNegText.setText(Messages.ValueColorSchemeDialogTextColor);
        GridDataFactory.swtDefaults().align(SWT.BEGINNING, SWT.CENTER).applyTo(lblNegText);
        negativeTextSwatch = createColorSwatch(downGroup, () -> negativeRGB, () -> chooseColor(negativeRGB, rgb -> negativeRGB = rgb));
        createColorButton(downGroup, () -> chooseColor(negativeRGB, rgb -> negativeRGB = rgb));

        Label lblNegArrow = new Label(downGroup, SWT.NONE);
        lblNegArrow.setText(Messages.ValueColorSchemeDialogArrowColor);
        GridDataFactory.swtDefaults().align(SWT.BEGINNING, SWT.CENTER).applyTo(lblNegArrow);
        negativeArrowSwatch = createArrowSwatch(downGroup, () -> ValueColorScheme.getArrowImage(negativeArrowRGB, false), () -> chooseColor(negativeArrowRGB, rgb -> {
            ValueColorScheme.unpinArrowColor(negativeArrowRGB, false);
            negativeArrowRGB = rgb;
            ValueColorScheme.pinArrowColor(negativeArrowRGB, false);
        }));
        createColorButton(downGroup, () -> chooseColor(negativeArrowRGB, rgb -> {
            ValueColorScheme.unpinArrowColor(negativeArrowRGB, false);
            negativeArrowRGB = rgb;
            ValueColorScheme.pinArrowColor(negativeArrowRGB, false);
        }));

        // Group 3: Preview table
        Group previewGroup = new Group(container, SWT.NONE);
        previewGroup.setText(Messages.ValueColorSchemeDialogPreview);
        GridLayoutFactory.fillDefaults().margins(10, 10).applyTo(previewGroup);
        GridDataFactory.fillDefaults().grab(true, true).applyTo(previewGroup);

        previewTable = new Table(previewGroup, SWT.BORDER | SWT.FULL_SELECTION | SWT.NO_SCROLL);
        previewTable.setHeaderVisible(true);
        previewTable.setLinesVisible(true);
        GridDataFactory.fillDefaults().grab(true, true).hint(480, 140).applyTo(previewTable);

        TableColumn colAsset = new TableColumn(previewTable, SWT.LEFT);
        colAsset.setText(Messages.ValueColorSchemeDialogColAsset);
        colAsset.setWidth(140);

        TableColumn colPrice = new TableColumn(previewTable, SWT.RIGHT);
        colPrice.setText(Messages.ValueColorSchemeDialogColPrice);
        colPrice.setWidth(95);

        TableColumn colChange = new TableColumn(previewTable, SWT.RIGHT);
        colChange.setText(Messages.ValueColorSchemeDialogColChange);
        colChange.setWidth(115);

        TableColumn colGainLoss = new TableColumn(previewTable, SWT.RIGHT);
        colGainLoss.setText(Messages.ValueColorSchemeDialogColGainLoss);
        colGainLoss.setWidth(120);

        itemApple = new TableItem(previewTable, SWT.NONE);
        itemApple.setText(0, "Apple Inc. (AAPL)"); //$NON-NLS-1$
        itemApple.setText(1, Values.Amount.format(Values.Amount.factorize(182.50)));
        itemApple.setText(2, Values.PercentWithSign.format(0.0235));
        itemApple.setText(3, "+" + Values.Amount.format(Values.Amount.factorize(420.50))); //$NON-NLS-1$

        itemMicrosoft = new TableItem(previewTable, SWT.NONE);
        itemMicrosoft.setText(0, "Microsoft (MSFT)"); //$NON-NLS-1$
        itemMicrosoft.setText(1, Values.Amount.format(Values.Amount.factorize(415.20)));
        itemMicrosoft.setText(2, Values.PercentWithSign.format(-0.0115));
        itemMicrosoft.setText(3, Values.Amount.format(Values.Amount.factorize(-125.30)));

        itemNvidia = new TableItem(previewTable, SWT.NONE);
        itemNvidia.setText(0, "NVIDIA (NVDA)"); //$NON-NLS-1$
        itemNvidia.setText(1, Values.Amount.format(Values.Amount.factorize(120.40)));
        itemNvidia.setText(2, Values.PercentWithSign.format(0.0510));
        itemNvidia.setText(3, "+" + Values.Amount.format(Values.Amount.factorize(1850.00))); //$NON-NLS-1$

        itemTesla = new TableItem(previewTable, SWT.NONE);
        itemTesla.setText(0, "Tesla Inc. (TSLA)"); //$NON-NLS-1$
        itemTesla.setText(1, Values.Amount.format(Values.Amount.factorize(195.80)));
        itemTesla.setText(2, Values.PercentWithSign.format(-0.0340));
        itemTesla.setText(3, Values.Amount.format(Values.Amount.factorize(-340.00)));

        updatePreview();

        return container;
    }

    /**
     * Creates a clickable color swatch canvas displaying the given RGB color.
     *
     * @param parent the parent composite
     * @param rgbSupplier supplier returning the current RGB color to display
     * @param onClick callback invoked when the swatch is clicked
     * @return the created {@link Canvas}
     */
    private Canvas createColorSwatch(Composite parent, Supplier<RGB> rgbSupplier, Runnable onClick)
    {
        Canvas swatch = new Canvas(parent, SWT.BORDER);
        GridDataFactory.swtDefaults().hint(36, 20).applyTo(swatch);
        swatch.setCursor(parent.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
        swatch.addPaintListener(e -> {
            RGB rgb = rgbSupplier.get();
            if (rgb != null)
            {
                e.gc.setBackground(Colors.getColor(rgb));
                Rectangle clientArea = swatch.getClientArea();
                e.gc.fillRectangle(clientArea);
            }
        });
        swatch.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseDown(MouseEvent e)
            {
                onClick.run();
            }
        });
        return swatch;
    }

    /**
     * Creates a clickable arrow swatch canvas displaying the directional indicator icon.
     *
     * @param parent the parent composite
     * @param imageSupplier supplier returning the current arrow {@link Image} to display
     * @param onClick callback invoked when the swatch is clicked
     * @return the created {@link Canvas}
     */
    private Canvas createArrowSwatch(Composite parent, Supplier<Image> imageSupplier, Runnable onClick)
    {
        Canvas swatch = new Canvas(parent, SWT.BORDER);
        GridDataFactory.swtDefaults().hint(36, 20).applyTo(swatch);
        swatch.setCursor(parent.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
        swatch.addPaintListener(e -> {
            Color bg = Colors.theme().defaultBackground();
            e.gc.setBackground(bg);
            Rectangle clientArea = swatch.getClientArea();
            e.gc.fillRectangle(clientArea);

            Image img = imageSupplier.get();
            if (img != null)
            {
                int imgW = img.getBounds().width;
                int imgH = img.getBounds().height;
                int x = (clientArea.width - imgW) / 2;
                int y = (clientArea.height - imgH) / 2;
                e.gc.drawImage(img, x, y);
            }
        });
        swatch.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseDown(MouseEvent e)
            {
                onClick.run();
            }
        });
        return swatch;
    }

    /**
     * Creates a push button to open the color selection dialog.
     *
     * @param parent the parent composite
     * @param onClick callback invoked when the button is clicked
     * @return the created {@link Button}
     */
    private Button createColorButton(Composite parent, Runnable onClick)
    {
        Button btn = new Button(parent, SWT.PUSH);
        btn.setText(Messages.ValueColorSchemeDialogChooseColor);
        btn.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> onClick.run()));
        return btn;
    }

    /**
     * Opens the native color dialog to choose a new color.
     *
     * @param currentRGB the initial color in the picker
     * @param onSelected consumer invoked with the newly selected {@link RGB}
     */
    private void chooseColor(RGB currentRGB, Consumer<RGB> onSelected)
    {
        ColorDialog dialog = new ColorDialog(getShell());
        dialog.setRGB(currentRGB);
        RGB result = dialog.open();
        if (result != null)
        {
            onSelected.accept(result);
            updatePreview();
        }
    }

    /**
     * Redraws all swatches and updates the sample rows in the preview table.
     */
    private void updatePreview()
    {
        Color themeBg = Colors.theme().defaultBackground();
        Color themeFg = Colors.theme().defaultForeground();

        Color posColor = Colors.getColor(positiveRGB);
        Color negColor = Colors.getColor(negativeRGB);

        Image posArrow = ValueColorScheme.getArrowImage(positiveArrowRGB, true);
        Image negArrow = ValueColorScheme.getArrowImage(negativeArrowRGB, false);

        if (positiveTextSwatch != null && !positiveTextSwatch.isDisposed())
            positiveTextSwatch.redraw();
        if (positiveArrowSwatch != null && !positiveArrowSwatch.isDisposed())
            positiveArrowSwatch.redraw();
        if (negativeTextSwatch != null && !negativeTextSwatch.isDisposed())
            negativeTextSwatch.redraw();
        if (negativeArrowSwatch != null && !negativeArrowSwatch.isDisposed())
            negativeArrowSwatch.redraw();

        if (previewTable != null && !previewTable.isDisposed())
        {
            previewTable.setBackground(themeBg);
            previewTable.setForeground(themeFg);

            // Apple (positive)
            itemApple.setForeground(0, themeFg);
            itemApple.setForeground(1, themeFg);
            itemApple.setImage(2, posArrow);
            itemApple.setForeground(2, posColor);
            itemApple.setForeground(3, posColor);

            // Microsoft (negative)
            itemMicrosoft.setForeground(0, themeFg);
            itemMicrosoft.setForeground(1, themeFg);
            itemMicrosoft.setImage(2, negArrow);
            itemMicrosoft.setForeground(2, negColor);
            itemMicrosoft.setForeground(3, negColor);

            // NVIDIA (positive)
            itemNvidia.setForeground(0, themeFg);
            itemNvidia.setForeground(1, themeFg);
            itemNvidia.setImage(2, posArrow);
            itemNvidia.setForeground(2, posColor);
            itemNvidia.setForeground(3, posColor);

            // Tesla (negative)
            itemTesla.setForeground(0, themeFg);
            itemTesla.setForeground(1, themeFg);
            itemTesla.setImage(2, negArrow);
            itemTesla.setForeground(2, negColor);
            itemTesla.setForeground(3, negColor);

            previewTable.redraw();
        }
    }

    /**
     * Resets all colors to their accessible defaults.
     */
    private void resetDefaults()
    {
        ValueColorScheme.unpinArrowColor(positiveArrowRGB, true);
        ValueColorScheme.unpinArrowColor(negativeArrowRGB, false);
        positiveRGB = ValueColorScheme.getDefaultCustomPositiveRGB();
        positiveArrowRGB = positiveRGB;
        negativeRGB = ValueColorScheme.getDefaultCustomNegativeRGB();
        negativeArrowRGB = negativeRGB;
        ValueColorScheme.pinArrowColor(positiveArrowRGB, true);
        ValueColorScheme.pinArrowColor(negativeArrowRGB, false);
        updatePreview();
    }

    /**
     * Closes the dialog and releases pinned arrow color resources.
     *
     * @return {@code true} if shell closed successfully
     */
    @Override
    public boolean close()
    {
        ValueColorScheme.unpinArrowColor(positiveArrowRGB, true);
        ValueColorScheme.unpinArrowColor(negativeArrowRGB, false);
        return super.close();
    }

    /**
     * Creates custom buttons for the button bar, including Reset Defaults, OK, and Cancel.
     *
     * @param parent the button bar composite
     */
    @Override
    protected void createButtonsForButtonBar(Composite parent)
    {
        Button btnDefaults = createButton(parent, IDialogConstants.CLIENT_ID,
                        Messages.ValueColorSchemeDialogResetDefaults, false);
        btnDefaults.addSelectionListener(SelectionListener.widgetSelectedAdapter(e -> resetDefaults()));
        GridDataFactory.swtDefaults().grab(true, false).align(SWT.BEGINNING, SWT.CENTER).applyTo(btnDefaults);

        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }

    /**
     * Handles button press events, including the Reset Defaults button.
     *
     * @param buttonId the identifier of the pressed button
     */
    @Override
    protected void buttonPressed(int buttonId)
    {
        if (buttonId == IDialogConstants.CLIENT_ID)
        {
            resetDefaults();
            return;
        }
        super.buttonPressed(buttonId);
    }

    /**
     * Returns the selected positive text color.
     *
     * @return the positive text {@link RGB}
     */
    public RGB getPositiveRGB()
    {
        return positiveRGB;
    }

    /**
     * Returns the selected positive arrow color.
     *
     * @return the positive arrow {@link RGB}
     */
    public RGB getPositiveArrowRGB()
    {
        return positiveArrowRGB;
    }

    /**
     * Returns the selected negative text color.
     *
     * @return the negative text {@link RGB}
     */
    public RGB getNegativeRGB()
    {
        return negativeRGB;
    }

    /**
     * Returns the selected negative arrow color.
     *
     * @return the negative arrow {@link RGB}
     */
    public RGB getNegativeArrowRGB()
    {
        return negativeArrowRGB;
    }
}
