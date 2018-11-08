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
	//���Ͷ˻���
	private Map<Integer,DatagramPacket> sendBuf = new HashMap<>();
	//���ն˻���
	private Map<Integer,DatagramPacket> receiveBuf = new HashMap<>();
	private int length;
	
	private ByteArrayOutputStream receive=new ByteArrayOutputStream();
	
	private DatagramSocket socket;	
	private InetAddress address = null;
	private int port;
	private int targetPort;
	private InetAddress targetAddress;
	
	//���ʹ��ڴ�С
	private int sendWin = 20;
	//���մ��ڴ�С
	private int receiveWin = 20;
	
	//���к�������
	private int maxSeq = 255;
	
	
	//���ʹ�����ʼ
	private int sendBase = 1;
	//���տ���ʼ
	private int receiveBase = 1;
	
	private int maxData = 1200;
	
	//���ʹ�����һ�����к�
	private int sendNext = 1;
	
	private Time clock= new Time(this);
	
	//����ʱ��
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
				System.err.println("�ļ�����");
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
				System.out.println("���ڷ���SeqΪ��"+seq+"�����ݰ�");
				if(end-start<=maxData) {
					System.err.println("���ݰ�����Ϊ��"+seq);
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
		System.out.println("���ʹ����������ȴ�����ACK�ƶ�����");
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
		System.out.println("�����Ѿ�����");
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
		System.out.println("��ʼ�����ļ���"+filename);
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
		System.err.println("�����ѽ�������������Ϊ��");
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
			System.out.println("����ACK:"+seq);
			sendBuf.remove(seq);
			clock.deleteClock(seq);
			if(seq == sendBase) {
				sendBase++;
				System.out.println("SendBase��1,��Ϊ"+sendBase);
				//������ͻ��治Ϊ����sendBase����sendBuf�У��Լ����ܣ������ƶ�����
				while(!sendBuf.containsKey(sendBase)&&!sendBuf.isEmpty()) {
					sendBase++;
					System.out.println("SendBase��1,��Ϊ"+sendBase);
				}
			}
			
		}else {
			if(seq==receiveBase) {
				System.out.println("Seq:"+seq+" ����receiveBase�����򵽴�ɹ�����");
				receive.write(receiveData, 1, length-1);
				send(new byte[0],receiveBase++,sendAddress,sendPort,0);
				System.out.println("ReceiveBase="+receiveBase);
				while(receiveBuf.containsKey(receiveBase)) {
					System.out.println("Seq:"+receiveBase+" �ѽ���,�ӻ�����ȡ�����ݱ�������װ,receiveBase����1");
					DatagramPacket Data = receiveBuf.remove(receiveBase);
					receive.write(Data.getData(),1,Data.getLength()-1);
					send(new byte[0],receiveBase,sendAddress,sendPort,0);
					System.out.println("ReceiveBase="+(++receiveBase));
				}
			}
			else if(receiveBase+receiveWin>seq){
				System.err.println("Seq:"+seq+" �ڽ��մ����ڣ����򵽴������ջ���");
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
