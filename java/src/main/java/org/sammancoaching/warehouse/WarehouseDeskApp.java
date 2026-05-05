package org.sammancoaching.warehouse;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WarehouseDeskApp {
    public record SeedItem(String sku, double price, int stock) {}

    private final Map<String, Integer> stockBySku = new HashMap<>();
    private final Map<String, Integer> reservedBySku = new HashMap<>();
    private final Map<String, Double> priceBySku = new HashMap<>();
    private final Map<String, String> orderStatus = new HashMap<>();
    private final Map<String, String> orderSku = new HashMap<>();
    private final Map<String, Integer> orderQty = new HashMap<>();
    private final Map<String, String> reservationSku = new HashMap<>();
    private final Map<String, Integer> reservationQty = new HashMap<>();
    private final Map<String, LocalDateTime> reservationExpiry = new HashMap<>();
    private final Map<String, String> reservationCustomer = new HashMap<>();
    private final List<String> eventLog = new ArrayList<>();
    private double cashBalance;
    private int nextOrderNumber;

    public void seedData(List<SeedItem> items, double startingCash, int startingOrderNumber) {
        stockBySku.clear();
        reservedBySku.clear();
        priceBySku.clear();
        orderStatus.clear();
        orderSku.clear();
        orderQty.clear();
        reservationSku.clear();
        reservationQty.clear();
        reservationExpiry.clear();
        reservationCustomer.clear();
        eventLog.clear();

        Set<String> seenSkus = new HashSet<>();
        for (SeedItem item : items) {
            if (item == null) {
                throw new IllegalArgumentException("seed item cannot be null");
            }
            String sku = item.sku().trim();
            if (sku.isEmpty()) {
                throw new IllegalArgumentException("sku cannot be blank");
            }
            if (item.price() < 0) {
                throw new IllegalArgumentException("price cannot be negative for " + sku);
            }
            if (item.stock() < 0) {
                throw new IllegalArgumentException("stock cannot be negative for " + sku);
            }
            if (!seenSkus.add(sku)) {
                throw new IllegalArgumentException("duplicate sku in seed data: " + sku);
            }

            stockBySku.put(sku, item.stock());
            priceBySku.put(sku, item.price());
            reservedBySku.put(sku, 0);
        }

        cashBalance = startingCash;
        nextOrderNumber = startingOrderNumber;
    }

    public void process(List<String> commands) {
        for (String command : commands) {
            processLine(command);
        }
    }

    private void processLine(String line) {
        processLine(line, LocalDateTime.now());
    }

    public void processLine(String line, LocalDateTime currentTime) {
        expireReservations(currentTime);
        String[] parts = line.split(";");
        String type = parts[0];

        if ("RECV".equals(type)) {
            String sku = parts[1];
            int qty = parseInt(parts[2]);
            double unitCost = parseDouble(parts[3]);
            int current = stockBySku.getOrDefault(sku, 0);
            stockBySku.put(sku, current + qty);
            cashBalance = cashBalance - (qty * unitCost);
            eventLog.add("received " + qty + " of " + sku + " at " + unitCost);
            return;
        }

        if ("SELL".equals(type)) {
            String customer = parts[1];
            String sku = parts[2];
            int qty = parseInt(parts[3]);
            String orderId = "O" + nextOrderNumber;
            nextOrderNumber = nextOrderNumber + 1;
            orderSku.put(orderId, sku);
            orderQty.put(orderId, qty);

            int onHand = stockBySku.getOrDefault(sku, 0);
            int reserved = reservedBySku.getOrDefault(sku, 0);
            int available = onHand - reserved;
            if (available < qty) {
                orderStatus.put(orderId, "BACKORDER");
                eventLog.add("order " + orderId + " backordered for " + customer + " sku=" + sku + " qty=" + qty);
            } else {
                stockBySku.put(sku, onHand - qty);
                double unitPrice = priceBySku.getOrDefault(sku, 0.0);
                double orderTotal = unitPrice * qty;
                cashBalance = cashBalance + orderTotal;
                orderStatus.put(orderId, "SHIPPED");
                eventLog.add("order " + orderId + " shipped to " + customer + " amount=" + orderTotal);
            }
            return;
        }

        if ("CANCEL".equals(type)) {
            String orderId = parts[1];
            String status = orderStatus.get(orderId);
            if (status == null) {
                eventLog.add("cannot cancel " + orderId + " because it does not exist");
                return;
            }

            if ("BACKORDER".equals(status)) {
                orderStatus.put(orderId, "CANCELLED");
                eventLog.add("cancelled backorder " + orderId);
                return;
            }

            if ("SHIPPED".equals(status)) {
                String sku = orderSku.get(orderId);
                int qty = orderQty.getOrDefault(orderId, 0);
                int current = stockBySku.getOrDefault(sku, 0);
                stockBySku.put(sku, current + qty);
                double unitPrice = priceBySku.getOrDefault(sku, 0.0);
                cashBalance = cashBalance - (unitPrice * qty);
                orderStatus.put(orderId, "CANCELLED_AFTER_SHIP");
                eventLog.add("cancelled shipped order " + orderId + " with restock");
                return;
            }

            eventLog.add("order " + orderId + " could not be cancelled from state " + status);
            return;
        }

        if ("COUNT".equals(type)) {
            String sku = parts[1];
            int onHand = stockBySku.getOrDefault(sku, 0);
            int reserved = reservedBySku.getOrDefault(sku, 0);
            int available = onHand - reserved;
            eventLog.add("count " + sku + " onHand=" + onHand + " reserved=" + reserved + " available=" + available);
            return;
        }

        if ("RESERVE".equals(type)) {
            String customer = parts[1];
            String sku = parts[2];
            int qty = parseInt(parts[3]);
            int minutes = parseInt(parts[4]);

            int onHand = stockBySku.getOrDefault(sku, 0);
            int reserved = reservedBySku.getOrDefault(sku, 0);
            int available = onHand - reserved;

            if (available < qty) {
                eventLog.add("cannot reserve " + qty + " of " + sku + " for " + customer + ": insufficient stock");
            } else {
                String reservationId = "R" + nextOrderNumber;
                nextOrderNumber = nextOrderNumber + 1;
                reservationSku.put(reservationId, sku);
                reservationQty.put(reservationId, qty);
                reservationCustomer.put(reservationId, customer);
                reservationExpiry.put(reservationId, currentTime.plusMinutes(minutes));
                reservedBySku.put(sku, reserved + qty);
                eventLog.add("reserved " + qty + " of " + sku + " for " + customer + " (id=" + reservationId + ")");
            }
            return;
        }

        if ("CONFIRM".equals(type)) {
            String reservationId = parts[1];
            String sku = reservationSku.get(reservationId);
            if (sku == null) {
                eventLog.add("cannot confirm " + reservationId + ": reservation expired or not found");
                return;
            }

            int qty = reservationQty.get(reservationId);
            stockBySku.put(sku, stockBySku.getOrDefault(sku, 0) - qty);
            reservedBySku.put(sku, reservedBySku.getOrDefault(sku, 0) - qty);

            String orderId = "O" + nextOrderNumber;
            nextOrderNumber = nextOrderNumber + 1;
            orderSku.put(orderId, sku);
            orderQty.put(orderId, qty);
            orderStatus.put(orderId, "SHIPPED");

            double orderTotal = priceBySku.getOrDefault(sku, 0.0) * qty;
            cashBalance = cashBalance + orderTotal;

            removeReservation(reservationId);
            eventLog.add("reservation " + reservationId + " confirmed and shipped as " + orderId);
            return;
        }

        if ("RELEASE".equals(type)) {
            String reservationId = parts[1];
            String sku = reservationSku.get(reservationId);
            if (sku == null) {
                eventLog.add("cannot release " + reservationId + ": reservation expired or not found");
                return;
            }

            int qty = reservationQty.get(reservationId);
            reservedBySku.put(sku, reservedBySku.getOrDefault(sku, 0) - qty);
            removeReservation(reservationId);
            eventLog.add("reservation " + reservationId + " released");
            return;
        }

        if ("DUMP".equals(type)) {
            System.out.println("---- dump ----");
            System.out.println("stock=" + stockBySku);
            System.out.println("reserved=" + reservedBySku);
            System.out.println("orders=" + orderStatus);
            System.out.println("cashBalance=" + cashBalance);
            return;
        }

        eventLog.add("unknown command: " + line);
    }

    private int parseInt(String value) {
        return Integer.parseInt(value.trim());
    }

    private double parseDouble(String value) {
        return Double.parseDouble(value.trim());
    }

    private void expireReservations(LocalDateTime currentTime) {
        List<String> expiredIds = new ArrayList<>();
        for (Map.Entry<String, LocalDateTime> entry : reservationExpiry.entrySet()) {
            if (!entry.getValue().isAfter(currentTime)) {
                expiredIds.add(entry.getKey());
            }
        }

        for (String reservationId : expiredIds) {
            String sku = reservationSku.get(reservationId);
            int qty = reservationQty.get(reservationId);
            reservedBySku.put(sku, reservedBySku.getOrDefault(sku, 0) - qty);
            removeReservation(reservationId);
            eventLog.add("reservation " + reservationId + " expired");
        }
    }

    private void removeReservation(String reservationId) {
        reservationSku.remove(reservationId);
        reservationQty.remove(reservationId);
        reservationExpiry.remove(reservationId);
        reservationCustomer.remove(reservationId);
    }

    public List<String> getEventLog() {
        return Collections.unmodifiableList(eventLog);
    }

    public void printEndOfDayReport() {
        int shipped = 0;
        int backorder = 0;
        int cancelled = 0;
        for (String status : orderStatus.values()) {
            if ("SHIPPED".equals(status)) {
                shipped = shipped + 1;
            } else if ("BACKORDER".equals(status)) {
                backorder = backorder + 1;
            } else if (status.startsWith("CANCELLED")) {
                cancelled = cancelled + 1;
            }
        }

        List<String> lowStock = new ArrayList<>();
        for (Map.Entry<String, Integer> item : stockBySku.entrySet()) {
            if (item.getValue() < 5) {
                lowStock.add(item.getKey());
            }
        }

        System.out.println();
        System.out.println("==== end of day ====");
        System.out.println("orders shipped: " + shipped);
        System.out.println("orders backordered: " + backorder);
        System.out.println("orders cancelled: " + cancelled);
        System.out.println("cash balance: " + String.format("%.2f", cashBalance));
        System.out.println("low stock skus: " + lowStock);
        System.out.println();
        System.out.println("events:");
        for (String event : eventLog) {
            System.out.println(" - " + event);
        }
    }
}
