package newbank.server.loans.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import newbank.server.loans.models.Customer;
import newbank.server.loans.models.Account;
import newbank.server.loans.models.LoanPayment;
import newbank.server.loans.models.LoanRequest;
import newbank.server.loans.models.Money;

public class LoanService implements LoanPaymentOperation, CustomerLoanOperations {

	private List<Customer> customers;

	public LoanService(List<Customer> customers) {
		this.customers = customers;
	}

	@Override
	public Boolean paymentValidation(BigDecimal amount, Account sourceAccount) {
		// 1. Check not null values
		Boolean notNull = amount != null && sourceAccount != null;
		// 2. Return response (true if a whole-penny amount and enough money is found in
		// the account)
		return notNull && amount.signum() > 0 && Money.hasAtMostTwoDecimals(amount)
				&& sourceAccount.getBalance().compareTo(amount) >= 0;
	}

	@Override
	public Boolean activeLoan(Long loanRequestId) {
		// 1. Get the current loan
		LoanRequest loan = getLoanRequest(loanRequestId);
		// 2. Check if the loan is active
		return loan != null && loan.isActive() && loan.isApproved();
	}

	@Override
	public LoanRequest getLoanRequest(Long loanRequestId) {
		// 1. Get each customer from the list
		for (Customer customer : customers) {
			// 2. Get each loan request from the customer
			for (LoanRequest loan : customer.getLoanRequests()) {
				// 3. Check if the loan request ID matches
				if (loan.getLoanRequestId().equals(loanRequestId)) {
					return loan;
				}
			}
		}
		return null;
	}

	@Override
	public LoanPayment makePayment(Long loanRequestId, BigDecimal amount, Account sourceAccount) {
		// 1. Get the loan request
		LoanRequest loan = getLoanRequest(loanRequestId);
		// 2. Validate the loan, payment amount, source account's balance and debt
		// status
		if (loan == null) {
			throw new IllegalArgumentException("No loan was found with the given id");
		} else if (!paymentValidation(amount, sourceAccount)) {
			throw new IllegalArgumentException("Invalid payment (zero or negative amount) or account not found");
		} else if (loan.getCurrentDebt() != null && loan.getCurrentDebt().compareTo(amount) < 0) {
			throw new IllegalArgumentException("Payment value exceeds the current debt");
		}
		// 3. Validate the loan's active status
		if (!activeLoan(loanRequestId)) {
			throw new IllegalArgumentException("Loan is not active");
		}
		// 4. Register payment
		LoanPayment payment = new LoanPayment(estimateLastLoanPaymentId(), amount, java.time.LocalDateTime.now(),
				sourceAccount, loan);
		// 5. Deduct the amount from the source account & loan's current debt
		sourceAccount.makePayment(amount);
		loan.reduceDebt(amount);
		// 6. Record the payment in the loan's payment history
		loan.getLoanPayments().add(payment);
		// 7. If debt is paid, close it!
		loan.close();
		return payment;
	}

	@Override
	public List<LoanPayment> getPaymentsHistory(Long loanRequestId) {
		// 1. Find Loan
		LoanRequest loan = this.getLoanRequest(loanRequestId);
		if (loan == null) {
			throw new IllegalArgumentException("No loan was found with the given id");
		}
		return loan.getLoanPayments();
	}

	@Override
	public List<LoanRequest> getActiveLoans(Customer customer) {
		// 1. Validate customer's existance
		if (customer == null) {
			throw new IllegalArgumentException("Not a valid cutsomer!");
		}
		// 2. Get Active loans
		List<LoanRequest> loans = new ArrayList<>();
		for (LoanRequest loan : customer.getLoanRequests()) {
			if (loan.isActive()) {
				loans.add(loan);
			}
		}
		return loans;
	}

	@Override
	public BigDecimal getCurrentLoanDebt(Long loanRequestId) {
		// 1. Validate loan existence
		LoanRequest loan = getLoanRequest(loanRequestId);
		if (loan == null) {
			throw new IllegalArgumentException("No loan was found with the given id");
		}
		// 2. Return current debt
		return loan.getCurrentDebt();
	}

	@Override
	public LoanRequest newLoanRequest(Customer customer, BigDecimal amount, BigDecimal interestRate) {
		// 1. Validate customer, amount and interest rate
		if (customer == null) {
			throw new IllegalArgumentException("Invalid customer");
		}
		if (amount == null || amount.signum() <= 0 || !Money.hasAtMostTwoDecimals(amount)) {
			throw new IllegalArgumentException("Invalid loan amount");
		}
		if (interestRate == null || interestRate.signum() <= 0) {
			throw new IllegalArgumentException("Invalid interest rate");
		}
		// 2. Estimate last loan ID
		Long loanId = estimateLastLoanId();
		// 3. Create new loan request with estimated ID
		LoanRequest newLoan = new LoanRequest(loanId, customer, amount, amount, interestRate);
		// 4. Add new loan to customer's loan requests
		customer.getLoanRequests().add(newLoan);
		return newLoan;
	}

	@Override
	public Boolean studyCredit(Long loanRequestId) {
		// 1. Find Loan
		LoanRequest loan = this.getLoanRequest(loanRequestId);
		// 2. Validate if Loan exists and is pending
		if (loan == null) {
			throw new IllegalArgumentException("No loan was found with the given id");
		}
		if (!loan.isPending()) {
			throw new IllegalArgumentException("Loan is not pending");
		}
		// 3. Study credit and approve loan (for simplicity, we approve all pending
		// loans)
		loan.approve();
		return true;
	}

	private Long estimateLastLoanId() {
		Long lastId = 0l;
		for (Customer customer : customers) {
			List<LoanRequest> loanRequests = customer.getLoanRequests();
			if (!loanRequests.isEmpty()) {
				lastId += loanRequests.size();
			}
		}
		return lastId + 1;
	}

	private Long estimateLastLoanPaymentId() {
		Long lastId = 0l;
		for (Customer customer : customers) {
			for (LoanRequest loan : customer.getLoanRequests()) {
				List<LoanPayment> loanPayments = loan.getLoanPayments();
				if (!loanPayments.isEmpty()) {
					lastId += loanPayments.size();
				}
			}
		}
		return lastId + 1;
	}

}
