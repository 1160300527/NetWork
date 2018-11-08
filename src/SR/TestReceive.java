package SR;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class TestReceive {
	public static void main(String args[]) throws UnknownHostException {
		Client receive = new Client(88);
		receive.startReceive();
		receive.writeFile("src/SR/write.txt");
		receive.startSend(InetAddress.getLocalHost(),80,"src/SR/read.txt");
	}
}
