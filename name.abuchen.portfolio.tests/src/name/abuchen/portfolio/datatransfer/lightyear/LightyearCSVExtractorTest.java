package name.abuchen.portfolio.datatransfer.lightyear;

import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.deposit;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.dividend;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.fee;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasAmount;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasDate;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasForexGrossValue;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasIsin;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasSecurity;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasShares;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasTaxes;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.hasTicker;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.inboundCash;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.interest;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.outboundCash;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.purchase;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.removal;
import static name.abuchen.portfolio.datatransfer.ExtractorMatchers.sale;
import static name.abuchen.portfolio.datatransfer.ExtractorTestUtilities.countAccountTransactions;
import static name.abuchen.portfolio.datatransfer.ExtractorTestUtilities.countAccountTransfers;
import static name.abuchen.portfolio.datatransfer.ExtractorTestUtilities.countBuySell;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

import org.junit.Test;

import name.abuchen.portfolio.datatransfer.Extractor.InputFile;
import name.abuchen.portfolio.datatransfer.SecurityCache;
import name.abuchen.portfolio.datatransfer.TestExtractorHelper;
import name.abuchen.portfolio.datatransfer.actions.AssertImportActions;
import name.abuchen.portfolio.model.Client;

/**
 * ============================================================================
 * SUITE DE PRUEBAS UNITARIAS: LightyearCSVExtractorTest
 * ============================================================================
 * 
 * Verifica mediante pruebas automatizadas JUnit 4 que el extractor de Lightyear
 * reconozca de manera exacta todas las operaciones financieras, gestione las
 * peculiaridades del broker y rechace formatos inválidos.
 */
@SuppressWarnings("nls")
public class LightyearCSVExtractorTest
{
    /**
     * TEST INTEGRAL CON FIXTURE REAL ANONIMIZADO:
     * 
     * Procesa el archivo 'LightyearExport01.csv' que contiene 16 transacciones
     * representativas de la actividad habitual en Lightyear:
     * - Compras y ventas de acciones (AAPL) con comisiones.
     * - Dividendos en USD con retención en origen (GOOG, V).
     * - Distribuciones de fondos monetarios BlackRock ICS en EUR y USD
     *   (comprobando el mapeo de ISIN por ticker y la inversión de retención fiscal).
     * - Intereses de efectivo no invertido y recompensas.
     * - Depósitos y retiradas bancarias.
     * - Conversión de divisas emparejada (EUR -> USD) con GROSS_VALUE y comisión.
     */
    @Test
    public void testLightyearExport01() throws IOException
    {
        var client = new Client();
        var errors = new ArrayList<Exception>();
        var filename = "LightyearExport01.csv";

        // Ejecutar el extractor a través del ayudante estándar de pruebas de PP
        var results = TestExtractorHelper.runExtractor(new LightyearCSVExtractor(client), client, getClass(), filename,
                        errors);

        // 1. Debe completarse sin ningún error de análisis
        assertThat(errors, empty());

        // 2. Comprobación de conteos por tipo de operación
        //    - 5 operaciones de compra/venta (3 compras, 2 ventas)
        assertThat(countBuySell(results), is(5L));

        //    - 10 transacciones de cuenta (2 dividendos, 2 distribuciones MMF, 1 depósito,
        //      2 retiradas, 1 interés, 1 recompensa y 1 comisión de conversión)
        assertThat(countAccountTransactions(results), is(10L));

        //    - 1 transferencia entre cuentas por el intercambio EUR <-> USD
        assertThat(countAccountTransfers(results), is(1L));

        // 3. Conteo total en la lista de resultados:
        //    La lista contiene 16 transacciones + 5 activos (SecurityItem) creados
        //    automáticamente por el SecurityCache para AAPL, GOOG, V, BRICEKSP e ICSUSSDP.
        //    Total = 21 elementos.
        assertThat(results.size(), is(21));

        // 4. Verificación detallada de cada transacción:

        // Dividendo de Alphabet (GOOG): importe neto 11.98 USD, impuestos retenidos 2.11 USD
        assertThat(results, hasItem(dividend( //
                        hasDate("2025-12-16T06:02:35"), //
                        hasAmount("USD", 11.98), //
                        hasTaxes("USD", 2.11), //
                        hasSecurity(hasTicker("GOOG"), hasIsin("US02079K1079")))));

        // Dividendo de Visa (V): importe neto 1.14 USD, impuestos 0.20 USD
        assertThat(results, hasItem(dividend( //
                        hasDate("2025-12-02T06:16:10"), //
                        hasAmount("USD", 1.14), //
                        hasTaxes("USD", 0.20), //
                        hasSecurity(hasTicker("V"), hasIsin("US92826C8394")))));

        // Distribución MMF BlackRock ICS Euro (BRICEKSP):
        // Sin ISIN en el CSV original -> asigna IE000GWTNRJ7; columna Fee se asigna a TAX (0.36 EUR)
        assertThat(results, hasItem(dividend( //
                        hasDate("2025-12-01T13:33:55"), //
                        hasAmount("EUR", 6.83), //
                        hasTaxes("EUR", 0.36), //
                        hasSecurity(hasTicker("BRICEKSP"), hasIsin("IE000GWTNRJ7")))));

        // Distribución MMF BlackRock ICS USD (ICSUSSDP):
        // Sin ISIN en el CSV original -> asigna IE00B44BQ083; columna Fee se asigna a TAX (0.81 USD)
        assertThat(results, hasItem(dividend( //
                        hasDate("2025-12-01T13:33:55"), //
                        hasAmount("USD", 21.17), //
                        hasTaxes("USD", 0.81), //
                        hasSecurity(hasTicker("ICSUSSDP"), hasIsin("IE00B44BQ083")))));

        // Recompensa en efectivo (Reward): 0.05 EUR registrado como interés
        assertThat(results, hasItem(interest( //
                        hasDate("2025-10-02T17:19:06"), //
                        hasAmount("EUR", 0.05))));

        // Interés de saldo no invertido (Interest): 0.02 USD
        assertThat(results, hasItem(interest( //
                        hasDate("2025-10-01T03:18:28"), //
                        hasAmount("USD", 0.02))));

        // Depósito bancario (Deposit): 100.00 EUR
        assertThat(results, hasItem(deposit( //
                        hasDate("2025-04-07T16:04:53"), //
                        hasAmount("EUR", 100.00))));

        // Retiradas hacia cuenta bancaria (Withdrawal): 300.00 USD y 1.00 USD
        assertThat(results, hasItem(removal( //
                        hasDate("2025-07-30T09:01:34"), //
                        hasAmount("USD", 300.00))));
        assertThat(results, hasItem(removal( //
                        hasDate("2025-07-25T07:45:01"), //
                        hasAmount("USD", 1.00))));

        // Compra de acciones de Apple (AAPL): 10 acciones por 1855.00 USD
        assertThat(results, hasItem(purchase( //
                        hasDate("2025-03-15T10:30:00"), //
                        hasShares(10.00), //
                        hasAmount("USD", 1855.00), //
                        hasSecurity(hasTicker("AAPL"), hasIsin("US0378331005")))));

        // Venta de acciones de Apple (AAPL): 10 acciones por 2103.00 USD
        assertThat(results, hasItem(sale( //
                        hasDate("2025-06-20T14:22:11"), //
                        hasShares(10.00), //
                        hasAmount("USD", 2103.00), //
                        hasSecurity(hasTicker("AAPL"), hasIsin("US0378331005")))));

        // Conversión multidivisa emparejada:
        // - Salida de efectivo: 59.03 EUR con GROSS_VALUE informando 64.50 USD
        // - Entrada de efectivo: 64.50 USD
        // - Comisión independiente de cambio de divisa: 0.21 EUR
        assertThat(results, hasItem(outboundCash( //
                        hasDate("2025-04-07T19:09:30"), //
                        hasAmount("EUR", 59.03), //
                        hasForexGrossValue("USD", 64.50))));
        assertThat(results, hasItem(inboundCash( //
                        hasDate("2025-04-07T19:09:30"), //
                        hasAmount("USD", 64.50))));
        assertThat(results, hasItem(fee( //
                        hasDate("2025-04-07T19:09:30"), //
                        hasAmount("EUR", 0.21))));

        // Comprobación de integridad global del flujo de acciones de importación
        new AssertImportActions().check(results, "EUR", "USD");
    }

    /**
     * TEST ESPECÍFICO DE RESOLUCIÓN DE ISIN EN FONDOS MONETARIOS:
     * 
     * Comprueba un CSV donde la columna ISIN está explícitamente vacía para el
     * ticker 'BRICEKSP' y verifica que el extractor asigne el ISIN oficial
     * 'IE000GWTNRJ7' e impute la retención de la columna Fee a Taxes.
     */
    @Test
    public void testDistributionMissingIsinUsesTickerFallback() throws IOException
    {
        var csv = "Date,Reference,Product,ISIN,Type,Quantity,CCY,Price,Gross Amount,Fee,Tax Amt.,Net Amt.,FX Rate\n"
                        + "01/12/2025 13:33:55,REF123,BRICEKSP,,Distribution,100,EUR,,7.19,0.36,,6.83,\n";

        var tempFile = File.createTempFile("lightyear_test", ".csv");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), csv, StandardCharsets.UTF_8);

        var client = new Client();
        var cache = new SecurityCache(client);
        var errors = new ArrayList<Exception>();
        var extractor = new LightyearCSVExtractor(client);

        var results = extractor.extract(cache, new InputFile(tempFile), errors);

        assertThat(errors, empty());
        assertThat(countAccountTransactions(results), is(1L));
        assertThat(results, hasItem(dividend( //
                        hasDate("2025-12-01T13:33:55"), //
                        hasAmount("EUR", 6.83), //
                        hasTaxes("EUR", 0.36), //
                        hasSecurity(hasTicker("BRICEKSP"), hasIsin("IE000GWTNRJ7")))));
    }

    /**
     * TEST DE COMPATIBILIDAD CON DELIMITADOR ';' Y FECHAS ISO:
     * 
     * Verifica que el extractor admita variantes con delimitador de punto y coma
     * (común en configuraciones regionales europeas) y fechas en formato ISO 'yyyy-MM-dd'.
     */
    @Test
    public void testSemicolonDelimiterAndIsoDates() throws IOException
    {
        var csv = "Date;Reference;Product;ISIN;Type;Quantity;CCY;Price;Gross Amount;Fee;Tax Amt.;Net Amt.;FX Rate\n"
                        + "2025-04-07 16:04:53;DEP1;EUR;;Deposit;;EUR;;100,00;;;100,00;\n"
                        + "2025-03-15 10:30:00;ORD1;Apple;US0378331005;Buy;5;USD;180,00;900,00;;;900,00;\n";

        var tempFile = File.createTempFile("lightyear_test_semi", ".csv");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), csv, StandardCharsets.UTF_8);

        var client = new Client();
        var cache = new SecurityCache(client);
        var errors = new ArrayList<Exception>();
        var extractor = new LightyearCSVExtractor(client);

        var results = extractor.extract(cache, new InputFile(tempFile), errors);

        assertThat(errors, empty());
        assertThat(countAccountTransactions(results), is(1L));
        assertThat(countBuySell(results), is(1L));
        assertThat(results, hasItem(deposit(hasDate("2025-04-07T16:04:53"), hasAmount("EUR", 100.00))));
        assertThat(results, hasItem(purchase(hasDate("2025-03-15T10:30:00"), hasShares(5.00), hasAmount("USD", 900.00))));
    }

    /**
     * TEST NEGATIVO: RECHAZO DE ARCHIVOS NO COMPATIBLES
     * 
     * Comprueba que al intentar importar un archivo CSV que no corresponde a Lightyear
     * (sin las cabeceras requeridas), el extractor no genere transacciones erróneas
     * y agregue un error explicativo a la lista de errores.
     */
    @Test
    public void testUnsupportedFileReportsError() throws IOException
    {
        var csv = "SomeHeader,AnotherHeader,Value\n1,2,3\n";

        var tempFile = File.createTempFile("unsupported", ".csv");
        tempFile.deleteOnExit();
        Files.writeString(tempFile.toPath(), csv, StandardCharsets.UTF_8);

        var client = new Client();
        var cache = new SecurityCache(client);
        var errors = new ArrayList<Exception>();
        var extractor = new LightyearCSVExtractor(client);

        var results = extractor.extract(cache, new InputFile(tempFile), errors);

        assertThat(results, empty());
        assertThat(errors.size(), is(1));
    }
}
