package cccustom.stripesend;

import java.io.Console;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import org.ini4j.InvalidFileFormatException;
import org.ini4j.Wini;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.ChargeCollection;
import com.stripe.model.Customer;
import com.xenixi.orangejuice.main.OJ;

/**
 * Custom integration Server - CambridgeCraft
 *
 */
//NOTE - ADD REAL API KEYS
public class App {
	// static File sent = new File("donatorsSent.lst");

	public static void main(String[] args) {
		System.out.println(
				"Starting StripeSendgrid Integration Server\nNote:No affiliation with either of these companies - a custom server for CambridgeCraft donations management and ranks.");
		System.out.println("Beginning process...");

		/*
		 * if (!sent.exists()) { try { sent.createNewFile(); } catch (IOException e) {
		 * System.err.println("Error while creating csv... exiting..."); System.exit(0);
		 * } }
		 */
		File emails = new File("admins.lst");
		try {
			if (!emails.exists()) {

				emails.createNewFile();

			}
		} catch (IOException w) {
			w.printStackTrace();
			System.exit(0);
		}
		// edit THIS!
		Scanner ver = new Scanner(System.in);
		Console con = System.console();
		String pass = "";
		if (args.length > 1 && args[0].equalsIgnoreCase("-v")) {
			System.out.println("VERIFCATION: USING ARGS");
			pass = args[1];
		} else {
			if (!(con == null)) {
				con.printf("VERIFCATION: IMPORTANT: ENTER API PASSCODE:%n");

				pass = new String(con.readPassword());
			} else {
				System.err.println("Unable to get console...");
				System.err.println("WARNING: CONSOLE RUNNING WITH ECHO ENABLED. PASSWORD WILL SHOW IN CONSOLE LOGS.");
				System.out.println("VERIFCATION: IMPORTANT: ENTER API PASSCODE:");
				pass = ver.nextLine();
			}
		}
		ver.close();

		try {
			init(emails, pass);
		} catch (FileNotFoundException e) {
			System.err.println(
					"Program execution encountered and error - exiting...\nnote to development: actually tell user what the error is");
			System.out.println("FILE NOT FOUND");
		} catch (StripeException b) {
			System.out.println("STRIPE ERROR");
			b.printStackTrace();
		} catch (InterruptedException x) {
			System.out.println("INTERRUPTED THREAD");
			x.printStackTrace();
		} catch (Exception o) {
			o.printStackTrace();
		}
	}

	public static void init(File emailsFile, String passcode)
			throws StripeException, InterruptedException, UnirestException, InvalidFileFormatException, IOException {
		//////////////
		String stripeAPI = "";
		String sendGridAPI = "";

		final File API_STORE = new File("keys.ini");
		if (!API_STORE.exists()) {
			API_STORE.createNewFile();
		}

		Wini wini = new Wini(API_STORE);
		try {
			stripeAPI = OJ.decrypt(wini.get("API_KEYS", "stripe"), passcode);
			sendGridAPI = OJ.decrypt(wini.get("API_KEYS", "sendgrid"), passcode);
			System.out.println("API keys entered");
		} catch (Exception e) {
			System.err.println("VERIFCATION FAILED: ABORTING...");

		}

		/////////////

		Stripe.apiKey = stripeAPI;
		SendGrid sg = new SendGrid(sendGridAPI);

		HashMap<String, Map> allCusts = new HashMap<String, Map>();

		while (true) {

			System.out.println("Reading..");

			HashMap<String, Object> params = new HashMap<String, Object>();
			params.put("limit", 50);
			ChargeCollection charges = Charge.list(params);

			System.out.println("number in list " + charges.getData().size());
			int failed = 0;
			for (Charge c : charges.getData()) {
				if (c != null) {
					String cust = c.getCustomer();
					if (!c.getRefunded() && c.getPaid()) {
						Customer customer = Customer.retrieve(cust);
						String email = customer.getEmail();

						// String itemName = c.getDescription().substring(3);

						Map<String, String> customerEntity = new HashMap<String, String>();

						Random r = new Random();

						StringBuilder sb = new StringBuilder();
						for (int a = 0; a < 8; a++) {
							sb.append(r.nextInt(9));
						}
						System.err.println("GEN ******" + sb.toString().substring(6));

						String itemName = c.getDescription(); // invoice.getBillingReason() + " | " +
																// invoice.getLines().getData().get(4);

						customerEntity.put("id", c.getId());
						customerEntity.put("item", itemName);
						// this could be a tad more efficient, though it shoudln't be an issue for this
						// use case.
						customerEntity.put("verify", sb.toString());
						customerEntity.put("email", email);
						String price = Long.toString(c.getAmount());
						String finalFormatPrice = price.substring(0, price.length() - 2) + "."
								+ price.substring(price.length() - 2);
						customerEntity.put("amount", finalFormatPrice);

						allCusts.put(c.getId(), customerEntity);
					} else {
						failed++;
					}
				} else {
					System.out.println("Null obj");
				}

			}
			int i = 0;
			System.out.println("Provided (Success) = " + allCusts.keySet().size() + " | (FAILED): " + failed + " | ");
			for (String id : allCusts.keySet()) {
				System.out.println("----- " + i + " ----------------------");
				System.out.println("Email: " + allCusts.get(id).get("email"));
				System.out.println("Item: " + allCusts.get(id).get("item"));
				System.out.println("ID: " + allCusts.get(id).get("id"));
				System.out.println("--------------------------------------");
				i++;

			}

			for (String id : allCusts.keySet()) {

				Charge c = Charge.retrieve(id);
				System.out.println("amt " + c.getAmount());

				Map<String, String> metaCollected = c.getMetadata();

				if (!metaCollected.containsKey("mID")) {

					HttpResponse<String> response = Unirest.put("https://api.sendgrid.com/v3/marketing/contacts")
							.header("authorization",
									"Bearer "+sendGridAPI+"")
							.header("content-type", "application/json").body("{\"contacts\":[{\"email\":\""
									+ allCusts.get(id).get("email") + "\",\"custom_fields\":{}}]}")
							.asString();

					System.out.println(
							"Sending to SendGrid: ID: " + id + " | with email " + allCusts.get(id).get("email"));

					System.out.println("Sending mail...");

					/// Send emails to both client and self
					// switch this to new email once I create it.
					// ********EDITING AREA
					String emailAdmin = "testing.cambridgecraftofficial@xenixi.com",
							emailClient = (String) allCusts.get(id).get("email"), fromName = "CambridgeCraft";

					ArrayList<String> adminEmails = new ArrayList<String>();

					File emails = emailsFile;
					try {
						if (!emails.exists()) {

							emails.createNewFile();

						}
						Scanner scan = new Scanner(emails);
						while (scan.hasNextLine()) {
							adminEmails.add(scan.nextLine());

						}
						scan.close();

					} catch (IOException e) {
						e.printStackTrace();
					}

					//////////////////////////////////////////////////
					// sending to client
					Email from = new Email(emailAdmin);
					from.setName(fromName);
					String subject = "[TESTMODE] CambridgeCraft Verification Code";
					Email to = new Email(emailClient);
					StringBuilder sb2 = new StringBuilder();

					for (String adminEmail : adminEmails) {
						sb2.append(" | ");
						sb2.append(adminEmail);
					}
					Content emailContent = new Content("text/plain", "[TESTMODE] Thanks for your donation! \nPlease contact "
							+ adminEmails.get(0)
							+ " with your email, username (Java/Realm/Both), and the security code below:\n \nDon't share this code with anyone except the admin email listed above or other certified admin emails: "
							+ allCusts.get(id).get("verify")
							+ "\n \n \n \nOfficially Affiliated with xenixi.com | Additional Admin Emails: \n[TESTMODE]"
							+ sb2.toString());

					// Mail mail = new Mail(from, subject, to, emailContent);
					Mail mail = new Mail(from, subject, to, emailContent);

					Request req = new Request();
					try {
						req.setMethod(Method.POST);
						req.setEndpoint("mail/send");
						req.setBody(mail.build());
						Response resp = sg.api(req);
						System.err.println("Sent email to client**");
						// do something with response maybe..
					} catch (IOException ep) {
						ep.printStackTrace();
					}
					// sending to admin

					for (String adminEmailListed : adminEmails) {
						Email aFrom = new Email(emailAdmin);
						aFrom.setName("CCraft Admins");
						String aSubject = "[TESTMODE] A new user verifcation has been generated.";
						Email aTo = new Email(adminEmailListed);
						Content aEmailContent = new Content("text/plain", "[TESTMODE] A new donation has been made:\nEmail: '"
								+ allCusts.get(id).get("email") + "'\nCustomerID: '" + allCusts.get(id).get("id")
								+ "'\nItem: '" + allCusts.get(id).get("item") + "'\nVerifyCode: '"
								+ allCusts.get(id).get("verify") + "'\nAmount: '$" + allCusts.get(id).get("amount")
								+ "'\n \nThank you.\n\nThis email is intended to be received by CambridgeCraft administrators only. If you have received this email by mistake, report immediately to an administrator. YOU ARE '"
								+ adminEmailListed
								+ "' \n \n \nOfficially Affiliated with xenixi.com | Admin Emails: \n[TESTMODE]"
								+ sb2.toString());
						Mail aMail = new Mail(aFrom, aSubject, aTo, aEmailContent);

						Request aReq = new Request();

						try {

							aReq.setMethod(Method.POST);
							aReq.setEndpoint("mail/send");
							aReq.setBody(aMail.build());

							Response aResp = sg.api(aReq);
							System.err.println("Sent email to admin***");
						} catch (IOException ioe) {
							ioe.printStackTrace();
						}
					}

					/// ********************************************///

					Map<String, Object> chargeParams = new HashMap<String, Object>();
					Map<String, String> metadata = new HashMap<String, String>();
					metadata.put("mID", id);
					chargeParams.put("metadata", metadata);
					Charge updated = c.update(chargeParams);

					// METADATA ADDED TO CHARGE WITH NAME 'mID' AFTER SUCCESSFUL EMAIL PROCESSING

				} else {
					System.out.println("ALREADY ADDED |||| ID: " + id + " | with email "
							+ allCusts.get(id).get("email" + "  already added - skipping......"));

				}

			}

			// Thread.sleep(3500);

		}

	}
}
