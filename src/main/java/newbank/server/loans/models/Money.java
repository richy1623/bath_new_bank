package newbank.server.loans.models;

import java.math.BigDecimal;
import java.math.RoundingMode;

// Money amounts are held with exactly 2 decimal places, like newbank.server.Account
public final class Money {

	private Money() {
	}

	public static boolean hasAtMostTwoDecimals(BigDecimal amount) {
		return amount.stripTrailingZeros().scale() <= 2;
	}

	// Returns the amount at scale 2 (null stays null); rejects fractions of a penny
	public static BigDecimal of(BigDecimal amount) {
		if (amount == null) {
			return null;
		}
		if (!hasAtMostTwoDecimals(amount)) {
			throw new IllegalArgumentException("Amount cannot have more than 2 decimal places: " + amount);
		}
		return amount.setScale(2, RoundingMode.UNNECESSARY);
	}
}
