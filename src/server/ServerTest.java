package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

public class ServerTest {
	public static void main(String args[]) {
		try {
			ServerSocket server = new ServerSocket(8080);
			while(true) {
				Socket client = server.accept();
				Thread serverThread = new ServerThread(client);
				serverThread.start();
			}
		}catch(SocketException e) {
			System.out.println("确认端口未被占用");
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
}
