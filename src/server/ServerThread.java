package server;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Set;


public class ServerThread extends Thread{
	private Socket server;
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
			InputStream input = client.getInputStream();
			if (input == null)
				return;
			final int bufsize = 8192;
			byte[] buf = new byte[bufsize];
			int splitbyte = 0;
			int rlen = 0;
			//读入缓冲区的总字节数，到未尾时都返回-1
			int read = input.read(buf, 0, bufsize);
			while (read > 0) {
				rlen += read;
				//\r\n\r\n为结束，避免inputStream阻塞。
				splitbyte = findHeaderEnd(buf, rlen);
				if (splitbyte > 0)
					break;
				//
				read = input.read(buf, rlen, bufsize - rlen);
			}
			ByteArrayInputStream stream = new ByteArrayInputStream(buf,0, rlen);
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
					port = 80;
				}
				sendAndreceive(buf, rlen, client, client.getInputStream(),client.getOutputStream(), address,port);	
			} catch (Exception e) {
				System.out.println("Run Exception!");
				e.printStackTrace();
			}
		} catch (Exception e) {}
	}
	
	private int findHeaderEnd(final byte[] buf, int rlen) {
		int splitbyte = 0;
		while (splitbyte + 3 < rlen) {
			//遇到换行回车
			if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n'
					&& buf[splitbyte + 2] == '\r'&& buf[splitbyte + 3] == '\n')
				return splitbyte + 4;
			splitbyte++;
		}
		return 0;
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
				if(port==80) {
					output.write(request, 0, requestLen);
					//output.write(content.getBytes());
				}else {
					output.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
					output.write(request, 0, requestLen);
					//output.write(content.getBytes());
				}
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
}
