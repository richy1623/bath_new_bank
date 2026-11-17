package newbank.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CustomerDatabaseTest {

	// JUnit creates a fresh empty folder for each test and deletes it afterwards
	@TempDir
	Path tempDir;

	private String file(String name) {
		return tempDir.resolve(name).toString();
	}

	@Test
	void existsIsFalseUntilSaved() throws IOException {
		CustomerDatabase db = new CustomerDatabase(file("customers.txt"));
		assertFalse(db.exists());
		db.save(new HashMap<>());
		assertTrue(db.exists());
	}

	@Test
	void savedCustomersLoadBackTheSame() throws IOException {
		HashMap<String, Customer> customers = new HashMap<>();
		Customer bhagy = new Customer(1, "", null);
		bhagy.addAccount(new Account("Main", 1000.0));
		bhagy.addAccount(new Account("Savings", 50.5));
		customers.put("Bhagy", bhagy);
		customers.put("asmith", new Customer(4, "Alice Smith", "my secret"));

		CustomerDatabase db = new CustomerDatabase(file("customers.txt"));
		db.save(customers);
		HashMap<String, Customer> loaded = db.load();

		assertEquals(2, loaded.size());
		Customer alice = loaded.get("asmith");
		assertEquals(4, alice.getId());
		assertEquals("Alice Smith", alice.getFullName());
		assertTrue(alice.checkPassword("my secret"));
		assertTrue(alice.getAccounts().isEmpty());

		Customer loadedBhagy = loaded.get("Bhagy");
		assertEquals(1, loadedBhagy.getId());
		assertTrue(loadedBhagy.checkPassword(""));
		assertEquals(2, loadedBhagy.getAccounts().size());
		assertEquals("Savings", loadedBhagy.getAccounts().get(1).getAccountName());
		assertEquals(50.5, loadedBhagy.getAccounts().get(1).getBalance());
	}

	@Test
	void emptyFileLoadsNoCustomers() throws IOException {
		Files.writeString(tempDir.resolve("customers.txt"), "");
		assertTrue(new CustomerDatabase(file("customers.txt")).load().isEmpty());
	}

	@Test
	void lineWithMissingFieldsFailsToLoad() throws IOException {
		Files.writeString(tempDir.resolve("customers.txt"), "1,Bhagy,,,Main:1000.0\n2,Christina\n");
		IOException e = assertThrows(IOException.class, () -> new CustomerDatabase(file("customers.txt")).load());
		assertTrue(e.getMessage().contains("line 2"));
	}

	@Test
	void lineWithBadNumberFailsToLoad() throws IOException {
		Files.writeString(tempDir.resolve("customers.txt"), "one,Bhagy,,,Main:1000.0\n");
		assertThrows(IOException.class, () -> new CustomerDatabase(file("customers.txt")).load());
	}

	@Test
	void saveFailureIsReported() {
		// a folder can't be written to as if it were a file, so the save must fail
		// loudly
		CustomerDatabase db = new CustomerDatabase(tempDir.toString());
		assertThrows(IOException.class, () -> db.save(new HashMap<>()));
	}
}
