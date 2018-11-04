package server;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Set;


public class ServerThread extends Thread{
	private Socket client;
	private String host;
	private String address;
	private int port;
	private BlackList blackList = new BlackList("src/server/AddressBlackList.txt","src/server/UserBlackList.txt");
	private Set<String> addressBlacklist = blackList.getAddressBlackList();
	private Set<String> userBlacklist = blackList.getUserBlackList();
	private String clientAddress;
	private int clientPort;
	private Fishing fish = new Fishing();
	String refused = "HTTP/1.1 403 Forbidden\r\n" +
	          "Content-Type: text/html\r\n" +
	          "Content-Length: 23\r\n" +
	          "\r\n" +
	          "<h1>This is blacklist  </h1>";
	
	public ServerThread(Socket client) {
		this.client=client;
		this.setDaemon(true);
	}
	
	
	public void run() {
		try {
			addfish();
			//获取client输入流，从而获取客户端请求
			clientAddress = client.getLocalAddress().getHostAddress();
			clientPort = client.getPort();
			System.out.println(clientAddress);
			System.out.println(clientPort);
			client.setSoTimeout(400);
			ByteArrayOutputStream CloneResult = new ByteArrayOutputStream();
			int rlen = 0;
			InputStream input = client.getInputStream();
			if (input == null)
				return;
			ByteArrayInputStream stream = new ByteArrayInputStream(CloneResult.toByteArray());
			//InputStreamReader将字节流转化为字符流
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			String line;
			boolean flag = false;
			//行读取客户端数据
			while ((line = reader.readLine()) != null) {
				//把URL字符串里的字符变成小写的，这样在判断是不是以"host:"开头
				if (line.toLowerCase().startsWith("host:")) {
						host = line;
						flag = true;
				}
				System.out.println(host);
			}
			if (!flag) {
					client.getOutputStream().write("error!".getBytes());
					client.close();
					return;
			}
			//根据host分离出address和port
			addressAndport();
			System.out.println("address:[" + address + "]port:" + port + "\n-------------------\n");
			try {
				//创建与address、port连接的套接字 TCP连接建立
				String fishAddress = fish.fishing(address);
				if(fishAddress!=null) {
					address = fishAddress;
					//port = 80;
				}
				if(port!=80) {
					client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
					CloneResult = new ByteArrayOutputStream();
					rlen = getBack(CloneResult,client);
				}
				sendAndreceive(CloneResult.toByteArray(),rlen, client, client.getInputStream(),client.getOutputStream(), address,port);	
			} catch (Exception e) {
				System.out.println("Run Exception!");
				e.printStackTrace();
			}
		} catch (Exception e) {}
	}
	
	
	void sendAndreceive(byte[] request, int requestLen, Socket client,InputStream clientIS, OutputStream clientOS, String address, int port)throws Exception {
		byte bytes[] = new byte[1024 * 32];
		Socket socket = new Socket(address, port);
		socket.setSoTimeout(3000);
		OutputStream output = socket.getOutputStream();
		InputStream input = socket.getInputStream();
		try {
			do {
				if(addressBlacklist.contains(address)){
					System.out.println("---->>>" + address + " is in blacklist");
					OutputStream clientOutput = client.getOutputStream();
					clientOutput.write(refused.getBytes());
					break;
				}
				if(userBlacklist.contains(clientAddress)) {
					System.out.println("---->>>"+clientAddress+" is in blacklist");
					OutputStream clientOutput = client.getOutputStream();
					clientOutput.write(refused.getBytes());
					break;
				}
				output.write(request, 0, requestLen);
				int resultLen = 0;
				try {
					while ((resultLen = input.read(bytes)) != -1
							&& !client.isClosed() && !socket.isClosed()) {
						clientOS.write(bytes, 0, resultLen);
					}
				} catch (Exception e) {
					System.out.println("target Socket exception:"+ e.toString());
				}
				System.out.println("proxy requset-connect broken,socket:"+ socket.hashCode());
			} while (!client.isClosed()&& (requestLen = clientIS.read(request)) != -1);
		} catch (Exception e) {
			System.out.println("client Socket exception:" + e.toString());
		}
		System.out.println("finish,socket:" + socket.hashCode());
		output.close();
		input.close();
		clientIS.close();
		clientOS.close();
		socket.close();
		client.close();
	}
	
	public boolean addressAndport() {
		
		if (host == null)
			return false;
		//https:Host:address:port
		int start = host.indexOf(": ");
		if (start == -1)
			return false;
		//http:Host:address
		int next = host.indexOf(':', start + 2);
		if (next == -1) {
			port = 80;
			address = host.substring(start + 2);
		} else {
			address = host.substring(start + 2, next);
			port = Integer.valueOf(host.substring(next + 1));
		}
		return true;
	}
	
	
	public void addfish() {
		fish.addFishing("www.taobao.com", "jwts.hit.edu.cn");
	}
	
	
	private int getBack(ByteArrayOutputStream CloneResult,Socket client) {
		byte[] buffer = new byte[1024];
		int len = 0;
        int length = 0;
        try {
        	InputStream input = client.getInputStream();
			while ((length = input.read(buffer)) != -1) {
			    CloneResult.write(buffer, 0, length);
			    len += length;
			}
		}catch(SocketTimeoutException e) {
			return len;
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return len;
	}
	
}
