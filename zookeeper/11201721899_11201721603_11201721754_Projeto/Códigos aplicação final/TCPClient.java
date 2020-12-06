import java.io.*;
import java.net.*;
import java.util.Date;


/**
 * Simple TCP client. Use the command "quit" to end the communication.
 *
 */
public class TCPClient {
	/** Well-known server port. */
	public static int serverPort = 9000;    
	/** Hostname. */
	public static String hostname = "localhost";

	public static void main (String args[]) throws Exception {
		// Connect to the server process running at localhhost:serverPort
		Socket s = new Socket(hostname, serverPort);

		// The next 2 lines create a output stream we can
		// write to.  (To write TO SERVER)
		OutputStream os= s.getOutputStream();
		DataOutputStream serverWriter = new DataOutputStream(os);

		// The next 2 lines create a buffer reader that
		// reads from the standard input. (to read stream FROM SERVER)
		InputStreamReader isrServer = new InputStreamReader(s.getInputStream());
		BufferedReader serverReader = new BufferedReader(isrServer);      

		//Create buffer reader to read input from user. Read the user input to string 'sentence'
		BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
		String sentence;  
		
		//Read the sentence
		
    String resposta;

    resposta = serverReader.readLine();
    System.out.println("1." + resposta);
    System.out.print("Client$ ");
    sentence = inFromUser.readLine();
    serverWriter.writeBytes(sentence +"\n");

    resposta = serverReader.readLine();
    System.out.println("2." + resposta);
    System.out.print("Client$ ");
    sentence = inFromUser.readLine();
    serverWriter.writeBytes(sentence +"\n");

    resposta = serverReader.readLine();
    System.out.println("3." + resposta);
    System.out.print("Client$ ");
    sentence = inFromUser.readLine();
    serverWriter.writeBytes(sentence +"\n");

    resposta = serverReader.readLine() + "\n" + serverReader.readLine() + "\n" + serverReader.readLine();
    System.out.println("==========================\nResposta final:\n" + resposta);

		//Close communication
		serverWriter.close();
		isrServer.close();
		s.close();
	}
}
