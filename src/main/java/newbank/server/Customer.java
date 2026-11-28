package newbank.server;

import java.util.ArrayList;

public class Customer {

	private ArrayList<Account> accounts;
	private int id;
	private String fullName;
	private String password;
	// staff can change account statuses but can't bank; REGISTER always creates
	// customers
	private Role role = Role.CUSTOMER;

	public enum Role {
		CUSTOMER, STAFF
	}

	// the only constructor: every customer must have an ID, so there's no empty
	// Customer()
	// (used by REGISTER, the test data, and when loading the customers file)
	public Customer(int id, String fullName, String password) {
		accounts = new ArrayList<>();
		this.id = id;
		this.fullName = fullName;
		this.password = password;
	}

	public int getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public Role getRole() {
		return role;
	}

	void setRole(Role role) {
		this.role = java.util.Objects.requireNonNull(role);
	}

	public boolean isStaff() {
		return role == Role.STAFF;
	}

	// true if the attempt matches this customer's password.
	// Test customers have no password (null, or "" once loaded from the file),
	// so for them a blank attempt (just pressing Enter) matches.
	public boolean checkPassword(String attempt) {
		if (attempt == null) {
			return false;
		}
		String saved = (password == null) ? "" : password;
		return saved.equals(attempt);
	}

	// closed accounts are left out; SHOWACCOUNTDETAILS still lists them
	public String accountsToString() {
		String s = "";
		for (Account a : accounts) {
			if (a.getStatus() == Account.Status.CLOSED) {
				continue;
			}
			if (!s.isEmpty()) {
				s += ", ";
			}
			s += a.toString();
		}
		return s;
	}

	public void addAccount(Account account) {
		accounts.add(account);
		assignMissingAccountNumbers();
	}

	// used when loading the file: every account is added first, so numbers that
	// are already saved are never given to another account
	void addLoadedAccount(Account account) {
		accounts.add(account);
	}

	// accounts without a number get the next free one: customer ID * 100 + position
	void assignMissingAccountNumbers() {
		long next = 1;
		for (Account a : accounts) {
			if (a.getAccountNumber() != null) {
				continue;
			}
			String candidate;
			do {
				candidate = String.format(java.util.Locale.ROOT, "%08d", id * 100L + next++);
			} while (numberInUse(candidate));
			a.setAccountNumber(candidate);
		}
	}

	private boolean numberInUse(String number) {
		for (Account a : accounts) {
			if (number.equals(a.getAccountNumber())) {
				return true;
			}
		}
		return false;
	}

	// the open account of this type, or the most recently opened closed one if
	// there is no open account, or null if there is neither
	public Account getAccount(String accountName) {
		Account closed = null;
		for (Account a : accounts) {
			if (a.getAccountName().equalsIgnoreCase(accountName)) {
				if (a.getStatus() != Account.Status.CLOSED) {
					return a;
				}
				closed = a;
			}
		}
		return closed;
	}

	// closed accounts don't count, so a customer can open that type again
	public boolean hasAccountType(String accountType) {
		Account account = getAccount(accountType);
		return account != null && account.getStatus() != Account.Status.CLOSED;
	}

	// used when saving to the customers file; test customers have no password, so
	// this can be null
	public String getPassword() {
		return password;
	}

	public ArrayList<Account> getAccounts() {
		return accounts;
	}
}
