package newbank.server.loans.models;
import java.util.List;
import java.util.ArrayList;

public class LoanRequest {
	private Long loanRequestId;
	private Customer customer;
	private Double requestedAmount;
	private Double currentDebt;
	private Double interestRate;
	private Boolean active;
	private Boolean approved;
	private Boolean pending;
	private List<LoanPayment> loanPayments;

	// New Loan Request
	public LoanRequest(Long loanRequestId, Customer customer, Double requestedAmount, Double currentDebt,
			Double interestRate) {
		this.loanRequestId = loanRequestId;
		this.customer = customer;
		this.requestedAmount = requestedAmount;
		this.currentDebt = currentDebt;
		this.interestRate = interestRate;
		this.active = false;
		this.approved = false;
		this.pending = true;
		this.loanPayments = new ArrayList<>();
	}

	// Existing Loan Request
	public LoanRequest(Long loanRequestId, Customer customer, Double requestedAmount, Double currentDebt,
			Double interestRate, Boolean active, Boolean approved, Boolean pending, List<LoanPayment> loanPayments) {
		this.loanRequestId = loanRequestId;
		this.customer = customer;
		this.requestedAmount = requestedAmount;
		this.currentDebt = currentDebt;
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

	public Double getRequestedAmount() {
		return requestedAmount;
	}

	public Double getCurrentDebt() {
		return currentDebt;
	}

	public Double getInterestRate() {
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

	public void reduceDebt(Double amount) {
		this.currentDebt -= amount;
	}

	public void close() {
		if (this.currentDebt == 0.0) {
			this.active = false;
		}
	}
}
