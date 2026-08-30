# Kaffe rule kernel v1: parity baseline

`com.kaffe.common.rules.v1.RuleKernelV1` is a pure compatibility kernel. It is
not wired into Spring, checkout, campaigns, promotions, loyalty, or Orders.
Publishing the class must not change a production decision.

The first contract freezes only decisions already shared by the domains:

- active/inactive state;
- validity start inclusive and end exclusive;
- branch-local weekday and time windows, with start inclusive and end exclusive;
- payment-method group eligibility;
- order-channel eligibility;
- deterministic rejection reasons and an explicit contract version.

It intentionally does not calculate money, choose a promotion, reserve usage,
mutate points, select an audience, dispatch a campaign, or execute an action.

## Current authorities

- `kaffe-msa-orders` is authoritative at checkout. It evaluates against the
  order's `placed_at` (falling back to `created_at`) and recalculates catalog
  prices, targets, limits, audiences, and schedules before reserving a benefit.
- `kaffe-msa-promotions` evaluates discovery/feed results using its request
  time. It also owns definitions, targeting, eligibility configuration, and
  promotion redemption projection.
- `kaffe-msa-loyalty` owns reward definition, issue validity, points mutation,
  issuance expiry, and reward lifecycle. Orders rechecks an issued reward and
  its schedule when reserving it for checkout.
- Customer campaigns live in Promotions. Approval creates a specific-customer
  audience and forces `kaffe_only` + `customer_app`; Notifications only delivers
  the approved outbox event and is not a rule authority.

## Known parity boundaries

1. Promotions discovery uses the current request instant while checkout uses
   the immutable order placement instant. A promotion can disappear from the
   feed after its window closes and still remain valid for an order placed
   inside the window. This is intentional checkout behavior and must not be
   replaced by `now()`.
2. Promotion target allocation, usage-period windows, channel mapping, and
   weekly schedules currently have equivalent implementations in Promotions
   and Orders. They remain duplicated until shadow comparison proves parity.
3. Invalid eligibility definitions are rejected by Promotions at write time;
   Orders fails closed when reading an invalid or empty custom selection. A
   shared read model must preserve that fail-closed boundary.
4. Rewards cap issued expiry at the reward campaign end. Promotions do not
   issue a second-lived benefit, so this is a domain-specific step rather than
   a shared temporal rule.
5. Campaign approval checks the linked promotion and selected recipients, then
   dispatches. It does not run a separate campaign schedule evaluator. Campaign
   eligibility is inherited from the linked segmented promotion.
6. Current checkout allows only one promotion, customer reward, or Kaffe Points
   benefit. A future kernel cannot introduce stacking by default.

## Safe activation sequence

1. Keep the golden fixtures green in Common, Promotions, Loyalty, and Orders.
2. Publish Common under a new immutable artifact version; never replace the
   existing `1.0.0` package in place.
3. Add read-only shadow evaluation with no writes and no user-visible result.
4. Compare legacy and kernel decisions by contract version and reason code.
5. Resolve every divergence, especially amounts, usage limits, order placement
   time, audiences, addons, exclusions, and combo allocation.
6. Enable one domain and one tenant behind a feature flag with instant rollback.
7. Keep the frozen definition and evaluator version on every applied benefit.

Natural-language creation and AI recommendations must produce a draft rule
definition. They may never publish or execute a monetary rule without the same
validation, authorization, preview, and audit path as a manually created rule.
