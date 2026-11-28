package newbank.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.math.BigDecimal;

/**
 * Saves and loads customers to and from a plain text file.
 *
 * File format: one customer per line id,username,full
 * name,password,AccountName:balance:number:status[#txs];... e.g.
 * 1,Bhagy,,,Savings:1000.00:00000101:ACTIVE#2026-11-17~1000.0~Opening
 * 4,asmith,Alice Smith,secret, (registered customer with no accounts yet)
 * 5,bob,Bob,pw,Savings:0.0#2026-11-17~0.0~Opening
 *
 * Legacy AccountName:balance[#txs] entries are also supported. Optional
 * transaction list after #: date~amount~beneficiary|date~amount~beneficiary
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

	// writes every customer to a temporary file first, then swaps it in, so a
	// failed write
	// never leaves a half-written customers file behind
	public void save(HashMap<String, Customer> customers) throws IOException {
		Path target = new File(fileName).toPath();
		if (Files.isDirectory(target)) {
			throw new IOException(fileName + " is a directory");
		}
		Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), "customers", ".tmp");
		try {
			try (BufferedWriter file = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				for (String userName : customers.keySet()) {
					Customer c = customers.get(userName);
					String fullName = (c.getFullName() == null) ? "" : c.getFullName();
					String password = (c.getPassword() == null) ? "" : c.getPassword();
					String accounts = "";
					for (Account a : c.getAccounts()) {
						if (!accounts.isEmpty()) {
							accounts += ";";
						}
						accounts += a.getAccountName() + ":" + a.balanceAmount().toPlainString() + ":"
								+ a.getAccountNumber() + ":" + a.getStatus().name();
						if (!a.getTransactions().isEmpty()) {
							accounts += "#" + transactionsToString(a);
						}
					}
					file.write(c.getId() + "," + userName + "," + fullName + "," + password + "," + accounts + ","
							+ c.getRole().name());
					file.newLine();
				}
			}
			// Fail safely rather than risk partial replacement on unsupported file systems.
			Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	// reads the whole file into a new HashMap (the reverse of save).
	// The map is only returned if every line was read successfully, so a bad file
	// never leaves the bank with half of its customers.
	public HashMap<String, Customer> load() throws IOException {
		HashMap<String, Customer> customers = new HashMap<>();
		try (BufferedReader file = Files.newBufferedReader(Path.of(fileName), StandardCharsets.UTF_8)) {
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
					// [5] role was added later; older lines without it are customers
					if (fields.length > 5) {
						c.setRole(Customer.Role.valueOf(fields[5]));
					}
					if (!fields[4].isEmpty()) {
						for (String account : fields[4].split(";")) {
							c.addLoadedAccount(parseAccount(account));
						}
					}
					c.assignMissingAccountNumbers();
					if (customers.putIfAbsent(fields[1], c) != null) {
						throw new IllegalArgumentException("Duplicate username");
					}
				} catch (IllegalArgumentException | IndexOutOfBoundsException | ArithmeticException
						| java.time.format.DateTimeParseException e) {
					// a missing field, bad number or bad date means the file is damaged
					throw new IOException("line " + lineNumber + " of " + fileName + " is not valid: " + line);
				}
			}
		}
		HashSet<String> numbers = new HashSet<>();
		for (Customer customer : customers.values()) {
			for (Account account : customer.getAccounts()) {
				if (!numbers.add(account.getAccountNumber())) {
					throw new IOException("Duplicate account number: " + account.getAccountNumber());
				}
			}
		}
		return customers;
	}

	private String transactionsToString(Account account) {
		String result = "";
		for (Transaction t : account.getTransactions()) {
			if (!result.isEmpty()) {
				result += "|";
			}
			result += t.getDate() + "~" + t.amountValue().toPlainString() + "~" + t.getBeneficiary();
		}
		return result;
	}

	// AccountName:balance[:number:status][#date~amount~beneficiary|...]
	private Account parseAccount(String account) {
		String[] sections = account.split("#", 2);
		String[] fields = sections[0].split(":", -1);
		if (fields.length != 2 && fields.length != 4) {
			throw new IllegalArgumentException("Invalid account fields");
		}
		// Older files may contain binary floating-point artifacts with more than two
		// decimal places. Normalize those balances to cents when loading legacy data.
		Account a = new Account(fields[0], new BigDecimal(fields[1]).setScale(2, java.math.RoundingMode.HALF_UP));
		if (fields.length == 4) {
			a.setAccountNumber(fields[2]);
			a.setStatus(Account.Status.valueOf(fields[3]));
		} else {
			// saved before statuses existed: staff must review it, like a new account
			a.setStatus(Account.Status.IN_REVIEW);
		}
		String txPart = sections.length == 2 ? sections[1] : null;
		if (txPart != null && !txPart.isEmpty()) {
			for (String tx : txPart.split("\\|")) {
				String[] transaction = tx.split("~", 3);
				a.addTransaction(new Transaction(new BigDecimal(transaction[1]), LocalDate.parse(transaction[0]),
						transaction[2]));
			}
		}
		return a;
	}
}
