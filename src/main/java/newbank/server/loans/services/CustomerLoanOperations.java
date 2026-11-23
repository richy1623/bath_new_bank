package newbank.server.loans.services;

import newbank.server.loans.models.Customer;
import newbank.server.loans.models.LoanRequest;

public interface CustomerLoanOperations {
	LoanRequest newLoanRequest(Customer customer, Double amount, Double interestRate);
	Boolean studyCredit(Long loanRequestId);
}
