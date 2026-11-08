package io.github.confidesconch.villagelife.market.contract;

/**
 * What a trade contract says (M15d): {@code percent} % of the giver's surplus of {@code good}, at most
 * {@code max} per delivery, every {@code days} days; for a full delivery the receiver pays
 * {@code paymentCount} × {@code payment}. Items are ids like {@code minecraft:wheat}.
 */
public record ContractTerms(String good, int percent, int max, int days, String payment, int paymentCount) {
}
