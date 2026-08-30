package com.kaffe.common.rules.v1;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Pure, side-effect-free compatibility kernel for the first unified Kaffe rule contract.
 *
 * <p>This type deliberately has no Spring, persistence, clock, or network dependency. Services
 * may compare their existing decisions against it before a future opt-in migration. Adding this
 * class does not activate a new evaluator.</p>
 */
public final class RuleKernelV1 {

    public static final String CONTRACT_VERSION = "kaffe-rule-kernel/v1";

    private RuleKernelV1() {
    }

    public static Decision evaluate(Definition definition, Context context) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(context, "context");

        if (!context.active()) {
            return Decision.reject(Reason.INACTIVE);
        }
        if (context.effectiveAt().isBefore(definition.startsAt())) {
            return Decision.reject(Reason.NOT_STARTED);
        }
        if (!context.effectiveAt().isBefore(definition.endsAt())) {
            return Decision.reject(Reason.ENDED);
        }
        if (!definition.scheduleWindows().isEmpty()) {
            ZonedDateTime local = context.effectiveAt().toInstant().atZone(context.zoneId());
            int localDay = local.getDayOfWeek().getValue();
            LocalTime localTime = local.toLocalTime();
            boolean scheduled = definition.scheduleWindows().stream()
                    .anyMatch(window -> window.matches(localDay, localTime));
            if (!scheduled) {
                return Decision.reject(Reason.OUTSIDE_SCHEDULE);
            }
        }
        if (!definition.paymentEligibility().matches(context.paymentMethodGroup())) {
            return Decision.reject(Reason.PAYMENT_METHOD_NOT_ELIGIBLE);
        }
        if (!definition.orderChannelEligibility().matches(context.orderChannel())) {
            return Decision.reject(Reason.ORDER_CHANNEL_NOT_ELIGIBLE);
        }
        return Decision.accept();
    }

    public enum Reason {
        ELIGIBLE("eligible"),
        INACTIVE("inactive"),
        NOT_STARTED("not_started"),
        ENDED("ended"),
        OUTSIDE_SCHEDULE("outside_schedule"),
        PAYMENT_METHOD_NOT_ELIGIBLE("payment_method_not_eligible"),
        ORDER_CHANNEL_NOT_ELIGIBLE("order_channel_not_eligible");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public record Decision(boolean eligible, Reason reason, String contractVersion) {
        public Decision {
            Objects.requireNonNull(reason, "reason");
            if (!CONTRACT_VERSION.equals(contractVersion)) {
                throw new IllegalArgumentException("Unsupported rule contract version");
            }
            if (eligible != (reason == Reason.ELIGIBLE)) {
                throw new IllegalArgumentException("Rule decision and reason disagree");
            }
        }

        static Decision accept() {
            return new Decision(true, Reason.ELIGIBLE, CONTRACT_VERSION);
        }

        static Decision reject(Reason reason) {
            return new Decision(false, reason, CONTRACT_VERSION);
        }
    }

    public record Definition(
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            List<WeeklyWindow> scheduleWindows,
            Eligibility paymentEligibility,
            Eligibility orderChannelEligibility
    ) {
        public Definition {
            Objects.requireNonNull(startsAt, "startsAt");
            Objects.requireNonNull(endsAt, "endsAt");
            if (!endsAt.isAfter(startsAt)) {
                throw new IllegalArgumentException("endsAt must be after startsAt");
            }
            scheduleWindows = scheduleWindows == null ? List.of() : List.copyOf(scheduleWindows);
            paymentEligibility = Objects.requireNonNull(paymentEligibility, "paymentEligibility");
            orderChannelEligibility = Objects.requireNonNull(
                    orderChannelEligibility, "orderChannelEligibility");
        }
    }

    public record Context(
            boolean active,
            OffsetDateTime effectiveAt,
            ZoneId zoneId,
            String paymentMethodGroup,
            String orderChannel
    ) {
        public Context {
            Objects.requireNonNull(effectiveAt, "effectiveAt");
            Objects.requireNonNull(zoneId, "zoneId");
            paymentMethodGroup = canonicalCode(paymentMethodGroup);
            orderChannel = canonicalCode(orderChannel);
        }
    }

    public record WeeklyWindow(int dayOfWeek, LocalTime timeFrom, LocalTime timeTo) {
        public WeeklyWindow {
            if (dayOfWeek < 1 || dayOfWeek > 7) {
                throw new IllegalArgumentException("dayOfWeek must be between 1 and 7");
            }
            if ((timeFrom == null) != (timeTo == null)) {
                throw new IllegalArgumentException("timeFrom and timeTo must both be present or absent");
            }
            if (timeFrom != null && !timeTo.isAfter(timeFrom)) {
                throw new IllegalArgumentException("Overnight or empty windows are not supported in v1");
            }
        }

        boolean matches(int localDay, LocalTime localTime) {
            return dayOfWeek == localDay
                    && (timeFrom == null
                    || (!localTime.isBefore(timeFrom) && localTime.isBefore(timeTo)));
        }
    }

    public record Eligibility(boolean all, Set<String> selectedCodes) {
        public Eligibility {
            Set<String> normalized = new LinkedHashSet<>();
            if (selectedCodes != null) {
                selectedCodes.stream()
                        .filter(Objects::nonNull)
                        .filter(value -> !value.isBlank())
                        .map(RuleKernelV1::canonicalCode)
                        .forEach(normalized::add);
            }
            selectedCodes = Set.copyOf(normalized);
            if (!all && selectedCodes.isEmpty()) {
                throw new IllegalArgumentException("Selected eligibility requires at least one code");
            }
            if (all && !selectedCodes.isEmpty()) {
                throw new IllegalArgumentException("All eligibility cannot include selected codes");
            }
        }

        public static Eligibility allowAll() {
            return new Eligibility(true, Set.of());
        }

        public static Eligibility only(Set<String> selectedCodes) {
            return new Eligibility(false, selectedCodes);
        }

        boolean matches(String value) {
            return all || selectedCodes.contains(canonicalCode(value));
        }
    }

    private static String canonicalCode(String value) {
        if (value == null || value.isBlank()) {
            return "other";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
