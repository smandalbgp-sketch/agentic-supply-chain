package com.example.agentic.agent;

import com.example.agentic.llm.Llm;
import com.example.agentic.model.CustomerResolution;
import com.example.agentic.model.ImpactReport;
import com.example.agentic.model.InventoryReport;
import com.example.agentic.model.Notification;
import com.example.agentic.model.Order;
import com.example.agentic.model.SupplierDelayEvent;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Customer Service Agent: "draft notifications & resolutions". Drafts only; sending is the supervisor's call. */
public class CustomerServiceAgent {

    /** A goodwill credit is proposed when an order is at least this many days late. */
    static final int CREDIT_AFTER_DAYS_LATE = 3;
    static final BigDecimal CREDIT_RATE = new BigDecimal("0.05");

    private final Llm llm;

    public CustomerServiceAgent(Llm llm) {
        this.llm = llm;
    }

    public CustomerResolution resolve(SupplierDelayEvent event, InventoryReport inventory, ImpactReport impact) {
        List<Notification> notifications = new ArrayList<>();
        BigDecimal credit = BigDecimal.ZERO;

        String reason = (event.reason() == null || event.reason().isBlank()) ? "unspecified" : event.reason();

        for (Order order : impact.impactedOrders()) {
            int daysLate = event.delayDays() - order.promisedInDays();
            boolean creditEligible = daysLate >= CREDIT_AFTER_DAYS_LATE;
            if (creditEligible) {
                credit = credit.add(order.value().multiply(CREDIT_RATE).setScale(2, RoundingMode.HALF_UP));
            }

            String next = inventory.usableAlternatives().isEmpty()
                    ? " We will update you as soon as we have a new delivery date."
                    : " We are arranging a faster alternative source.";
            String goodwill = creditEligible ? " We are also reviewing a goodwill credit." : "";

            String draft = ("Dear %s, your order %s (%d x %s) was promised in %s but is now expected "
                    + "about %s late due to a supplier delay (%s).%s%s")
                    .formatted(order.customerName(), order.orderId(), order.quantity(), order.sku(),
                            days(order.promisedInDays()), days(daysLate), reason, next, goodwill);

            String body = llm.refine(
                    "Rewrite as a polite, concise customer email. Do not promise anything that is not in the text.",
                    draft);

            notifications.add(new Notification(order.orderId(), order.customerName(),
                    "Update on your order " + order.orderId(), body));
        }
        return new CustomerResolution(List.copyOf(notifications), credit);
    }

    private static String days(int n) {
        return n == 1 ? "1 day" : n + " days";
    }
}
