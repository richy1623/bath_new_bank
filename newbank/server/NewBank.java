package newbank.server;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;

public class NewBank {

	// customers are saved to this plain text file (in the folder the server is started from,
	// which is the project folder when you follow the README)
	private static final String CUSTOMERS_FILE = "customers_database.txt";

	private static final NewBank bank = new NewBank(CUSTOMERS_FILE);
	private HashMap<String,Customer> customers;
	private CustomerDatabase database;
	// false if the file exists but couldn't be loaded. Saving is then switched off,
	// so the empty customer list in memory never overwrites the real file.
	private boolean canSave = true;
	// the ID the next registered customer will get (one more than the highest ID so far)
	private int nextId = 1;

	// only these account types may be created with NEWACCOUNT
	private static final String[] VALID_ACCOUNT_TYPES = {"Savings", "Cheque", "Investment"};

	// package-private (not private) so the tests can create a bank that uses a temporary file
	NewBank(String customersFile) {
		customers = new HashMap<>();
		database = new CustomerDatabase(customersFile);
		// on start: if the file exists, load it and move on;
		// if not (first run), create it from the test data
		if(database.exists()) {
			try {
				customers = database.load();
				// keep nextId above every ID already used, so IDs stay unique
				for(Customer c : customers.values()) {
					if(c.getId() >= nextId) {
						nextId = c.getId() + 1;
					}
				}
			} catch(IOException e) {
				canSave = false;
				System.out.println("ERROR: could not load customers: " + e.getMessage());
				System.out.println("Saving is switched off so " + customersFile + " is not overwritten. "
						+ "Fix or remove the file and restart the server.");
			}
		}
		else {
			addTestData();
			try {
				database.save(customers);
			} catch(IOException e) {
				System.out.println("ERROR: could not create " + customersFile + ": " + e.getMessage());
			}
		}
	}

	private void addTestData() {
		// test customers get IDs 1-3 and have no full name or password
		Customer bhagy = new Customer(nextId++, "", null);
		bhagy.addAccount(new Account("Main", 1000.0));
		customers.put("Bhagy", bhagy);

		Customer christina = new Customer(nextId++, "", null);
		christina.addAccount(new Account("Savings", 1500.0));
		customers.put("Christina", christina);

		Customer john = new Customer(nextId++, "", null);
		john.addAccount(new Account("Checking", 250.0));
		customers.put("John", john);
	}
	
	public static NewBank getBank() {
		return bank;
	}
	
	// returns a CustomerID only if the username exists AND the password matches, otherwise null
	public synchronized CustomerID checkLogInDetails(String userName, String password) {
		Customer customer = customers.get(userName);
		if(customer != null && customer.checkPassword(password)) {
			return new CustomerID(userName);
		}
		return null;
	}

	// REGISTER: adds a new customer (with no accounts and a unique ID) to the customers HashMap
	// and saves it. The client handler asks for each detail on its own line, so names and
	// passwords can contain spaces.
	// Returns "SUCCESS", or "FAIL: <reason>" so the customer knows what to fix.
	public synchronized String registerCustomer(String fullName, String userName, String password) {
		if(fullName == null || userName == null || password == null) {
			return "FAIL: Full name, username and password are all required";
		}
		fullName = fullName.trim();
		userName = userName.trim();
		if(fullName.isEmpty()) {
			return "FAIL: Full name cannot be empty";
		}
		if(userName.isEmpty() || userName.contains(" ")) {
			return "FAIL: Username cannot be empty or contain spaces";
		}
		if(password.isEmpty()) {
			return "FAIL: Password cannot be empty";
		}
		// commas are not allowed because the customers file uses them to separate fields
		if(fullName.contains(",") || userName.contains(",") || password.contains(",")) {
			return "FAIL: Full name, username and password cannot contain commas";
		}
		if(customers.containsKey(userName)) {
			return "FAIL: Username " + userName + " is already taken";
		}
		if(!canSave) {
			return "FAIL: Customer data could not be loaded, so new customers cannot be saved right now";
		}
		customers.put(userName, new Customer(nextId++, fullName, password));
		try {
			database.save(customers);
		} catch(IOException e) {
			// undo, so we never tell the customer they're registered when they'd be lost on restart
			customers.remove(userName);
			System.out.println("ERROR: could not save customers: " + e.getMessage());
			return "FAIL: Your details could not be saved, please try again later";
		}
		return "SUCCESS";
	}

	// commands from the NewBank customer are processed in this method
	public synchronized String processRequest(CustomerID customer, String request) {
		if(request == null || !customers.containsKey(customer.getKey())) {
			return "FAIL";
		}
		String trimmed = request.trim();
		if(trimmed.isEmpty()) {
			return "FAIL";
		}
		String[] parts = trimmed.split("\\s+");
		String command = parts[0];
		switch(command) {
		case "SHOWMYACCOUNTS" :
			return showMyAccounts(customer);
		case "NEWACCOUNT" :
			return newAccount(customer, parts);
		case "SHOWACTIVITY" :
			return showActivity(customer, parts);
		default :
			return "FAIL";
		}
	}
	
	private String showMyAccounts(CustomerID customer) {
		return (customers.get(customer.getKey())).accountsToString();
	}

	// NEWACCOUNT <Type>: opening balance is always 0
	private String newAccount(CustomerID customerID, String[] parts) {
		if(parts.length != 2) {
			return "FAIL: Type NEWACCOUNT <Type>, for example NEWACCOUNT Savings";
		}
		String canonicalType = canonicalAccountType(parts[1]);
		if(canonicalType == null) {
			return "FAIL: Account type must be Savings, Cheque or Investment";
		}
		Customer customer = customers.get(customerID.getKey());
		if(customer.hasAccountType(canonicalType)) {
			return "FAIL: You already have a " + canonicalType + " account";
		}
		if(!canSave) {
			return "FAIL: Customer data could not be loaded, so changes cannot be saved right now";
		}
		Account account = new Account(canonicalType, 0.0);
		account.addTransaction(new Transaction(canonicalType, 0.0, LocalDate.now(), "Opening"));
		customer.addAccount(account);
		String saveError = saveCustomers();
		if(saveError != null) {
			customer.getAccounts().remove(account);
			return saveError;
		}
		return "SUCCESS";
	}

	// SHOWACTIVITY <AccountType>
	private String showActivity(CustomerID customerID, String[] parts) {
		if(parts.length != 2) {
			return "FAIL: Type SHOWACTIVITY <AccountType>, for example SHOWACTIVITY Savings";
		}
		Customer customer = customers.get(customerID.getKey());
		Account account = customer.getAccount(parts[1]);
		if(account == null) {
			return "FAIL: Account " + parts[1] + " not found";
		}
		if(account.getTransactions().isEmpty()) {
			return "No transactions";
		}
		String result = "";
		for(Transaction t : account.getTransactions()) {
			if(!result.isEmpty()) {
				result += "\n";
			}
			result += t.toString();
		}
		return result;
	}

	private String canonicalAccountType(String type) {
		if(type == null) {
			return null;
		}
		for(String valid : VALID_ACCOUNT_TYPES) {
			if(valid.equalsIgnoreCase(type)) {
				return valid;
			}
		}
		return null;
	}

	// returns null on success, or a FAIL message on failure
	private String saveCustomers() {
		try {
			database.save(customers);
			return null;
		} catch(IOException e) {
			System.out.println("ERROR: could not save customers: " + e.getMessage());
			return "FAIL: Your details could not be saved, please try again later";
		}
	}

}
