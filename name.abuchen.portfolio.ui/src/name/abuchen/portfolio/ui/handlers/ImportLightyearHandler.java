package name.abuchen.portfolio.ui.handlers;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Named;

import org.eclipse.e4.core.di.annotations.CanExecute;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

import name.abuchen.portfolio.datatransfer.Extractor;
import name.abuchen.portfolio.datatransfer.SecurityCache;
import name.abuchen.portfolio.datatransfer.lightyear.LightyearCSVExtractor;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.ui.Messages;
import name.abuchen.portfolio.ui.PortfolioPlugin;
import name.abuchen.portfolio.ui.UIConstants;
import name.abuchen.portfolio.ui.editor.FilePathHelper;
import name.abuchen.portfolio.ui.editor.PortfolioPart;
import name.abuchen.portfolio.ui.wizards.datatransfer.ImportExtractedItemsWizard;
import name.abuchen.portfolio.ui.wizards.datatransfer.ImportWizardDialog;

/**
 * ============================================================================
 * MANEJADOR DE INTERFAZ DE USUARIO: ImportLightyearHandler
 * ============================================================================
 * 
 * Controlador de Eclipse E4 enlazado al comando de menú:
 * "Archivo -> Importar... -> Lightyear (CSV)..."
 * 
 * FLUJO DE EJECUCIÓN:
 * 1. Comprueba que el usuario tenga un archivo de portafolio abierto y activo (@CanExecute).
 * 2. Verifica precondiciones: debe existir al menos una cuenta de efectivo y una cartera.
 * 3. Abre un diálogo nativo de selección de archivos del sistema operativo (FileDialog)
 *    filtrando por archivos CSV.
 * 4. Invoca {@link LightyearCSVExtractor} para extraer todas las transacciones.
 * 5. Sincroniza la caché de activos (SecurityCache) para detectar activos nuevos.
 * 6. Abre el asistente gráfico de importación (ImportExtractedItemsWizard) donde el
 *    usuario puede previsualizar, asignar cuentas y confirmar las operaciones.
 */
public class ImportLightyearHandler
{
    /**
     * Determina si la opción de menú debe estar habilitada o deshabilitada.
     * Solo se activa si la vista activa corresponde a un archivo de portafolio abierto.
     */
    @CanExecute
    boolean isVisible(@Named(IServiceConstants.ACTIVE_PART) MPart part)
    {
        return MenuHelper.isClientPartActive(part);
    }

    /**
     * Punto de entrada invocado por el framework de Eclipse E4 cuando el usuario
     * hace clic en el elemento de menú correspondiente.
     */
    @Execute
    public void execute(@Named(IServiceConstants.ACTIVE_PART) MPart part,
                    @Named(IServiceConstants.ACTIVE_SHELL) Shell shell)
    {
        // Obtener el portafolio activo (Client) y lanzar el proceso de importación
        MenuHelper.getActiveClient(part).ifPresent(client -> runImport(part, shell, client));
    }

    /**
     * Coordina todo el flujo de importación desde la selección de archivos
     * hasta la apertura de la ventana de diálogo de previsualización.
     */
    private void runImport(MPart part, Shell shell, Client client)
    {
        // Precondición 1: Comprobar que el portafolio tenga al menos una cuenta de efectivo
        if (client.getAccounts().isEmpty())
        {
            MessageDialog.openError(shell, Messages.LabelError, Messages.MsgErrorAccountNotExist);
            return;
        }

        // Precondición 2: Comprobar que el portafolio tenga al menos una cartera de valores
        if (client.getPortfolios().isEmpty())
        {
            MessageDialog.openError(shell, Messages.LabelError, Messages.MsgErrorPortfolioNotExist);
            return;
        }

        try
        {
            // Instanciar el extractor central de Lightyear
            var extractor = new LightyearCSVExtractor(client);

            var portfolioPart = (PortfolioPart) part.getObject();
            // Helper para recordar la última carpeta seleccionada por el usuario
            var helper = new FilePathHelper(portfolioPart, UIConstants.Preferences.CSV_IMPORT_PATH);

            // Crear el diálogo de selección de archivos (permite seleccionar múltiples CSVs)
            var fileDialog = new FileDialog(shell, SWT.OPEN | SWT.MULTI);
            fileDialog.setText(extractor.getLabel());
            fileDialog.setFilterNames(MessageFormat.format("{0} ({1})", extractor.getLabel(), "*.csv")); //$NON-NLS-1$ //$NON-NLS-2$
            fileDialog.setFilterExtensions("*.csv;*.CSV"); //$NON-NLS-1$
            fileDialog.setFilterPath(helper.getPath());
            fileDialog.open();

            String[] filenames = fileDialog.getFileNames();

            // Si el usuario canceló la selección de archivos, salir limpiamente
            if (filenames.length == 0)
                return;

            // Guardar la ruta del directorio para futuras importaciones
            helper.savePath(fileDialog.getFilterPath());

            // Convertir las rutas seleccionadas a objetos InputFile
            List<Extractor.InputFile> files = new ArrayList<>();
            for (String filename : filenames)
                files.add(new Extractor.InputFile(new File(fileDialog.getFilterPath(), filename)));

            // Caché de activos para asociar operaciones a activos existentes o crearlos si no existen
            var cache = new SecurityCache(client);
            Map<File, List<Exception>> e = new HashMap<>();

            // Extraer las transacciones de cada archivo seleccionado
            var items = new ArrayList<Extractor.Item>();
            for (var f : files)
            {
                var errors = new ArrayList<Exception>();
                items.addAll(extractor.extract(cache, f, errors));
                if (!errors.isEmpty())
                    e.put(f.getFile(), errors);
            }

            // Ejecutar post-procesado estándar (ej. ordenación cronológica)
            extractor.postProcessing(items);

            // Agrupar los resultados por extractor
            Map<Extractor, List<Extractor.Item>> result = new HashMap<>();
            result.put(extractor, items);

            // Añadir los SecurityItem que se hayan generado implícitamente por las transacciones
            cache.addMissingSecurityItems(result);

            var preferences = portfolioPart.getPreferenceStore();

            // Construir y lanzar el diálogo modal con el asistente de importación (Wizard)
            var wizard = new ImportExtractedItemsWizard(client, preferences, result, e, new HashMap<>(), portfolioPart);
            wizard.setWindowTitle(Messages.CSVImportWizardTitle);
            portfolioPart.inject(wizard);
            var dialog = new ImportWizardDialog(Display.getDefault().getActiveShell(), wizard);
            dialog.open();
        }
        catch (IllegalArgumentException e)
        {
            // Registrar la excepción en el log del plugin y notificar al usuario
            PortfolioPlugin.log(e);
            MessageDialog.openError(shell, Messages.LabelError, e.getMessage());
        }
    }
}
