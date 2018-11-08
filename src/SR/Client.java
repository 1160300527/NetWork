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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Client{
	//���Ͷ˻���
	private Map<Integer,DatagramPacket> sendBuf = new HashMap<>();
	//���ն˻���
	private Map<Integer,DatagramPacket> receiveBuf = new HashMap<>();
	//���򵽴��ACK
	private Set<Integer> ackOutOfOrder = new HashSet<>();
	
	private ByteArrayOutputStream receive=new ByteArrayOutputStream();
	
	private DatagramSocket socket;	
	private InetAddress address = null;
	private int port;
	
	//���ʹ��ڴ�С
	private int sendWin = 10;
	//���մ��ڴ�С
	private int receiveWin = 10;
	
	//���к������maxSeq>=sendWin+receiveWin
	private int maxSeq = 20;
	
	
	//���ʹ�����ʼ
	private int sendBase = 0;
	
	//���տ���ʼ
	private int receiveBase = 0;
	
	private int maxData = 1200;
	
	//���ʹ�����һ�����к�
	private int sendNext = 0;
	
	private Time clock= new Time(this);
	
	//����ʱ��
	private int restTime = 3;
	
	/**
	 * ���캯������ָ���󶨵Ķ˿�
	 * @param port �󶨵Ķ˿ں�
	 */
	public Client(int port) {
		try {
			//����socket
			socket = new DatagramSocket(port);
			this.port = port;
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * ��ָ����ַ��˿ںŷ����ļ����ݣ����ڴ��ڴ�С��Ч�����ܲ���һ��������з��ͣ���Ҫ�ظ����ø÷��������
	 * ����ֵΪ�´η��͵��ļ���ʼλ�á�
	 * @param content 		���͵��ļ�����
	 * @param seq 			��ʼ����ʱ���Ŀ�ʼ�����
	 * @param sendAddress	 Ŀ�ĵ�ַ
	 * @param sendPort 		Ŀ�Ķ˿ں�
	 * @param start 		��ʼ���͵����
	 * @return 				�˴η��ͽ����㣬Ҳ�����´η���ʱ����㡣���ļ��Ѿ�ȫ�����ͣ��򷵻�-1
	 */
	public int send(byte[] content,int seq,InetAddress sendAddress,int sendPort,int start,int end){
		if(content.length==0) {
			ByteArrayOutputStream Send = new ByteArrayOutputStream();
			Send.write((byte)seq);
			sendData(Send.toByteArray(),seq,sendAddress,sendPort);
			try {
				Send.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return 0;
		}
		while(((sendNext<sendBase)&&((sendBase+sendWin)%maxSeq>sendNext))||
			   ((sendNext>sendBase)&&(sendBase+sendWin>sendNext))||
			   (sendNext==sendBase)) {
			try {
				if(start<0) {
					return -1;
				}
				ByteArrayOutputStream Send = new ByteArrayOutputStream();
				seq = sendNext;
				sendNext = (sendNext+1)%maxSeq;
				Send.write((byte)seq);
				System.out.println("���ڷ���SeqΪ��"+seq+"�����ݰ�");
				if(end-start<=maxData) {
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
	
	
	/**
	 * �齨���Ĳ����͵�ָ����ַ��ָ���˿�
	 * @param send 			��Ҫ���͵ı��ĵ����ݲ���
	 * @param seq  			���ĵ����
	 * @param sendAddress 	Ŀ�ĵ�ַ
	 * @param sendPort		Ŀ�Ķ˿ں�
	 */
	public void sendData(byte[] send,int seq,InetAddress sendAddress,int sendPort) {
		DatagramPacket data = new DatagramPacket(send,send.length,sendAddress,sendPort);
		if(send.length>1) {
			sendBuf.put(seq, data);
			clock.setClock(seq, restTime);
		}
		if(seq%3==0&&send.length!=1)
			return;
		try {
			socket.send(data);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * ����content,Ŀ�ĵ�ַΪtargetAddress,Ŀ�Ķ˿�ΪtargetPort
	 * @param content			��Ҫ����������
	 * @param targetAddress		Ŀ�ĵ�ַ
	 * @param targetPort		Ŀ�Ķ˿ں�
	 */
	@SuppressWarnings("deprecation")
	public void sendTo(String content,InetAddress targetAddress,int targetPort) {
		int start = 0;
		//�򿪶�ʱ��
		clock.start();
		while((start =send(content.getBytes(),-1,targetAddress,targetPort,start,content.length()))>=0||!sendBuf.isEmpty()) {
			//����ACK���ݱ��������ݱ��л�ȡACKֵ
			getInfo(receive(100));
		}
		clock.stop();
		//�յ�����ACKʱ����־�ļ����ͽ�������˷���һ��seqΪ-1�Ŀ����ݱ���ʾ���͵����
		send(new byte[0],-1,targetAddress,targetPort,0,0);
		System.out.println("�����Ѿ�����");
	}
	
	
	/**
	 * �����ĳ�ʱʱ���ɶ�ʱ�����ø÷���,�ط���ʱ����
	 * @param seq	��ʱ�ı���seq
	 */
	public void timeOut(int seq) {
		try {
			//�ӷ��ͻ�����ȡ�����Ĳ�ͨ��socket���з���
			socket.send(sendBuf.get(seq));
			//��ʱ�����¸ñ��ĵĳ�ʱʱ��
			clock.setClock(seq, restTime);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * ��ѯ���ͻ������Ƿ����ָ��seq���ݰ�(���Ϊseq�����ݰ��Ƿ��յ�ACK)
	 * @param seq 	��ѯ�����ݰ����
	 * @return		�����Ϊseq�����ݰ���δ����ȷ�ϣ���Ȼ�ڷ��ͻ����У��򷵻�true
	 */
	public boolean containsAck(int seq) {
		return sendBuf.containsKey(seq);
	}
	
	
	/**
	 * ��ȡָ��·�����ļ�����
	 * @param filepath	�ļ�·��
	 * @return			�ļ�����
	 */
	public String getContent(String filepath) {
		File file = new File(filepath);
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
		return Content;
	}
	
	
	/**
	 * ��ʼ��ָ����ַ��˿ںŷ����ļ�
	 * @param sendAddress	�ļ����͵�Ŀ�ĵ�ַ
	 * @param port			�ļ����͵�Ŀ�Ķ˿ں�
	 * @param filepath		�ļ�������·��
	 */
	public void startSend(InetAddress sendAddress,int port,String filepath) {
		InetAddress targetAddress = sendAddress;
		int targetPort = port;
		String content = getContent(filepath);
		System.out.println("��ʼ�����ļ���"+filepath);
		sendTo(content,targetAddress,targetPort);
	}
	
	
	/**
	 * ��ʼ�����ļ��Ľ���
	 */
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
	
	
	/**
	 * ��ȡ������ַ��Ϊ�˽��в��ԣ��Ա�����ַ��Ϊ���յ�ַ
	 * @return	�����ĵ�ַ
	 */
	public InetAddress getAddress() {
		if(address==null) {
			try {
				address = InetAddress.getLocalHost();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		}
		return address;
	}
	
	
	/**
	 * ��ȡ�󶨵�Port
	 * @return	���ر��ͻ��˰󶨵Ķ˿ںţ����ͷ�����շ���ͬ��
	 */
	public int getPort() {
		return port;
	}
	
	
	/**
	 * �������ݱ�
	 * @param time	�ȴ����յĳ�ʱʱ�䣬����DatagramSocket.receive()������ʽ������������г�ʱ���ã��������һֱ�����޷�����
	 * @return		����ȡ�����ݰ������ڵȴ�ʱ����δ���յ��κ����ݣ�������null
	 */
	public DatagramPacket receive(int time) {
		byte[] bytes = new byte[1500];
		DatagramPacket data = new DatagramPacket(bytes,bytes.length);
		try {
			socket.setSoTimeout(time);
			socket.receive(data);
		} catch(SocketTimeoutException e) {
			//���ݰ��ж˿ں�Ϊ-1����ʾ�õȴ�ʱ����δ�����κ����ݰ�
			if(data.getPort()==-1) {
				return null;
			}
		}catch (IOException e) {
			e.printStackTrace();
		}
		return data;
	}
	
	
	/**
	 * �����ݱ��л�ȡ������Ϣ��ΪACK���ݱ���Я���������ݲ�������Ӧ����
	 * ���յ�ACK�󣬴��ڿ��ܽ�����Ӧ�ƶ����Ҷ�Ӧ��ʱ����Ҫ���йرա�
	 * ���յ����ݺ���Ҫ��ȡ��������ݣ���������ջ������װ�����ϲ㣨�˴����м򻯣���Ϊ��receiveBase��ʼ����������
	 * ���������receive�У�
	 * @param data	�յ������ݱ�
	 * @return		���ݱ��Ƿ��ʾ���ͽ��������ݱ���Seq�Ƿ�Ϊ0��
	 */
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
			if(seq==-1) {
				return true;
			}
			ackOutOfOrder.add(seq);
			System.out.println("����ACK:"+seq);
			sendBuf.remove(seq);
			clock.deleteClock(seq);
			if(seq == sendBase) {
				ackOutOfOrder.remove(seq);
				//sendBase++;
				sendBase = (sendBase+1)%maxSeq;
				System.out.println("SendBase��1,��Ϊ"+sendBase);
				//���Seq=sendBase�����ݰ����յ���ACK���򴰿��ƶ�
				while(ackOutOfOrder.contains(sendBase)) {
					ackOutOfOrder.remove(sendBase);
					//sendBase++;
					sendBase = (sendBase+1)%maxSeq;
					System.out.println("SendBase��1,��Ϊ"+sendBase);
				}
			}
		}else {
			if(seq==receiveBase) {
				System.out.println("Seq:"+seq+" ����receiveBase�����򵽴�ɹ�����");
				receive.write(receiveData, 1, length-1);
				send(new byte[0],receiveBase,sendAddress,sendPort,0,0);
				receiveBase = (receiveBase+1)%maxSeq;
				System.out.println("ReceiveBase="+receiveBase);
				while(receiveBuf.containsKey(receiveBase)) {
					System.out.println("Seq:"+receiveBase+" �ѽ���,�ӻ�����ȡ�����ݱ�������װ,receiveBase����1");
					DatagramPacket Data = receiveBuf.remove(receiveBase);
					receive.write(Data.getData(),1,Data.getLength()-1);
					send(new byte[0],receiveBase,sendAddress,sendPort,0,0);
					receiveBase = (receiveBase+1)%maxSeq;
					System.out.println("ReceiveBase="+receiveBase);
				}
			}
			else if(((seq<receiveBase)&&(receiveBase+receiveWin)%maxSeq>seq)||
					((seq>receiveBase)&&(receiveBase+receiveWin)>seq)){
				System.err.println("Seq:"+seq+" �ڽ��մ����ڣ����򵽴������ջ���");
				System.out.println("ReceiveBase="+receiveBase);
				receiveBuf.put(seq, data);
				send(new byte[0],seq,sendAddress,sendPort,0,0);
			}
			else {
				System.err.println("�������մ��ڣ�����");
			}
		}
		return false;
	}

	
	/**
	 * ���յ�������д��ָ���ļ�
	 * @param filepath	д���ļ���·��
	 */
	public void writeFile(String filepath) {
		File file = new File(filepath);
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(file));
			writer.write(new String(receive.toByteArray()));
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}
}
