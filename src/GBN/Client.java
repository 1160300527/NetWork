package GBN;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
	
	//���յ����ݣ�����
	private ByteArrayOutputStream receive=new ByteArrayOutputStream();
	
	//����socket
	private DatagramSocket socket;	
	//������ַ
	private InetAddress address = null;
	//socket�󶨵Ķ˿ں�
	private int port;
	
	//���ʹ��ڴ�С
	private int sendWin = 20;
	
	//���к������maxSeq>=sendWin+receiveWin
	private int maxSeq = 40;
	
	//���ʹ�����ʼ
	private int sendBase = 0;
	private int expectReceive = 0;
	
	//ÿ�������������������ֽ���
	private int maxData = 1200;
	
	//���ʹ�����һ�����к�
	private int sendNext = 0;
	
	//��ʱ��
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
		if(start<0) {
			return -1;
		}
		//�����ʹ���δ��������Լ�������
		while(((sendNext<sendBase)&&((sendBase+sendWin)%maxSeq>sendNext))||
			   ((sendNext>sendBase)&&(sendBase+sendWin>sendNext))||
			   (sendNext==sendBase)) {
			try {
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
			if(seq == sendBase) {
				clock.begin();
			}
		}
		
		//�ǽ�����־���ģ����п��ܶ�ʧ
		if(seq>=0) {
			//������Ϊ0.4
			if(Math.random()<0.4)
				return;
		}
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
	public void sendTo(byte content[],InetAddress targetAddress,int targetPort) {
		int start = 0;
		//�򿪶�ʱ��
		clock.start();
		while((start =send(content,-1,targetAddress,targetPort,start,content.length))>=0||!sendBuf.isEmpty()) {
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
	public void timeOut() {
		int seq = sendBase;
		while(sendBuf.containsKey(seq)) {
			try {
				socket.send(sendBuf.get(seq));
				System.err.println("�ش���"+seq+" �ű���");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			seq++;
		}
		clock.begin();
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
	public byte[] getContent(String filepath) {
		File file = new File(filepath);
		byte content[]=new byte[(int) file.length()];
		try {
			FileInputStream input = new FileInputStream(file);
			input.read(content);
			input.close();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return content;
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
		byte content[] = getContent(filepath);
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
	 * �����ݱ��л�ȡ������Ϣ��ΪACK���ݱ���Я���������ݲ�������Ӧ������
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
			System.out.println("���ģ�"+seq+" �Ѿ���ȷ����");
			if((sendBase<seq&&sendBase+sendWin>seq)||(((sendBase+sendWin)%maxSeq>seq)&&(sendBase<seq))){
				while(sendBase<seq&&sendBase+sendWin>seq) {
					sendBuf.remove(sendBase);
					sendBase = (sendBase+1)%maxSeq;
				}
			}
			if(seq == sendBase) {
				System.out.println("���ģ�"+seq+" �Ѿ���ȷ����");
				sendBuf.remove(seq);
				sendBase = (sendBase+1)%maxSeq;
				System.out.println("SendBase��1,��Ϊ"+sendBase);
				if(sendBase == sendNext) {
					clock.Stop();
				}
				else {
					clock.begin();
				}
			}
		}else {
			if(seq==expectReceive) {
				System.out.println("Seq:"+seq+" ����receiveBase�����򵽴�ɹ�����");
				receive.write(receiveData, 1, length-1);
				send(new byte[0],expectReceive,sendAddress,sendPort,0,0);
				expectReceive = (expectReceive+1)%maxSeq;
				System.out.println("expectReceive="+expectReceive);
			}
			else {
				send(new byte[0],expectReceive-1,sendAddress,sendPort,0,0);
				System.err.println(seq+"��ΪexpectReceive,����");
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
			FileOutputStream output = new FileOutputStream(file);
			output.write(receive.toByteArray(), 0, receive.size());
			output.close();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}