package newbank.server;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class NewBank {

	// customers are saved to this plain text file (in the folder the server is
	// started from,
	// which is the project folder when you follow the README)
	private static final String CUSTOMERS_FILE = "customers_database.txt";

	private static final NewBank bank = new NewBank(CUSTOMERS_FILE);
	private HashMap<String, Customer> customers;
	private CustomerDatabase database;
	// false if the file exists but couldn't be loaded. Saving is then switched off,
	// so the empty customer list in memory never overwrites the real file.
	private boolean canSave = true;
	// the ID the next registered customer will get (one more than the highest ID so
	// far)
	private int nextId = 1;
	private final Clock clock;

	// only these account types may be created with NEWACCOUNT
	private static final String[] VALID_ACCOUNT_TYPES = {"Savings", "Cheque", "Investment"};

	// package-private (not private) so the tests can create a bank that uses a
	// temporary file
	NewBank(String customersFile) {
		this(customersFile, Clock.systemDefaultZone());
	}

	NewBank(String customersFile, Clock clock) {
		this.clock = clock;
		customers = new HashMap<>();
		database = new CustomerDatabase(customersFile);
		// on start: if the file exists, load it and move on;
		// if not (first run), create it from the test data
		if (database.exists()) {
			try {
				customers = database.load();
				// keep nextId above every ID already used, so IDs stay unique
				for (Customer c : customers.values()) {
					if (c.getId() >= nextId) {
						nextId = c.getId() + 1;
					}
				}
			} catch (IOException e) {
				canSave = false;
				System.out.println("ERROR: could not load customers: " + e.getMessage());
				System.out.println("Saving is switched off so " + customersFile + " is not overwritten. "
						+ "Fix or remove the file and restart the server.");
			}
		} else {
			addTestData();
			try {
				database.save(customers);
			} catch (IOException e) {
				System.out.println("ERROR: could not create " + customersFile + ": " + e.getMessage());
			}
		}
	}

	private void addTestData() {
		// test customers get IDs 1-3 and have no full name or password
		Customer bhagy = new Customer(nextId++, "", null);
		Account bhagySavings = new Account("Savings", 1000.0);
		bhagySavings.addTransaction(new Transaction(1000.0, LocalDate.now(clock), "Opening"));
		bhagy.addAccount(bhagySavings);
		customers.put("Bhagy", bhagy);

		Customer christina = new Customer(nextId++, "", null);
		Account christinaSavings = new Account("Savings", 1500.0);
		christinaSavings.addTransaction(new Transaction(1500.0, LocalDate.now(clock), "Opening"));
		christina.addAccount(christinaSavings);
		customers.put("Christina", christina);

		Customer john = new Customer(nextId++, "", null);
		Account johnCheque = new Account("Cheque", 250.0);
		johnCheque.addTransaction(new Transaction(250.0, LocalDate.now(clock), "Opening"));
		john.addAccount(johnCheque);
		customers.put("John", john);
	}

	public static NewBank getBank() {
		return bank;
	}

	// returns a CustomerID only if the username exists AND the password matches,
	// otherwise null
	public synchronized CustomerID checkLogInDetails(String userName, String password) {
		Customer customer = customers.get(userName);
		if (customer != null && customer.checkPassword(password)) {
			return new CustomerID(userName);
		}
		return null;
	}

	// REGISTER: adds a new customer (with no accounts and a unique ID) to the
	// customers HashMap
	// and saves it. The client handler asks for each detail on its own line, so
	// names and
	// passwords can contain spaces.
	// Returns "SUCCESS", or "FAIL: <reason>" so the customer knows what to fix.
	public synchronized String registerCustomer(String fullName, String userName, String password) {
		if (fullName == null || userName == null || password == null) {
			return "FAIL: Full name, username and password are all required";
		}
		fullName = fullName.trim();
		userName = userName.trim();
		if (fullName.isEmpty()) {
			return "FAIL: Full name cannot be empty";
		}
		if (userName.isEmpty() || userName.contains(" ")) {
			return "FAIL: Username cannot be empty or contain spaces";
		}
		if (password.isEmpty()) {
			return "FAIL: Password cannot be empty";
		}
		// reject every delimiter used by the plain-text customer and transaction format
		if (containsFileSeparator(fullName) || containsFileSeparator(userName) || containsFileSeparator(password)) {
			return "FAIL: Full name, username and password cannot contain file separators (, ; # | ~ :)";
		}
		if (customers.containsKey(userName)) {
			return "FAIL: Username " + userName + " is already taken";
		}
		if (!canSave) {
			return "FAIL: Customer data could not be loaded, so new customers cannot be saved right now";
		}
		Customer newCustomer = new Customer(nextId++, fullName, password);
		customers.put(userName, newCustomer);
		String saveError = saveCustomers();
		if (saveError != null) {
			// undo, so we never tell the customer they're registered when they'd be lost on
			// restart
			customers.remove(userName);
			return saveError;
		}
		return "SUCCESS";
	}

	// commands from the NewBank customer are processed in this method
	public synchronized String processRequest(CustomerID customer, String request) {
		if (customer == null || request == null || !customers.containsKey(customer.getKey())) {
			return "FAIL: Unknown command";
		}
		String trimmed = request.trim();
		if (trimmed.isEmpty()) {
			return "FAIL: Unknown command";
		}
		String[] parts = trimmed.split("\\s+");
		String command = parts[0];
		switch (command) {
			case "SHOWMYACCOUNTS" :
				return showMyAccounts(customer);
			case "NEWACCOUNT" :
				return newAccount(customer, parts);
			case "SHOWACTIVITY" :
				return showActivity(customer, parts);
			case "SHOWACCOUNTDETAILS" :
				return showAccountDetails(customer, parts);
			case "TRANSFER" :
				return transfer(customer, parts);
			default :
				return "FAIL: Unknown command";
		}
	}

	private String showMyAccounts(CustomerID customer) {
		return (customers.get(customer.getKey())).accountsToString();
	}

	// NEWACCOUNT <Type>: opening balance is always 0
	private String newAccount(CustomerID customerID, String[] parts) {
		if (parts.length != 2) {
			return "FAIL: Type NEWACCOUNT <Type>, for example NEWACCOUNT Savings";
		}
		String canonicalType = canonicalAccountType(parts[1]);
		if (canonicalType == null) {
			return "FAIL: Account type must be Savings, Cheque or Investment";
		}
		Customer customer = customers.get(customerID.getKey());
		if (customer.hasAccountType(canonicalType)) {
			return "FAIL: You already have a " + canonicalType + " account";
		}
		if (!canSave) {
			return "FAIL: Customer data could not be loaded, so changes cannot be saved right now";
		}
		Account account = new Account(canonicalType, 0.0);
		account.addTransaction(new Transaction(0.0, LocalDate.now(clock), "Opening"));
		customer.addAccount(account);
		String saveError = saveCustomers();
		if (saveError != null) {
			customer.getAccounts().remove(account);
			return saveError;
		}
		return "SUCCESS";
	}

	// SHOWACTIVITY <AccountType>
	private String showActivity(CustomerID customerID, String[] parts) {
		if (parts.length != 2) {
			return "FAIL: Type SHOWACTIVITY <AccountType>, for example SHOWACTIVITY Savings";
		}
		Customer customer = customers.get(customerID.getKey());
		Account account = customer.getAccount(parts[1]);
		if (account == null) {
			return "FAIL: Account " + parts[1] + " not found";
		}
		if (account.getTransactions().isEmpty()) {
			return "No transactions";
		}
		String result = "";
		for (Transaction t : account.getTransactions()) {
			if (!result.isEmpty()) {
				result += "; ";
			}
			result += t.toString();
		}
		return result;
	}

	private String canonicalAccountType(String type) {
		if (type == null) {
			return null;
		}
		for (String valid : VALID_ACCOUNT_TYPES) {
			if (valid.equalsIgnoreCase(type)) {
				return valid;
			}
		}
		return null;
	}

	// Keep SHOWMYACCOUNTS compatible; use this command to discover account numbers.
	private String showAccountDetails(CustomerID customerID, String[] parts) {
		if (parts.length != 1) {
			return "FAIL: Type SHOWACCOUNTDETAILS";
		}
		String result = "";
		for (Account account : customers.get(customerID.getKey()).getAccounts()) {
			if (!result.isEmpty()) {
				result += "; ";
			}
			result += account.getAccountName() + " | " + account.getAccountNumber() + " | " + account.getStatus()
					+ " | " + Account.format(account.balanceAmount());
		}
		return result;
	}

	// All commands hold the bank lock, so no request observes half a transfer.
	private String transfer(CustomerID customerID, String[] parts) {
		if (parts.length != 4) {
			return "FAIL: Type TRANSFER <FromType> <ReceivingAccountNumber> <Amount>";
		}
		BigDecimal amount;
		try {
			BigDecimal parsed = new BigDecimal(parts[3]);
			if (parsed.signum() <= 0 || parsed.compareTo(new BigDecimal("999999999999.99")) > 0) {
				return "FAIL: Amount must be between 0.01 and 999999999999.99";
			}
			amount = parsed.setScale(2, RoundingMode.UNNECESSARY);
		} catch (NumberFormatException | ArithmeticException e) {
			return "FAIL: Amount must be a positive number with at most 2 decimal places";
		}
		Customer payer = customers.get(customerID.getKey());
		Account from = payer.getAccount(parts[1]);
		if (from == null) {
			return "FAIL: Source account not found";
		}
		Account to = null;
		Customer receiver = null;
		for (Customer candidate : customers.values()) {
			for (Account account : candidate.getAccounts()) {
				if (parts[2].equals(account.getAccountNumber())) {
					to = account;
					receiver = candidate;
				}
			}
		}
		if (to == null) {
			return "FAIL: Receiving account not found";
		}
		if (receiver == payer) {
			return "FAIL: Receiving account must belong to another customer";
		}
		if (from.getStatus() != Account.Status.ACTIVE || to.getStatus() != Account.Status.ACTIVE) {
			return "FAIL: Both accounts must be Active";
		}
		if (from.balanceAmount().compareTo(amount) < 0) {
			return "FAIL: Not enough money";
		}
		if (!canSave) {
			return "FAIL: Customer data could not be loaded";
		}
		BigDecimal fromBefore = from.balanceAmount();
		BigDecimal toBefore = to.balanceAmount();
		int fromCount = from.getTransactions().size();
		int toCount = to.getTransactions().size();
		LocalDate date = LocalDate.now(clock);
		from.recordTransfer(amount.negate(), date, to.getAccountNumber());
		to.recordTransfer(amount, date, from.getAccountNumber());
		String error = saveCustomers();
		if (error != null) {
			from.restore(fromBefore, fromCount);
			to.restore(toBefore, toCount);
			return error;
		}
		return "SUCCESS";
	}

	private boolean containsFileSeparator(String value) {
		return value.chars().anyMatch(c -> ",;#|~:".indexOf(c) >= 0);
	}

	// returns null on success, or a FAIL message on failure
	private String saveCustomers() {
		try {
			database.save(customers);
			return null;
		} catch (IOException e) {
			System.out.println("ERROR: could not save customers: " + e.getMessage());
			return "FAIL: Your details could not be saved, please try again later";
		}
	}

}
