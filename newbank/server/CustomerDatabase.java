package newbank.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashMap;

/**
 * Saves and loads customers to and from a plain text file.
 *
 * File format: one customer per line
 *   id,username,full name,password,AccountName:balance[#txs];AccountName:balance[#txs]
 * e.g.
 *   1,Bhagy,,,Main:1000.0                 (test customers have no full name or password)
 *   4,asmith,Alice Smith,secret,          (registered customer with no accounts yet)
 *   5,bob,Bob,pw,Savings:0.0#2026-11-17~0.0~Opening
 *
 * Optional transaction list after #: date~amount~beneficiary|date~amount~beneficiary
 *
 * Both methods throw IOException when something goes wrong, so the caller (NewBank)
 * can decide what to do instead of carrying on as if everything worked.
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

	// writes every customer to a temporary file first, then swaps it in, so a failed write
	// never leaves a half-written customers file behind
	public void save(HashMap<String,Customer> customers) throws IOException {
		Path target = new File(fileName).toPath();
		if(Files.isDirectory(target)) {
			throw new IOException(fileName + " is a directory");
		}
		Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), "customers", ".tmp");
		try {
			try(PrintWriter file = new PrintWriter(new FileWriter(temp.toFile()))) {
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
						if(!a.getTransactions().isEmpty()) {
							accounts += "#" + transactionsToString(a);
						}
					}
					file.println(c.getId() + "," + userName + "," + fullName + "," + password + "," + accounts);
				}
				// PrintWriter never throws on println; it only records that an error happened,
				// so we have to ask it, otherwise a failed write would look like a success
				if(file.checkError()) {
					throw new IOException("error while writing " + fileName);
				}
			}
			try {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch(AtomicMoveNotSupportedException e) {
				Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	// reads the whole file into a new HashMap (the reverse of save).
	// The map is only returned if every line was read successfully, so a bad file
	// never leaves the bank with half of its customers.
	public HashMap<String,Customer> load() throws IOException {
		HashMap<String,Customer> customers = new HashMap<>();
		try(BufferedReader file = new BufferedReader(new FileReader(fileName))) {
			String line;
			int lineNumber = 0;
			while((line = file.readLine()) != null) {
				lineNumber++;
				if(line.trim().isEmpty()) {
					continue;
				}
				try {
					// -1 keeps empty fields, e.g. "4,asmith,Alice Smith,secret," has an empty accounts field
					// fields: [0] id, [1] username, [2] full name, [3] password, [4] accounts
					String[] fields = line.split(",", -1);
					Customer c = new Customer(Integer.parseInt(fields[0]), fields[2], fields[3]);
					if(!fields[4].isEmpty()) {
						for(String account : fields[4].split(";")) {
							c.addAccount(parseAccount(account));
						}
					}
					customers.put(fields[1], c);
				} catch(NumberFormatException | ArrayIndexOutOfBoundsException | java.time.format.DateTimeParseException e) {
					// a missing field, bad number or bad date means the file is damaged
					throw new IOException("line " + lineNumber + " of " + fileName + " is not valid: " + line);
				}
			}
		}
		return customers;
	}

	private String transactionsToString(Account account) {
		String result = "";
		for(Transaction t : account.getTransactions()) {
			if(!result.isEmpty()) {
				result += "|";
			}
			result += t.getDate() + "~" + t.getAmount() + "~" + t.getBeneficiary();
		}
		return result;
	}

	// AccountName:balance  or  AccountName:balance#date~amount~beneficiary|...
	private Account parseAccount(String account) {
		String[] nameAndRest = account.split(":", 2);
		String accountName = nameAndRest[0];
		String balanceAndTxs = nameAndRest[1];
		int hash = balanceAndTxs.indexOf('#');
		double balance;
		String txPart = null;
		if(hash < 0) {
			balance = Double.parseDouble(balanceAndTxs);
		} else {
			balance = Double.parseDouble(balanceAndTxs.substring(0, hash));
			txPart = balanceAndTxs.substring(hash + 1);
		}
		Account a = new Account(accountName, balance);
		if(txPart != null && !txPart.isEmpty()) {
			for(String tx : txPart.split("\\|")) {
				String[] fields = tx.split("~", 3);
				a.addTransaction(new Transaction(
						accountName,
						Double.parseDouble(fields[1]),
						LocalDate.parse(fields[0]),
						fields[2]));
			}
		}
		return a;
	}
}
