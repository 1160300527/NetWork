package SR;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class TestSend {
	public static void main(String args[]) throws UnknownHostException {
		Client send = new Client(80);
		send.startSend(InetAddress.getLocalHost(),88,"src/SR/read.txt");
	}
}
