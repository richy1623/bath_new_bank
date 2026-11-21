package newbank.server.loans.services;

import java.util.ArrayList;
import java.util.List;

import newbank.server.loans.models.Customer;
import newbank.server.loans.models.Account;
import newbank.server.loans.models.LoanPayment;
import newbank.server.loans.models.LoanRequest;

public class LoanService implements LoanPaymentOperation {

	private List<Customer> customers;

	public LoanService() {
	}

	public LoanService(List<Customer> customers) {
		this.customers = customers;
	}

	@Override
	public Boolean paymentValidation(Double amount, Account sourceAccount) {
		// 1. Check not null values
		Boolean notNull = amount != null && sourceAccount != null;
		// 2. Return response (true if enough money is found in the account)
		return notNull && amount > 0 && sourceAccount.getBalance() >= amount;
	}

	@Override
	public Boolean activeLoan(Long loanRequestId) {
		// 1. Get the current loan
		LoanRequest loan = getLoanRequest(loanRequestId);
		// 2. Check if the loan is active
		return loan != null && loan.isActive();
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
	public LoanPayment makePayment(Long loanRequestId, Double amount, Account sourceAccount) {
		// 1. Get the loan request
		LoanRequest loan = getLoanRequest(loanRequestId);
		// 2. Validate the loam, payment amount, source account's balance and debt status
		if (loan == null) {
			throw new IllegalArgumentException("No loan was found with the given id");
		} else if (!paymentValidation(amount, sourceAccount)) {
			throw new IllegalArgumentException("Invalid payment (zero or negative amount) or account not found");
		} else if (loan.getCurrentDebt() != null && loan.getCurrentDebt() < amount) {
			throw new IllegalArgumentException("Payment value exceeds the current debt");
		}
		// 3. Register payment
		LoanPayment payment = new LoanPayment(amount, java.time.LocalDateTime.now(), sourceAccount, loan);
		// 4. Deduct the amount from the source account & loan's current debt
		sourceAccount.makePayment(amount);
		loan.reduceDebt(amount);
		// 5. Record the payment in the loan's payment history
		loan.getLoanPayments().add(payment);
		//6. If debt is paid, close it!
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
		//2. Get Active loans
		List<LoanRequest> loans = new ArrayList<>();
		for (LoanRequest loan : customer.getLoanRequests()) {
			if (loan.isActive()) {
				loans.add(loan);
			}
		}
		return loans;
	}

	@Override
	public Double getCurrentLoanDebt(Long loanRequestId) {
		// 1. Validate loan existence
		LoanRequest loan = getLoanRequest(loanRequestId);
		if (loan == null) {
			throw new IllegalArgumentException("No loan was found with the given id");
		}
		// 2. Return current debt
		return loan.getCurrentDebt();
	}

}
