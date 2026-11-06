package name.abuchen.portfolio.datatransfer.lightyear;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import name.abuchen.portfolio.Messages;
import name.abuchen.portfolio.datatransfer.Extractor;
import name.abuchen.portfolio.datatransfer.SecurityCache;
import name.abuchen.portfolio.model.AccountTransaction;
import name.abuchen.portfolio.model.AccountTransferEntry;
import name.abuchen.portfolio.model.BuySellEntry;
import name.abuchen.portfolio.model.Client;
import name.abuchen.portfolio.model.PortfolioTransaction;
import name.abuchen.portfolio.model.Security;
import name.abuchen.portfolio.model.Transaction.Unit;
import name.abuchen.portfolio.money.CurrencyUnit;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.Values;

/**
 * ============================================================================
 * EXTRACTOR DE TRANSACCIONES CSV DEL BROKER LIGHTYEAR
 * ============================================================================
 * 
 * Implementa la interfaz {@link Extractor} de Portfolio Performance para
 * importar archivos de extracto de actividad (Activity Statement) exportados
 * en formato CSV desde el broker europeo Lightyear (https://lightyear.com).
 * 
 * CARACTERÍSTICAS PRINCIPALES:
 * 1. Detección automática del delimitador: Soporta tanto comas (',') como
 *    puntos y comas (';').
 * 2. Soporte de codificación de texto: UTF-8 (con o sin marca BOM \uFEFF)
 *    y fallback automático a Windows-1252 / ISO-8859-1.
 * 3. Operaciones soportadas:
 *    - Compra y venta de acciones/ETFs (Buy / Sell), con soporte de fracciones.
 *    - Depósitos y retiradas de efectivo (Deposit / Withdrawal).
 *    - Intereses por saldo no invertido (Interest) y recompensas (Reward).
 *    - Dividendos de acciones y distribuciones de fondos monetarios (MMF).
 *    - Conversiones multi-divisa (Conversion) emparejadas en transferencias.
 * 4. Gestión de peculiaridades de Lightyear:
 *    - Fondos monetarios BlackRock ICS que omiten el ISIN (mapeo por ticker).
 *    - Inversión en columnas de impuestos/comisiones en distribuciones.
 *    - Registro de la tasa FX y unidad GROSS_VALUE en transferencias de divisas.
 *  
 */
@SuppressWarnings("nls")
public class LightyearCSVExtractor implements Extractor
{
    /**
     * Formateador de fechas con barras: "dd/MM/yyyy" (formato europeo estándar
     * utilizado habitualmente en las exportaciones de Lightyear).
     * 
     * Incorpora valores por defecto para hora, minuto y segundo en caso de que
     * la cadena en el CSV solo contenga la parte de fecha.
     */
    private static final DateTimeFormatter DATE_FORMAT_SLASH = new DateTimeFormatterBuilder()
                    .appendPattern("dd/MM/yyyy")
                    .optionalStart()
                    .appendPattern("[' ']['T']HH:mm[:ss]")
                    .optionalEnd()
                    .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                    .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                    .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                    .toFormatter(Locale.ROOT);

    /**
     * Formateador de fechas con guiones ISO: "yyyy-MM-dd".
     * Se usa como respaldo o para cuentas configuradas con marcas de tiempo ISO.
     */
    private static final DateTimeFormatter DATE_FORMAT_ISO = new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd")
                    .optionalStart()
                    .appendPattern("[' ']['T']HH:mm[:ss]")
                    .optionalEnd()
                    .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                    .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                    .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
                    .toFormatter(Locale.ROOT);

    /**
     * TABLA DE RESOLUCIÓN DE ISINs PARA FONDOS MONETARIOS (MMF) DE LIGHTYEAR:
     * 
     * En los extractos de Lightyear, cuando se pagan distribuciones de fondos de
     * liquidez de BlackRock (ICS - Institutional Cash Series), la columna ISIN
     * suele venir vacía o nula, figurando únicamente el ticker.
     * 
     * Este mapa asocia dichos tickers con sus ISINs oficiales internacionales:
     * - "BRICEKSP" -> BlackRock ICS Euro Liquidity Fund (ISIN: IE000GWTNRJ7)
     * - "ICSUSSDP" -> BlackRock ICS US Dollar Liquidity Fund (ISIN: IE00B44BQ083)
     */
    private static final Map<String, String> TICKER_TO_ISIN = Map.of(
                    "BRICEKSP", "IE000GWTNRJ7",
                    "ICSUSSDP", "IE00B44BQ083");

    /**
     * Constructor requerido por el contrato de extractores de Portfolio Performance.
     * Recibe la instancia actual del modelo de datos {@link Client}.
     */
    public LightyearCSVExtractor(Client client)
    {
        // El constructor se mantiene alineado con la firma habitual de extractores de PP.
    }

    /**
     * Devuelve la etiqueta descriptiva del importador para los menús y diálogos
     * de la interfaz gráfica.
     */
    @Override
    public String getLabel()
    {
        return Messages.LightyearCSVLabel;
    }

    /**
     * Método principal del ciclo de vida del extractor.
     * 
     * Lee el archivo suministrado, prueba la extracción primero con comas (',')
     * y, si falla o las cabeceras no coinciden, reintenta con punto y coma (';').
     * 
     * @param securityCache Caché para reutilizar o crear activos (Security).
     * @param inputFile     Archivo CSV seleccionado por el usuario.
     * @param errors        Lista acumulativa para registrar errores no fatales.
     * @return Lista de transacciones y activos extraídos ({@link Item}).
     */
    @Override
    public List<Item> extract(SecurityCache securityCache, InputFile inputFile, List<Exception> errors)
    {
        var items = new ArrayList<Item>();
        var source = inputFile.getName();

        try
        {
            // Lectura de bytes con autodetección de codificación (UTF-8 / Win-1252)
            var content = readContent(inputFile);

            // Intentar primero con delimitador coma ',' habitual en exportaciones en inglés
            // Si no reconoce las cabeceras, probar con punto y coma ';' (formato europeo con comas decimales)
            if (!tryParse(content, ',', securityCache, source, items, errors)
                            && !tryParse(content, ';', securityCache, source, items, errors))
            {
                // Si ninguno de los dos delimitadores produjo un archivo válido de Lightyear,
                // notificamos que el archivo no tiene el formato esperado.
                errors.add(new IllegalArgumentException(
                                MessageFormat.format(Messages.LightyearCSVMsgFileNotSupported, source)));
            }
        }
        catch (IOException e)
        {
            errors.add(e);
        }

        return items;
    }

    /**
     * Lee los bytes del archivo y decodifica el texto:
     * - Intenta decodificar en UTF-8 estricto.
     * - Elimina la marca de orden de bytes (BOM: \uFEFF) si está presente al inicio.
     * - Si encuentra secuencias de bytes inválidas para UTF-8, hace fallback a Windows-1252.
     */
    private String readContent(InputFile inputFile) throws IOException
    {
        var bytes = Files.readAllBytes(inputFile.getFile().toPath());
        try
        {
            var content = StandardCharsets.UTF_8.newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes)).toString();
            // Retirar Byte Order Mark (BOM) si existe al principio del archivo
            return content.startsWith("\uFEFF") ? content.substring(1) : content;
        }
        catch (CharacterCodingException e)
        {
            // Fallback para archivos generados en Windows con codificación ANSI / Windows-1252
            return Charset.forName("windows-1252").decode(ByteBuffer.wrap(bytes)).toString();
        }
    }

    /**
     * Intenta analizar el contenido CSV utilizando el delimitador indicado.
     * 
     * El proceso se divide en dos fases:
     * - Fase 1: Emparejar conversiones de divisa (cada cambio genera 2 filas con la misma referencia).
     * - Fase 2: Procesar individualmente las demás filas (compras, ventas, dividendos, intereses, depósitos).
     */
    private boolean tryParse(String content, char delimiter, SecurityCache securityCache, String source,
                    List<Item> items, List<Exception> errors)
    {
        var format = CSVFormat.DEFAULT.builder()
                        .setDelimiter(delimiter)
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreHeaderCase(true)
                        .setTrim(true)
                        .get();

        try (var reader = new StringReader(content); var parser = CSVParser.parse(reader, format))
        {
            var headerMap = parser.getHeaderMap();

            // Verificación de cabeceras mínimas obligatorias de Lightyear
            if (headerMap == null || !hasHeader(headerMap, "Date") || !hasHeader(headerMap, "Type")
                            || !hasHeader(headerMap, "Net Amt.") || !hasHeader(headerMap, "CCY")
                            || !hasHeader(headerMap, "Reference"))
            {
                return false;
            }

            var records = parser.getRecords();
            var processedRecords = new HashSet<CSVRecord>();

            // ----------------------------------------------------------------
            // FASE 1: Reconciliación de conversiones multidivisa emparejadas
            // ----------------------------------------------------------------
            processConversions(records, processedRecords, source, items, errors);

            // ----------------------------------------------------------------
            // FASE 2: Procesamiento de registros individuales
            // ----------------------------------------------------------------
            for (CSVRecord record : records)
            {
                // Si el registro ya fue procesado en la fase 1 como parte de una conversión, lo omitimos
                if (processedRecords.contains(record))
                    continue;

                try
                {
                    processRecord(record, securityCache, source, items);
                }
                catch (Exception e)
                {
                    errors.add(new IllegalArgumentException(
                                    "Lightyear CSV line " + record.getRecordNumber() + ": " + e.getMessage(), e));
                }
            }
            return true;
        }
        catch (IOException e)
        {
            errors.add(e);
            return true;
        }
    }

    /**
     * Comprueba de forma insensible a mayúsculas y espacios si una cabecera existe.
     */
    private boolean hasHeader(Map<String, Integer> headerMap, String expectedName)
    {
        for (String header : headerMap.keySet())
        {
            if (header != null && header.trim().equalsIgnoreCase(expectedName))
                return true;
        }
        return false;
    }

    /**
     * PROCESAMIENTO DE CONVERSIONES DE DIVISA:
     * 
     * En los extractos de Lightyear, cada intercambio de divisas genera 2 líneas
     * con el mismo identificador en la columna "Reference" (ej. "CN-0000000012"):
     * 1. Una fila con importe negativo: representa la divisa vendida/saliente (ej. EUR -59.03).
     * 2. Una fila con importe positivo: representa la divisa comprada/entrante (ej. USD +64.50).
     * 
     * Esta función:
     * - Agrupa las filas con Type="Conversion" por su código de referencia.
     * - Empareja la pata de salida (outRecord) y la pata de entrada (inRecord).
     * - Crea una transferencia entre cuentas ({@link AccountTransferEntry}).
     * - Agrega una unidad GROSS_VALUE con el tipo de cambio FX Rate para que PP calcule
     *   correctamente las tasas de cambio y ganancias/pérdidas por divisa.
     * - Si la pata de salida incluye una comisión en la columna "Fee", genera una
     *   transacción adicional independiente de tipo FEES (comisión).
     */
    private void processConversions(List<CSVRecord> records, Set<CSVRecord> processedRecords, String source,
                    List<Item> items, List<Exception> errors)
    {
        // Agrupar filas de tipo "Conversion" por su valor en "Reference"
        var conversionGroups = new HashMap<String, List<CSVRecord>>();
        for (CSVRecord record : records)
        {
            var type = getField(record, "Type");
            if ("Conversion".equalsIgnoreCase(type))
            {
                var ref = getField(record, "Reference");
                conversionGroups.computeIfAbsent(ref, k -> new ArrayList<>()).add(record);
            }
        }

        for (var entryGroup : conversionGroups.entrySet())
        {
            var group = entryGroup.getValue();
            // Debe haber exactamente dos filas para conformar un intercambio válido
            if (group.size() == 2)
            {
                CSVRecord outRecord = null;
                CSVRecord inRecord = null;

                for (CSVRecord record : group)
                {
                    var grossAmt = parseSignedDecimal(record, "Gross Amount", Values.Amount.factor());
                    var netAmt = parseSignedDecimal(record, "Net Amt.", Values.Amount.factor());
                    // La fila con importe negativo es la salida de fondos
                    if (grossAmt < 0 || netAmt < 0)
                        outRecord = record;
                    else
                        inRecord = record;
                }

                if (outRecord != null && inRecord != null)
                {
                    try
                    {
                        var transferEntry = new AccountTransferEntry();
                        var date = parseDate(getField(outRecord, "Date"));
                        var reference = entryGroup.getKey();

                        // Configuración de la transacción origen (salida de efectivo)
                        var outCcy = getField(outRecord, "CCY");
                        var outAmount = Math.abs(parseSignedDecimal(outRecord, "Net Amt.", Values.Amount.factor()));
                        transferEntry.getSourceTransaction().setCurrencyCode(outCcy);
                        transferEntry.getSourceTransaction().setAmount(outAmount);
                        transferEntry.getSourceTransaction().setDateTime(date);
                        transferEntry.getSourceTransaction().setSource(source);

                        // Configuración de la transacción destino (entrada de efectivo)
                        var inCcy = getField(inRecord, "CCY");
                        var inAmount = Math.abs(parseSignedDecimal(inRecord, "Net Amt.", Values.Amount.factor()));
                        var exchangeRate = getField(inRecord, "FX Rate");

                        // Si existe tipo de cambio informado, agregamos la unidad GROSS_VALUE
                        // Orientación de la tasa: outAmount (EUR) = inAmount (USD) * FX Rate (EUR/USD)
                        if (!exchangeRate.isEmpty())
                        {
                            transferEntry.getSourceTransaction()
                                            .addUnit(new Unit(Unit.Type.GROSS_VALUE, Money.of(outCcy, outAmount),
                                                            Money.of(inCcy, inAmount),
                                                            new BigDecimal(exchangeRate.replace(',', '.'))));
                        }
                        transferEntry.getTargetTransaction().setCurrencyCode(inCcy);
                        transferEntry.getTargetTransaction().setAmount(inAmount);
                        transferEntry.getTargetTransaction().setDateTime(parseDate(getField(inRecord, "Date")));
                        transferEntry.getTargetTransaction().setSource(source);

                        transferEntry.setDate(date);
                        var note = "Currency conversion Ref: " + reference;
                        transferEntry.setNote(note);

                        // Agregar la transferencia a la lista de elementos extraídos
                        items.add(new AccountTransferItem(transferEntry, true));

                        // Procesar comisión de conversión si existe en la fila saliente
                        var fee = Math.abs(parseSignedDecimal(outRecord, "Fee", Values.Amount.factor()));
                        if (fee > 0)
                        {
                            var feeTx = new AccountTransaction();
                            feeTx.setType(AccountTransaction.Type.FEES);
                            feeTx.setDateTime(date);
                            feeTx.setCurrencyCode(outCcy);
                            feeTx.setAmount(fee);
                            feeTx.setNote("Currency conversion fee Ref: " + reference);
                            feeTx.setSource(source);
                            items.add(new TransactionItem(feeTx));
                        }

                        // Marcar ambos registros como procesados para la fase 2
                        processedRecords.add(outRecord);
                        processedRecords.add(inRecord);
                    }
                    catch (Exception e)
                    {
                        errors.add(new IllegalArgumentException("Failed to pair conversion: " + e.getMessage(), e));
                    }
                }
            }
        }
    }

    /**
     * Despachador de registros individuales según el valor de la columna "Type".
     */
    private void processRecord(CSVRecord record, SecurityCache securityCache, String source, List<Item> items)
    {
        var type = getField(record, "Type");
        switch (type.toLowerCase(Locale.ROOT))
        {
            case "buy":
            case "sell":
                processBuySell(record, type, securityCache, source, items);
                break;
            case "dividend":
            case "distribution":
                processDividendOrDistribution(record, type, securityCache, source, items);
                break;
            case "deposit":
            case "withdrawal":
                processDepositWithdrawal(record, type, source, items);
                break;
            case "interest":
            case "reward":
                processInterestOrReward(record, type, source, items);
                break;
            default:
                throw new IllegalArgumentException(
                                MessageFormat.format(Messages.LightyearCSVMsgUnsupportedTransactionType, type));
        }
    }

    /**
     * PROCESAMIENTO DE COMPRAS Y VENTAS (Buy / Sell):
     * 
     * - Soporta compra/venta de acciones fraccionadas con precisión decimal completa.
     * - Asocia el activo financiero mediante {@link #lookupSecurity}.
     * - Calcula el importe total teniendo en cuenta si las comisiones ("Fee") ya
     *   están deducidas en Net Amt. o no.
     * - En compras, si hay comisión, se añade una unidad de tipo FEE a la transacción.
     */
    private void processBuySell(CSVRecord record, String type, SecurityCache securityCache, String source,
                    List<Item> items)
    {
        var isBuy = "buy".equalsIgnoreCase(type);
        var security = lookupSecurity(record, securityCache);
        var date = parseDate(getField(record, "Date"));
        var ccy = getField(record, "CCY");
        var shares = Math.abs(parseSignedDecimal(record, "Quantity", Values.Share.factor()));
        var fee = Math.abs(parseSignedDecimal(record, "Fee", Values.Amount.factor()));
        var netAmt = Math.abs(parseSignedDecimal(record, "Net Amt.", Values.Amount.factor()));
        var grossAmt = Math.abs(parseSignedDecimal(record, "Gross Amount", Values.Amount.factor()));

        // Determinación del importe total según la relación entre bruto, neto y comisiones
        long totalAmount;
        if (isBuy)
        {
            if (grossAmt > 0 && Math.abs(grossAmt - netAmt) == fee)
                totalAmount = Math.max(grossAmt, netAmt);
            else if (grossAmt == netAmt || grossAmt == 0)
                totalAmount = netAmt + fee;
            else
                totalAmount = Math.max(netAmt, grossAmt);
        }
        else
        {
            totalAmount = netAmt;
        }

        var entry = new BuySellEntry();
        entry.setType(isBuy ? PortfolioTransaction.Type.BUY : PortfolioTransaction.Type.SELL);
        entry.setSecurity(security);
        entry.setDate(date);
        entry.setCurrencyCode(ccy);
        entry.setShares(shares);
        entry.setAmount(totalAmount);
        entry.setNote(getField(record, "Reference"));
        entry.setSource(source);

        // Si se cobró comisión, registrar la unidad FEE
        if (fee > 0)
            entry.getPortfolioTransaction().addUnit(new Unit(Unit.Type.FEE, Money.of(ccy, fee)));

        items.add(new BuySellEntryItem(entry));
    }

    /**
     * PROCESAMIENTO DE DIVIDENDOS Y DISTRIBUCIONES (Dividend / Distribution):
     * 
     * PECULIARIDAD DE LIGHTYEAR EN DISTRIBUCIONES:
     * En los archivos exportados de Lightyear para distribuciones de fondos monetarios
     * (Distribution), las columnas de impuestos y comisiones aparecen invertidas por un
     * detalle del broker:
     * - La retención fiscal aplicada se sitúa en la columna "Fee".
     * - La columna "Tax Amt." figura vacía o como 0.
     * Para distribuciones normales de dividendos de acciones (Dividend), los impuestos
     * sí vienen correctamente en "Tax Amt.".
     * 
     * Este método detecta si es "distribution" e imputa la retención al campo fiscal TAX.
     */
    private void processDividendOrDistribution(CSVRecord record, String type, SecurityCache securityCache,
                    String source, List<Item> items)
    {
        var isDistribution = "distribution".equalsIgnoreCase(type);
        var security = lookupSecurity(record, securityCache);
        var date = parseDate(getField(record, "Date"));
        var ccy = getField(record, "CCY");
        var netAmount = Math.abs(parseSignedDecimal(record, "Net Amt.", Values.Amount.factor()));
        var shares = Math.abs(parseSignedDecimal(record, "Quantity", Values.Share.factor()));
        var reference = getField(record, "Reference");

        long tax;
        long fee;

        if (isDistribution)
        {
            // Peculiaridad de Lightyear: en distribuciones, los impuestos vienen en la columna "Fee"
            tax = Math.abs(parseSignedDecimal(record, "Fee", Values.Amount.factor()));
            fee = Math.abs(parseSignedDecimal(record, "Tax Amt.", Values.Amount.factor()));
        }
        else
        {
            tax = Math.abs(parseSignedDecimal(record, "Tax Amt.", Values.Amount.factor()));
            fee = Math.abs(parseSignedDecimal(record, "Fee", Values.Amount.factor()));
        }

        var t = new AccountTransaction();
        t.setType(AccountTransaction.Type.DIVIDENDS);
        t.setSecurity(security);
        t.setDateTime(date);
        t.setCurrencyCode(ccy);
        t.setShares(shares);
        t.setAmount(netAmount);
        t.setNote(isDistribution ? "Distribution " + getField(record, "Ticker") + " REF: " + reference : reference);
        t.setSource(source);

        // Desglose de retención en origen y comisión si aplican
        if (tax > 0)
            t.addUnit(new Unit(Unit.Type.TAX, Money.of(ccy, tax)));
        if (fee > 0)
            t.addUnit(new Unit(Unit.Type.FEE, Money.of(ccy, fee)));

        items.add(new TransactionItem(t));
    }

    /**
     * PROCESAMIENTO DE DEPÓSITOS Y RETIRADAS DE EFECTIVO (Deposit / Withdrawal):
     * 
     * Genera transacciones en cuenta de tipo DEPOSIT (ingreso de dinero externo)
     * o REMOVAL (transferencia de fondos hacia cuenta bancaria externa).
     */
    private void processDepositWithdrawal(CSVRecord record, String type, String source, List<Item> items)
    {
        var isDeposit = "deposit".equalsIgnoreCase(type);
        var date = parseDate(getField(record, "Date"));
        var ccy = getField(record, "CCY");
        var amount = Math.abs(parseSignedDecimal(record, "Net Amt.", Values.Amount.factor()));

        var t = new AccountTransaction();
        t.setType(isDeposit ? AccountTransaction.Type.DEPOSIT : AccountTransaction.Type.REMOVAL);
        t.setDateTime(date);
        t.setCurrencyCode(ccy);
        t.setAmount(amount);
        t.setNote(getField(record, "Reference"));
        t.setSource(source);

        items.add(new TransactionItem(t));
    }

    /**
     * PROCESAMIENTO DE INTERESES DE EFECTIVO Y RECOMPENSAS (Interest / Reward):
     * 
     * - Interest: Intereses devengados por saldos en efectivo no invertidos.
     * - Reward: Promociones o bonificaciones en efectivo otorgadas por el broker.
     * Ambas se clasifican en Portfolio Performance como AccountTransaction.Type.INTEREST.
     */
    private void processInterestOrReward(CSVRecord record, String type, String source, List<Item> items)
    {
        var isInterest = "interest".equalsIgnoreCase(type);
        var date = parseDate(getField(record, "Date"));
        var ccy = getField(record, "CCY");
        var netAmount = Math.abs(parseSignedDecimal(record, "Net Amt.", Values.Amount.factor()));
        var tax = Math.abs(parseSignedDecimal(record, "Tax Amt.", Values.Amount.factor()));
        var reference = getField(record, "Reference");

        var t = new AccountTransaction();
        t.setType(AccountTransaction.Type.INTEREST);
        t.setDateTime(date);
        t.setCurrencyCode(ccy);
        t.setAmount(netAmount);
        t.setNote((isInterest ? "Interest " : "Reward ") + reference);
        t.setSource(source);

        // Si Lightyear retuvo impuestos sobre los intereses, se añade la unidad TAX
        if (tax > 0)
            t.addUnit(new Unit(Unit.Type.TAX, Money.of(ccy, tax)));

        items.add(new TransactionItem(t));
    }

    /**
     * IDENTIFICACIÓN Y RECUPERACIÓN DEL ACTIVO FINANCIERO (Security):
     * 
     * Orden de prioridad para identificar el activo:
     * 1. Columna "ISIN".
     * 2. Si el ISIN está vacío pero existe "Ticker", consulta la tabla TICKER_TO_ISIN.
     * 3. Nombre del activo: columna "Instrument", fallback a "Name", fallback al ticker/isin.
     * 4. Divisa del activo: columna "CCY", fallback a "EUR".
     * 
     * Utiliza {@link SecurityCache#lookup} para vincular con un activo ya existente
     * en el portafolio del usuario o crear uno nuevo si no existe.
     */
    private Security lookupSecurity(CSVRecord record, SecurityCache securityCache)
    {
        var isin = getField(record, "ISIN");
        var ticker = getField(record, "Ticker");

        // Fallback para fondos monetarios de BlackRock que no exportan ISIN
        if (isin.isEmpty() && !ticker.isEmpty() && TICKER_TO_ISIN.containsKey(ticker))
            isin = TICKER_TO_ISIN.get(ticker);

        var name = getField(record, "Instrument");
        if (name.isEmpty())
            name = getField(record, "Name");
        if (name.isEmpty())
            name = !ticker.isEmpty() ? ticker : isin;

        var ccy = getField(record, "CCY");
        if (ccy.isEmpty())
            ccy = CurrencyUnit.EUR;

        final var finalIsin = isin.isEmpty() ? null : isin;
        final var finalTicker = ticker.isEmpty() ? null : ticker;
        final var finalName = name;
        final var finalCcy = ccy;

        return securityCache.lookup(finalIsin, finalTicker, null, finalName, () -> {
            var s = new Security(finalName, finalCcy);
            if (finalIsin != null)
                s.setIsin(finalIsin);
            if (finalTicker != null)
                s.setTickerSymbol(finalTicker);
            return s;
        });
    }

    /**
     * PARSER DE FECHA Y HORA FLEXIBLE:
     * 
     * Soporta múltiples formatos que pueden aparecer según el idioma o la versión del CSV:
     * 1. Marcas de tiempo ISO con huso horario (ej. "2025-04-07T19:09:30Z" o "+02:00").
     * 2. Formato europeo con barras (ej. "07/04/2025 19:09:30").
     * 3. Formato estándar ISO (ej. "2025-04-07 19:09:30" o "2025-04-07T19:09:30").
     */
    private LocalDateTime parseDate(String dateStr)
    {
        if (dateStr == null || dateStr.isBlank())
            throw new IllegalArgumentException("Missing date");

        var trimmed = dateStr.trim();

        // Manejo de marcas de tiempo con indicador de zona horaria (Z o +HH:mm)
        if (trimmed.endsWith("Z") || trimmed.contains("+") || (trimmed.lastIndexOf('-') > 10))
        {
            try
            {
                return java.time.OffsetDateTime.parse(trimmed).toLocalDateTime();
            }
            catch (Exception ignored)
            {
                // Fallback a los patrones convencionales
            }
        }

        // Si contiene barras '/', se procesa como formato europeo "dd/MM/yyyy"
        if (trimmed.contains("/"))
            return LocalDateTime.parse(trimmed, DATE_FORMAT_SLASH);

        // Si tiene formato extendido "yyyy-MM-dd HH:mm:ss"
        if (trimmed.length() > 19 && (trimmed.charAt(10) == ' ' || trimmed.charAt(10) == 'T'))
        {
            try
            {
                return LocalDateTime.parse(trimmed.substring(0, 19).replace('T', ' '),
                                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            catch (Exception ignored)
            {
                // Fallback
            }
        }

        // Formato ISO base "yyyy-MM-dd"
        return LocalDateTime.parse(trimmed, DATE_FORMAT_ISO);
    }

    /**
     * Obtiene el valor de una columna de forma insensible a mayúsculas/minúsculas.
     * Retorna una cadena vacía si la columna no existe o su valor es null.
     */
    private String getField(CSVRecord record, String column)
    {
        for (String key : record.getParser().getHeaderMap().keySet())
        {
            if (key != null && key.trim().equalsIgnoreCase(column))
            {
                var val = record.get(key);
                return val != null ? val.trim() : "";
            }
        }
        return "";
    }

    /**
     * Parsea un número decimal con signo a un entero escalado (formato interno de PP).
     * 
     * Portfolio Performance almacena internamente importes monetarios y acciones
     * multiplicados por un factor (ej. 100 para centavos de moneda, o 10.000.000
     * para fracciones de acciones):
     * - Reemplaza comas decimales por puntos.
     * - Multiplica por el factor indicado ({@code factor}).
     * - Redondea usando {@link RoundingMode#HALF_UP}.
     */
    private long parseSignedDecimal(CSVRecord record, String column, long factor)
    {
        var value = getField(record, column);
        if (value.isEmpty())
            return 0;
        return new BigDecimal(value.replace(',', '.')).multiply(BigDecimal.valueOf(factor))
                        .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
