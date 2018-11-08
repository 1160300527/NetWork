package SR;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Client{
	//发送端缓存
	private Map<Integer,DatagramPacket> sendBuf = new HashMap<>();
	//接收端缓存
	private Map<Integer,DatagramPacket> receiveBuf = new HashMap<>();
	private int length;
	
	private ByteArrayOutputStream receive=new ByteArrayOutputStream();
	
	private DatagramSocket socket;	
	private InetAddress address = null;
	private int port;
	private int targetPort;
	private InetAddress targetAddress;
	
	//发送窗口大小
	private int sendWin = 20;
	//接收窗口大小
	private int receiveWin = 20;
	
	//序列号最大个数
	private int maxSeq = 255;
	
	
	//发送窗口起始
	private int sendBase = 1;
	//接收口起始
	private int receiveBase = 1;
	
	private int maxData = 1200;
	
	//发送窗口下一个序列号
	private int sendNext = 1;
	
	private Time clock= new Time(this);
	
	//过期时间
	private int restTime = 3;
	private String content;
	
	public Client(int port) {
		try {
			socket = new DatagramSocket(port);
			this.port = port;
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public int send(byte[] content,int seq,InetAddress sendAddress,int sendPort,int start){
		if(content.length==0) {
			ByteArrayOutputStream Send = new ByteArrayOutputStream();
			Send.write((byte)seq);
			sendData(Send.toByteArray(),seq,sendAddress,sendPort);
			try {
				Send.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return 0;
		}
		int end = length;
		while(sendBase+sendWin>sendNext) {
			if(seq>maxSeq) {
				System.err.println("文件过大");
				clock.stop();
				send(new byte[0],0,targetAddress,targetPort,0);
				System.exit(1);
			}
			try {
				if(start<0) {
					return -1;
				}
				ByteArrayOutputStream Send = new ByteArrayOutputStream();
				//seq=sendNext
				seq = sendNext++;
				Send.write((byte)seq);
				System.out.println("正在发送Seq为："+seq+"的数据包");
				if(end-start<=maxData) {
					System.err.println("数据包总数为："+seq);
					byte[]sendcontent = Arrays.copyOfRange(content, start, end);
					Send.write(sendcontent);
					sendData(Send.toByteArray(),seq,sendAddress,sendPort);
					return -1;
				}
				else {
					byte[]sendcontent = Arrays.copyOfRange(content, start, start+maxData);
					Send.write(sendcontent);
					sendData(Send.toByteArray(),seq,sendAddress,sendPort);
					start += maxData;
				}
				Send.close();
			}catch(IOException e) {
				
			}
		}
		System.out.println("发送窗口已满，等待接收ACK移动窗口");
		return start;
	}
	
	
	
	public void sendData(byte[] send,int seq,InetAddress sendAddress,int sendPort) {
		DatagramPacket data = new DatagramPacket(send,send.length,sendAddress,sendPort);
		if(send.length>1) {
			sendBuf.put(seq, data);
			clock.setClock(seq, restTime);
		}
		if(seq%3==0&&seq>0&&send.length!=1)
			return;
		try {
			socket.send(data);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void sendTo() {
		int start = 0;
		clock.start();
		while((start =send(content.getBytes(),-1,targetAddress,targetPort,start))>=0||!sendBuf.isEmpty()) {
			getInfo(receive(100));
		}
		clock.stop();
		send(new byte[0],0,targetAddress,targetPort,0);
		System.out.println("发送已经结束");
	}
	
	
	public void timeOut(int ack) {
		try {
			socket.send(sendBuf.get(ack));
			 clock.setClock(ack, restTime);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public boolean containsAck(int ack) {
		return sendBuf.containsKey(ack);
	}
	
	
	public String getContent(String filename) {
		File file = new File(filename);
		String Content = "";
		try {
			BufferedReader reader = new BufferedReader(new FileReader(file));
			int length = (int)file.length();
			char content[] = new char[length];
			reader.read(content, 0, length);
			Content = new String(content);
			reader.close();
		} catch (FileNotFoundException e) {
			System.out.println("Don't find the file you want to send");
		} catch( IOException e) {
			
		}
		length = Content.length();
		return Content;
	}
	
	
	public void startSend(InetAddress sendAddress,int port,String filename) {
		this.targetAddress = sendAddress;
		this.targetPort = port;
		content = getContent(filename);
		System.out.println("开始发送文件："+filename);
		sendTo();
	}
	
	public void startReceive() {
		DatagramPacket data;
		while(true) {
			data = receive(100);
			if(getInfo(data)==true) {
				break;
			}
		}
		System.err.println("接收已结束，结束数据为：");
		System.err.println(new String(receive.toByteArray()));
	}
	
	
	public void setAddress(InetAddress address) {
		this.targetAddress= address;
	}
	
	public void setPort(int port) {
		this.targetPort = port;
	}
	
	
	public InetAddress getAddress() {
		if(address==null) {
			try {
				address = InetAddress.getLocalHost();
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return address;
	}
	
	public int getPort() {
		return port;
	}
	
	public DatagramPacket receive(int time) {
		byte[] bytes = new byte[1500];
		DatagramPacket data = new DatagramPacket(bytes,bytes.length);
		try {
			socket.setSoTimeout(time);
			socket.receive(data);
		} catch(SocketTimeoutException e) {
			if(data.getPort()==-1) {
				return null;
			}
		}catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return data;
	}
	
	public boolean getInfo(DatagramPacket data) {
		if(data == null)
			return false;
		byte receiveData[] = data.getData();
		InetAddress sendAddress = data.getAddress();
		int sendPort = data.getPort();
		data.getAddress();
		data.getPort();
		int seq = (int)receiveData[0];
		int length = data.getLength();
		if(length == 1) {
			if(seq==0) {
				return true;
			}
			System.out.println("接受ACK:"+seq);
			sendBuf.remove(seq);
			clock.deleteClock(seq);
			if(seq == sendBase) {
				sendBase++;
				System.out.println("SendBase加1,现为"+sendBase);
				//如果发送缓存不为空且sendBase不在sendBuf中（以及接受），则移动窗口
				while(!sendBuf.containsKey(sendBase)&&!sendBuf.isEmpty()) {
					sendBase++;
					System.out.println("SendBase加1,现为"+sendBase);
				}
			}
			
		}else {
			if(seq==receiveBase) {
				System.out.println("Seq:"+seq+" 等于receiveBase，按序到达，成功接收");
				receive.write(receiveData, 1, length-1);
				send(new byte[0],receiveBase++,sendAddress,sendPort,0);
				System.out.println("ReceiveBase="+receiveBase);
				while(receiveBuf.containsKey(receiveBase)) {
					System.out.println("Seq:"+receiveBase+" 已接收,从缓存中取出数据报进行组装,receiveBase增加1");
					DatagramPacket Data = receiveBuf.remove(receiveBase);
					receive.write(Data.getData(),1,Data.getLength()-1);
					send(new byte[0],receiveBase,sendAddress,sendPort,0);
					System.out.println("ReceiveBase="+(++receiveBase));
				}
			}
			else if(receiveBase+receiveWin>seq){
				System.err.println("Seq:"+seq+" 在接收窗口内，乱序到达，进入接收缓存");
				System.out.println("ReceiveBase="+receiveBase);
				receiveBuf.put(seq, data);
				send(new byte[0],seq,sendAddress,sendPort,0);
			}
		}
		return false;
	}

	public void writeFile(String filename) {
		File file = new File(filename);
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(file));
			writer.write(new String(receive.toByteArray()));
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
	}
}
