package newbank.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class NewBankClientHandler extends Thread{
	
	private NewBank bank;
	private BufferedReader in;
	private PrintWriter out;
	
	
	public NewBankClientHandler(Socket s) throws IOException {
		bank = NewBank.getBank();
		in = new BufferedReader(new InputStreamReader(s.getInputStream()));
		out = new PrintWriter(s.getOutputStream(), true);
	}
	
	// asks for a username and password until the customer logs in.
	// Returns their CustomerID, or null if they fail 3 times or disconnect.
	// Each call starts with 3 fresh attempts, so logging out and back in resets the count.
	private CustomerID logIn() throws IOException {
		// the customer gets this many tries to log in before being disconnected
		final int MAX_LOGIN_ATTEMPTS = 3;
		int failedAttempts = 0;
		CustomerID customer = null;
		while(customer == null && failedAttempts < MAX_LOGIN_ATTEMPTS) {
			// ask for user name; the user can type REGISTER <Full Name> <Username> <Password> here
			// instead to create a new customer (this doesn't count as a login attempt)
			out.println("Enter Username");
			String userName = in.readLine();
			if(userName == null) {
				break; // client disconnected
			}
			if(userName.startsWith("REGISTER")) {
				out.println(bank.registerCustomer(userName));
				continue;
			}
			// ask for password
			out.println("Enter Password");
			String password = in.readLine();
			if(password == null) {
				break; // client disconnected
			}
			out.println("Checking Details...");
			// authenticate user and get customer ID token from bank for use in subsequent requests
			customer = bank.checkLogInDetails(userName, password);
			if(customer == null) {
				failedAttempts++;
				int attemptsLeft = MAX_LOGIN_ATTEMPTS - failedAttempts;
				if(attemptsLeft > 0) {
					out.println("Log In Failed. Attempts left: " + attemptsLeft);
				}
				else {
					out.println("Log In Failed. Too many failed attempts, disconnecting.");
				}
			}
		}
		return customer;
	}

	public void run() {
		// keep getting requests from the client and processing them
		try {
			// each pass of this loop is one login session; LOGOUT goes back to the top to log in again
			while(true) {
				CustomerID customer = logIn();
				if(customer == null) {
					break; // too many failed attempts, or client disconnected
				}
				// the user is authenticated, so get requests from the user and process them
				out.println("Log In Successful. What do you want to do?");
				while(true) {
					String request = in.readLine();
					System.out.println("Request from " + customer.getKey());
					// LOGOUT ends this session (handled here, not in NewBank, because it's about the
					// connection rather than the customer's data)
					if("LOGOUT".equals(request)) {
						out.println("Log Out Successful. Goodbye " + customer.getKey() + ".");
						break;
					}
					String responce = bank.processRequest(customer, request);
					out.println(responce);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		finally {
			try {
				in.close();
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
			}
		}
	}

}
