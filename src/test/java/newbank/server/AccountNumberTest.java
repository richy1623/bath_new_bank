package newbank.server;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountNumberTest {
	@TempDir
	Path dir;

	@Test
	void legacyAccountsGetStableNumbersAndActiveStatus() throws IOException {
		Path file = dir.resolve("customers.txt");
		Files.writeString(file, "1,alice,Alice,pw,Savings:100.3;Cheque:0.0\n2,bob,Bob,pw,Savings:0.1\n");
		CustomerDatabase db = new CustomerDatabase(file.toString());
		var customers = db.load();
		Account savings = customers.get("alice").getAccount("Savings");
		assertEquals("00000101", savings.getAccountNumber());
		assertEquals("00000102", customers.get("alice").getAccount("Cheque").getAccountNumber());
		assertEquals("00000201", customers.get("bob").getAccount("Savings").getAccountNumber());
		assertEquals(Account.Status.ACTIVE, savings.getStatus());
		savings.setStatus(Account.Status.INACTIVE);
		db.save(customers);
		Account loaded = db.load().get("alice").getAccount("Savings");
		assertEquals(savings.getAccountNumber(), loaded.getAccountNumber());
		assertEquals(Account.Status.INACTIVE, loaded.getStatus());
		assertEquals(100.3, loaded.getBalance());
	}

	@Test
	void newAccountsHaveUniqueNumbersAndDetailsExposeThem() {
		NewBank bank = new NewBank(dir.resolve("customers.txt").toString());
		bank.registerCustomer("Alice", "alice", "pw");
		CustomerID alice = bank.checkLogInDetails("alice", "pw");
		assertEquals("SUCCESS", bank.processRequest(alice, "NEWACCOUNT Savings"));
		assertEquals("SUCCESS", bank.processRequest(alice, "NEWACCOUNT Cheque"));
		assertEquals("Savings | 00000401 | ACTIVE | 0.0; Cheque | 00000402 | ACTIVE | 0.0",
				bank.processRequest(alice, "SHOWACCOUNTDETAILS"));
	}

	@Test
	void malformedMetadataAndNonFiniteBalancesAreRejected() throws IOException {
		Path file = dir.resolve("customers.txt");
		for (String account : new String[]{"Savings:NaN", "Savings:Infinity", "Savings:-1", "Savings:0:123:INVALID",
				"Savings:0:not-a-number:ACTIVE", "Savings:0:123:ACTIVE;Cheque:0:123:ACTIVE"}) {
			Files.writeString(file, "1,alice,Alice,pw," + account + "\n");
			assertThrows(IOException.class, () -> new CustomerDatabase(file.toString()).load(), account);
		}
	}
}
