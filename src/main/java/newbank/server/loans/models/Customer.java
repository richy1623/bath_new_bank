package newbank.server.loans.models;
import java.util.List;

public class Customer {
	private Long customerId;
	private String name;
	private String nationalId;
	private List<Account> accounts;
	private List<LoanRequest> loanRequests;

	public Customer(Long customerId, String name, String nationalId, List<Account> accounts,
			List<LoanRequest> loanRequests) {
		this.customerId = customerId;
		this.name = name;
		this.nationalId = nationalId;
		this.accounts = accounts;
		this.loanRequests = loanRequests;
	}

	public Customer (newbank.server.Customer customer) {
		this.customerId = (long) customer.getId();
		this.name = customer.getFullName();
		
		//Link customer's accounts
		for (newbank.server.Account account : customer.getAccounts()) {
			this.accounts.add(new Account(account));
		}
	}

	public Long getCustomerId() {
		return customerId;
	}

	public String getName() {
		return name;
	}

	public String getNationalId() {
		return nationalId;
	}

	public List<Account> getAccounts() {
		return accounts;
	}

	public List<LoanRequest> getLoanRequests() {
		return loanRequests;
	}

	public void addAccount(Account account) {
		this.accounts.add(account);
	}

	public void addLoanRequest(LoanRequest loanRequest) {
		this.loanRequests.add(loanRequest);
	}
}
