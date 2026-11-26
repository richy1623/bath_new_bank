package newbank.server.loans.models;

import java.math.BigDecimal;

public class Account {
	private Long accountId;
	private Customer customer;
	private BigDecimal balance;

	public Account(Long accountId, Customer customer, BigDecimal balance) {
		this.accountId = accountId;
		this.customer = customer;
		this.balance = Money.of(balance);
	}

	public Account(newbank.server.Account account) {
		this.accountId = account.getAccountId();
		this.customer = new Customer(account.getCustomer());
		this.balance = account.balanceAmount();
	}

	public Long getAccountId() {
		return accountId;
	}

	public Customer getCustomer() {
		return customer;
	}

	public BigDecimal getBalance() {
		return balance;
	}

	public void makePayment(BigDecimal amount) {
		this.balance = balance.subtract(Money.of(amount));
	}
}
