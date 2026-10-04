package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class CrmWhatsappEcommerceOrderParser {
    private static final Pattern CART_HEADER = Pattern.compile(
            "(?i)^\\s*Hola\\s+KIMENTS,\\s*quiero\\s+comprar\\s+por\\s+WhatsApp:\\s*$");
    private static final Pattern SINGLE_HEADER = Pattern.compile(
            "(?i)^\\s*Hola,\\s*quiero\\s+comprar\\s+(.+?)\\s*$");
    private static final Pattern ITEM_HEADER = Pattern.compile("^\\s*\\d+\\.\\s+(.+?)\\s*$");
    private static final Pattern COLOR_SIZE = Pattern.compile(
            "(?i)^\\s*Color:\\s*(.+?)\\s*\\|\\s*Talla:\\s*(.+?)\\s*$");
    private static final Pattern COLOR = Pattern.compile("(?i)^\\s*Color:\\s*(.+?)\\s*$");
    private static final Pattern SIZE = Pattern.compile("(?i)^\\s*Talla:\\s*(.+?)\\s*$");
    private static final Pattern QUANTITY = Pattern.compile(
            "(?i)^\\s*Cantidad:\\s*(\\d+)(?:\\s*x\\s*S/\\s*([0-9.,]+)\\s*=\\s*S/\\s*([0-9.,]+))?\\s*$");
    private static final Pattern SUBTOTAL = Pattern.compile("(?i)^\\s*Subtotal:\\s*S/\\s*([0-9.,]+)\\s*$");
    private static final Pattern DISCOUNT = Pattern.compile("(?i)^\\s*Descuento:\\s*-?S/\\s*([0-9.,]+)\\s*$");
    private static final Pattern TOTAL = Pattern.compile("(?i)^\\s*Total:\\s*S/\\s*([0-9.,]+)\\s*$");
    private static final String COMPACT_PREFIX =
            "(?:hola\\s*[,!]\\s*)?(?:(?:quiero|deseo|quisiera)\\s+(?:comprar\\s+)?|"
                    + "(?:dame|agrega|anade|añade)\\s+)?";
    private static final Pattern COMPACT_WITH_COLOR = Pattern.compile(
            "(?iu)^\\s*" + COMPACT_PREFIX
                    + "(.+?)\\s+color\\s*:?\\s*(.+?)\\s+(?:\\|\\s*)?talla\\s*:?\\s*([^\\s|]+)"
                    + "\\s+(?:\\|\\s*)?cantidad\\s*:?\\s*(\\d+)\\s*$");
    private static final Pattern COMPACT_WITH_IMPLICIT_COLOR = Pattern.compile(
            "(?iu)^\\s*" + COMPACT_PREFIX
                    + "(.+?)\\s+(?:\\|\\s*)?talla\\s*:?\\s*([^\\s|]+)"
                    + "\\s+(?:\\|\\s*)?cantidad\\s*:?\\s*(\\d+)\\s*$");

    public EcommerceWhatsappOrder parse(String body) {
        String text = body == null ? "" : body.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (text.isBlank()) return EcommerceWhatsappOrder.notRecognized();
        List<String> lines = text.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) return EcommerceWhatsappOrder.notRecognized();
        if (CART_HEADER.matcher(lines.getFirst()).matches()) return parseCart(lines);
        EcommerceWhatsappOrder compact = parseCompact(text);
        if (compact.recognized()) return compact;
        Matcher single = SINGLE_HEADER.matcher(lines.getFirst());
        if (single.matches()) return parseSingle(lines, clean(single.group(1)));
        return EcommerceWhatsappOrder.notRecognized();
    }

    private EcommerceWhatsappOrder parseCompact(String text) {
        String singleLine = text.replaceAll("\\s+", " ").trim();
        Matcher explicit = COMPACT_WITH_COLOR.matcher(singleLine);
        if (explicit.matches()) {
            return compactOrder(explicit.group(1), explicit.group(2), explicit.group(3), explicit.group(4));
        }
        Matcher implicit = COMPACT_WITH_IMPLICIT_COLOR.matcher(singleLine);
        if (implicit.matches()) {
            // The catalog separates the longest matching product name from the remaining color words.
            return compactOrder(implicit.group(1), "", implicit.group(2), implicit.group(3));
        }
        return EcommerceWhatsappOrder.notRecognized();
    }

    private EcommerceWhatsappOrder compactOrder(String product, String color, String size, String quantity) {
        return new EcommerceWhatsappOrder(true,
                List.of(new EcommerceWhatsappOrderItem(clean(product), clean(color), clean(size),
                        integer(quantity), null, null)),
                null, null, null, List.of());
    }

    private EcommerceWhatsappOrder parseCart(List<String> lines) {
        List<EcommerceWhatsappOrderItem> items = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        BigDecimal subtotal = null;
        BigDecimal discount = null;
        BigDecimal total = null;
        for (int index = 1; index < lines.size();) {
            String line = lines.get(index);
            Matcher itemHeader = ITEM_HEADER.matcher(line);
            if (itemHeader.matches()) {
                String product = clean(itemHeader.group(1));
                String color = "";
                String size = "";
                Integer quantity = null;
                BigDecimal unitPrice = null;
                BigDecimal lineTotal = null;
                index++;
                while (index < lines.size() && !ITEM_HEADER.matcher(lines.get(index)).matches()
                        && !isTotalsLine(lines.get(index))) {
                    Matcher attributes = COLOR_SIZE.matcher(lines.get(index));
                    Matcher quantityLine = QUANTITY.matcher(lines.get(index));
                    if (attributes.matches()) {
                        color = clean(attributes.group(1));
                        size = clean(attributes.group(2));
                    } else if (quantityLine.matches()) {
                        quantity = integer(quantityLine.group(1));
                        unitPrice = money(quantityLine.group(2));
                        lineTotal = money(quantityLine.group(3));
                    }
                    index++;
                }
                if (product.isBlank()) errors.add("Hay un producto sin nombre.");
                else items.add(new EcommerceWhatsappOrderItem(product, color, size, quantity, unitPrice, lineTotal));
                continue;
            }
            Matcher subtotalLine = SUBTOTAL.matcher(line);
            Matcher discountLine = DISCOUNT.matcher(line);
            Matcher totalLine = TOTAL.matcher(line);
            if (subtotalLine.matches()) subtotal = money(subtotalLine.group(1));
            else if (discountLine.matches()) discount = money(discountLine.group(1));
            else if (totalLine.matches()) total = money(totalLine.group(1));
            index++;
        }
        if (items.isEmpty()) errors.add("No pude identificar productos en el pedido del ecommerce.");
        return new EcommerceWhatsappOrder(true, items, subtotal, discount, total, errors);
    }

    private EcommerceWhatsappOrder parseSingle(List<String> lines, String product) {
        String color = "";
        String size = "";
        Integer quantity = null;
        List<String> errors = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            Matcher colorLine = COLOR.matcher(lines.get(index));
            Matcher sizeLine = SIZE.matcher(lines.get(index));
            Matcher quantityLine = QUANTITY.matcher(lines.get(index));
            if (colorLine.matches()) color = clean(colorLine.group(1));
            else if (sizeLine.matches()) size = clean(sizeLine.group(1));
            else if (quantityLine.matches()) quantity = integer(quantityLine.group(1));
        }
        if (product.isBlank()) errors.add("Falta el nombre del producto.");
        return new EcommerceWhatsappOrder(true,
                List.of(new EcommerceWhatsappOrderItem(product, color, size, quantity, null, null)),
                null, null, null, errors);
    }

    private boolean isTotalsLine(String value) {
        return SUBTOTAL.matcher(value).matches() || DISCOUNT.matcher(value).matches() || TOTAL.matcher(value).matches();
    }

    private Integer integer(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BigDecimal money(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.contains(",") && normalized.contains(".")) {
            normalized = normalized.lastIndexOf(',') > normalized.lastIndexOf('.')
                    ? normalized.replace(".", "").replace(',', '.')
                    : normalized.replace(",", "");
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(',', '.');
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public record EcommerceWhatsappOrder(
            boolean recognized,
            List<EcommerceWhatsappOrderItem> items,
            BigDecimal informedSubtotal,
            BigDecimal informedDiscount,
            BigDecimal informedTotal,
            List<String> parsingErrors) {
        static EcommerceWhatsappOrder notRecognized() {
            return new EcommerceWhatsappOrder(false, List.of(), null, null, null, List.of());
        }
    }

    public record EcommerceWhatsappOrderItem(
            String productName,
            String color,
            String size,
            Integer quantity,
            BigDecimal informedUnitPrice,
            BigDecimal informedLineTotal) {
        public String label() {
            return productName == null ? "Producto" : productName.toUpperCase(Locale.ROOT);
        }
    }
}
