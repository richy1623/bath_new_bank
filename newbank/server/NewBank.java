package newbank.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

public class NewBank {

	// customers are saved to this plain text file (in the folder the server is started from,
	// which is the project folder when you follow the README)
	private static final String CUSTOMERS_FILE = "customers_database.txt";

	private static final NewBank bank = new NewBank();
	private HashMap<String,Customer> customers;
	// the ID the next registered customer will get (one more than the highest ID so far)
	private int nextId = 1;

	private NewBank() {
		customers = new HashMap<>();
		// on start: if customers_database.txt exists, load it and move on;
		// if not (first run), create it from the test data
		if(new File(CUSTOMERS_FILE).exists()) {
			loadCustomers();
		}
		else {
			addTestData();
			saveCustomers();
		}
	}

	/*
	 * File format: one customer per line
	 *   id,username,full name,password,AccountName:balance;AccountName:balance
	 * e.g.
	 *   1,Bhagy,,,Main:1000.0                 (test customers have no full name or password)
	 *   4,asmith,Alice Smith,secret,          (registered customer with no accounts yet)
	 */
	private void saveCustomers() {
		try(PrintWriter file = new PrintWriter(new FileWriter(CUSTOMERS_FILE))) {
			for(String userName : customers.keySet()) {
				Customer c = customers.get(userName);
				String fullName = (c.getFullName() == null) ? "" : c.getFullName();
				String password = (c.getPassword() == null) ? "" : c.getPassword();
				String accounts = "";
				for(Account a : c.getAccounts()) {
					if(!accounts.isEmpty()) {
						accounts += ";";
					}
					accounts += a.getAccountName() + ":" + a.getBalance();
				}
				file.println(c.getId() + "," + userName + "," + fullName + "," + password + "," + accounts);
			}
		} catch(IOException e) {
			System.out.println("Could not save " + CUSTOMERS_FILE + ": " + e.getMessage());
		}
	}

	// reads customers_database.txt back into the customers HashMap (the reverse of saveCustomers)
	private void loadCustomers() {
		try(BufferedReader file = new BufferedReader(new FileReader(CUSTOMERS_FILE))) {
			String line;
			while((line = file.readLine()) != null) {
				if(line.trim().isEmpty()) {
					continue;
				}
				// -1 keeps empty fields, e.g. "4,asmith,Alice Smith,secret," has an empty accounts field
				// fields: [0] id, [1] username, [2] full name, [3] password, [4] accounts
				String[] fields = line.split(",", -1);
				int id = Integer.parseInt(fields[0]);
				Customer c = new Customer(id, fields[2], fields[3]);
				if(!fields[4].isEmpty()) {
					for(String account : fields[4].split(";")) {
						String[] nameAndBalance = account.split(":");
						c.addAccount(new Account(nameAndBalance[0], Double.parseDouble(nameAndBalance[1])));
					}
				}
				customers.put(fields[1], c);
				// keep nextId above every ID already used, so IDs stay unique
				if(id >= nextId) {
					nextId = id + 1;
				}
			}
		} catch(IOException e) {
			System.out.println("Could not load " + CUSTOMERS_FILE + ": " + e.getMessage());
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
	
	public synchronized CustomerID checkLogInDetails(String userName, String password) {
		if(customers.containsKey(userName)) {
			return new CustomerID(userName);
		}
		return null;
	}

	// REGISTER <Full Name> <Username> <Password> : adds a new customer (with no accounts and a unique ID)
	// to the customers HashMap. The full name can be several words, e.g. REGISTER Alice Smith asmith secret,
	// so the last two words are the username and password and everything in between is the full name.
	// returns "SUCCESS", or "FAIL" if the format is wrong or the username is already taken
	// commas are not allowed because the customers file uses them to separate fields
	public synchronized String registerCustomer(String request) {
		if(request.contains(",")) {
			return "FAIL";
		}
		String[] parts = request.trim().split("\\s+");
		// need at least: REGISTER, one word of name, username, password
		if(parts.length < 4) {
			return "FAIL";
		}
		String userName = parts[parts.length - 2];
		String password = parts[parts.length - 1];
		String fullName = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length - 2));
		if(customers.containsKey(userName)) {
			return "FAIL";
		}
		customers.put(userName, new Customer(nextId++, fullName, password));
		saveCustomers();
		return "SUCCESS";
	}

	// commands from the NewBank customer are processed in this method
	public synchronized String processRequest(CustomerID customer, String request) {
		if(customers.containsKey(customer.getKey())) {
			switch(request) {
			case "SHOWMYACCOUNTS" : return showMyAccounts(customer);
			default : return "FAIL";
			}
		}
		return "FAIL";
	}
	
	private String showMyAccounts(CustomerID customer) {
		return (customers.get(customer.getKey())).accountsToString();
	}

}
