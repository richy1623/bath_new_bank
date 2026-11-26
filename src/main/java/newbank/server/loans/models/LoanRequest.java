package newbank.server.loans.models;

import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

public class LoanRequest {
	private Long loanRequestId;
	private Customer customer;
	private BigDecimal requestedAmount;
	private BigDecimal currentDebt;
	private BigDecimal interestRate;
	private Boolean active;
	private Boolean approved;
	private Boolean pending;
	private List<LoanPayment> loanPayments;

	// New Loan Request
	public LoanRequest(Long loanRequestId, Customer customer, BigDecimal requestedAmount, BigDecimal currentDebt,
			BigDecimal interestRate) {
		this.loanRequestId = loanRequestId;
		this.customer = customer;
		this.requestedAmount = Money.of(requestedAmount);
		this.currentDebt = Money.of(currentDebt);
		this.interestRate = interestRate;
		this.active = false;
		this.approved = false;
		this.pending = true;
		this.loanPayments = new ArrayList<>();
	}

	// Existing Loan Request
	public LoanRequest(Long loanRequestId, Customer customer, BigDecimal requestedAmount, BigDecimal currentDebt,
			BigDecimal interestRate, Boolean active, Boolean approved, Boolean pending,
			List<LoanPayment> loanPayments) {
		this.loanRequestId = loanRequestId;
		this.customer = customer;
		this.requestedAmount = Money.of(requestedAmount);
		this.currentDebt = Money.of(currentDebt);
		this.interestRate = interestRate;
		this.active = active;
		this.approved = approved;
		this.pending = pending;
		this.loanPayments = loanPayments;
	}

	public Long getLoanRequestId() {
		return loanRequestId;
	}

	public Customer getCustomer() {
		return customer;
	}

	public BigDecimal getRequestedAmount() {
		return requestedAmount;
	}

	public BigDecimal getCurrentDebt() {
		return currentDebt;
	}

	public BigDecimal getInterestRate() {
		return interestRate;
	}

	public Boolean isActive() {
		return active;
	}

	public Boolean isApproved() {
		return approved;
	}

	public Boolean isPending() {
		return pending;
	}

	public List<LoanPayment> getLoanPayments() {
		return loanPayments;
	}

	public void approve() {
		this.approved = true;
		this.pending = false;
		this.active = true;
	}

	public void reduceDebt(BigDecimal amount) {
		this.currentDebt = currentDebt.subtract(Money.of(amount));
	}

	public void close() {
		if (this.currentDebt.signum() == 0) {
			this.active = false;
		}
	}
}
