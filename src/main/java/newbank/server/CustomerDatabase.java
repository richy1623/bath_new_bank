package newbank.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

/**
 * Saves and loads customers to and from a plain text file.
 *
 * File format: one customer per line id,username,full
 * name,password,AccountName:balance;AccountName:balance e.g.
 * 1,Bhagy,,,Main:1000.0 (test customers have no full name or password)
 * 4,asmith,Alice Smith,secret, (registered customer with no accounts yet)
 *
 * Both methods throw IOException when something goes wrong, so the caller
 * (NewBank) can decide what to do instead of carrying on as if everything
 * worked.
 */
public class CustomerDatabase {

	private final String fileName;

	public CustomerDatabase(String fileName) {
		this.fileName = fileName;
	}

	public String getFileName() {
		return fileName;
	}

	public boolean exists() {
		return new File(fileName).exists();
	}

	// writes every customer to the file, replacing what was there
	public void save(HashMap<String, Customer> customers) throws IOException {
		try (PrintWriter file = new PrintWriter(new FileWriter(fileName))) {
			for (String userName : customers.keySet()) {
				Customer c = customers.get(userName);
				String fullName = (c.getFullName() == null) ? "" : c.getFullName();
				String password = (c.getPassword() == null) ? "" : c.getPassword();
				String accounts = "";
				for (Account a : c.getAccounts()) {
					if (!accounts.isEmpty()) {
						accounts += ";";
					}
					accounts += a.getAccountName() + ":" + a.getBalance();
				}
				file.println(c.getId() + "," + userName + "," + fullName + "," + password + "," + accounts);
			}
			// PrintWriter never throws on println; it only records that an error happened,
			// so we have to ask it, otherwise a failed write would look like a success
			if (file.checkError()) {
				throw new IOException("error while writing " + fileName);
			}
		}
	}

	// reads the whole file into a new HashMap (the reverse of save).
	// The map is only returned if every line was read successfully, so a bad file
	// never leaves the bank with half of its customers.
	public HashMap<String, Customer> load() throws IOException {
		HashMap<String, Customer> customers = new HashMap<>();
		try (BufferedReader file = new BufferedReader(new FileReader(fileName))) {
			String line;
			int lineNumber = 0;
			while ((line = file.readLine()) != null) {
				lineNumber++;
				if (line.trim().isEmpty()) {
					continue;
				}
				try {
					// -1 keeps empty fields, e.g. "4,asmith,Alice Smith,secret," has an empty
					// accounts field
					// fields: [0] id, [1] username, [2] full name, [3] password, [4] accounts
					String[] fields = line.split(",", -1);
					Customer c = new Customer(Integer.parseInt(fields[0]), fields[2], fields[3]);
					if (!fields[4].isEmpty()) {
						for (String account : fields[4].split(";")) {
							String[] nameAndBalance = account.split(":");
							c.addAccount(new Account(nameAndBalance[0], Double.parseDouble(nameAndBalance[1])));
						}
					}
					customers.put(fields[1], c);
				} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
					// a missing field or a bad number means the file is damaged
					throw new IOException("line " + lineNumber + " of " + fileName + " is not valid: " + line);
				}
			}
		}
		return customers;
	}
}
