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
	//发送端缓存
	private Map<Integer,DatagramPacket> sendBuf = new HashMap<>();
	//接收端缓存
	private Map<Integer,DatagramPacket> receiveBuf = new HashMap<>();
	//乱序到达的ACK
	private Set<Integer> ackOutOfOrder = new HashSet<>();
	
	private ByteArrayOutputStream receive=new ByteArrayOutputStream();
	
	private DatagramSocket socket;	
	private InetAddress address = null;
	private int port;
	
	//发送窗口大小
	private int sendWin = 10;
	//接收窗口大小
	private int receiveWin = 10;
	
	//序列号最大数maxSeq>=sendWin+receiveWin
	private int maxSeq = 20;
	
	
	//发送窗口起始
	private int sendBase = 0;
	
	//接收口起始
	private int receiveBase = 0;
	
	private int maxData = 1200;
	
	//发送窗口下一个序列号
	private int sendNext = 0;
	
	private Time clock= new Time(this);
	
	//过期时间
	private int restTime = 3;
	
	/**
	 * 构造函数，需指定绑定的端口
	 * @param port 绑定的端口号
	 */
	public Client(int port) {
		try {
			//创建socket
			socket = new DatagramSocket(port);
			this.port = port;
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 向指定地址与端口号发送文件内容，由于窗口大小有效，可能不能一次完成所有发送，需要重复调用该方法，因此
	 * 返回值为下次发送的文件起始位置。
	 * @param content 		发送的文件内容
	 * @param seq 			开始发送时报文开始的序号
	 * @param sendAddress	 目的地址
	 * @param sendPort 		目的端口号
	 * @param start 		开始发送的起点
	 * @return 				此次发送结束点，也就是下次发送时的起点。若文件已经全部发送，则返回-1
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
				System.out.println("正在发送Seq为："+seq+"的数据包");
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
		System.out.println("发送窗口已满，等待接收ACK移动窗口");
		return start;
	}
	
	
	/**
	 * 组建报文并发送到指定地址与指定端口
	 * @param send 			需要发送的报文的数据部分
	 * @param seq  			报文的序号
	 * @param sendAddress 	目的地址
	 * @param sendPort		目的端口号
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
	 * 发送content,目的地址为targetAddress,目的端口为targetPort
	 * @param content			将要发生的内容
	 * @param targetAddress		目的地址
	 * @param targetPort		目的端口号
	 */
	@SuppressWarnings("deprecation")
	public void sendTo(String content,InetAddress targetAddress,int targetPort) {
		int start = 0;
		//打开定时器
		clock.start();
		while((start =send(content.getBytes(),-1,targetAddress,targetPort,start,content.length()))>=0||!sendBuf.isEmpty()) {
			//接收ACK数据报并从数据报中获取ACK值
			getInfo(receive(100));
		}
		clock.stop();
		//收到所有ACK时，标志文件发送结束，因此发送一个seq为-1的空数据报表示发送的完成
		send(new byte[0],-1,targetAddress,targetPort,0,0);
		System.out.println("发送已经结束");
	}
	
	
	/**
	 * 当报文超时时，由定时器调用该方法,重发超时报文
	 * @param seq	超时的报文seq
	 */
	public void timeOut(int seq) {
		try {
			//从发送缓存中取出报文并通过socket进行发送
			socket.send(sendBuf.get(seq));
			//定时器更新该报文的超时时间
			clock.setClock(seq, restTime);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * 查询发送缓存中是否存在指定seq数据包(序号为seq的数据包是否收到ACK)
	 * @param seq 	查询的数据包序号
	 * @return		若序号为seq的数据包仍未进行确认，仍然在发送缓存中，则返回true
	 */
	public boolean containsAck(int seq) {
		return sendBuf.containsKey(seq);
	}
	
	
	/**
	 * 读取指定路径的文件内容
	 * @param filepath	文件路径
	 * @return			文件内容
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
	 * 开始向指定地址与端口号发送文件
	 * @param sendAddress	文件发送的目的地址
	 * @param port			文件发送的目的端口号
	 * @param filepath		文件的所在路径
	 */
	public void startSend(InetAddress sendAddress,int port,String filepath) {
		InetAddress targetAddress = sendAddress;
		int targetPort = port;
		String content = getContent(filepath);
		System.out.println("开始发送文件："+filepath);
		sendTo(content,targetAddress,targetPort);
	}
	
	
	/**
	 * 开始进行文件的接收
	 */
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
	
	
	/**
	 * 获取本机地址，为了进行测试，以本机地址作为接收地址
	 * @return	本机的地址
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
	 * 获取绑定的Port
	 * @return	返回本客户端绑定的端口号（发送方与接收方不同）
	 */
	public int getPort() {
		return port;
	}
	
	
	/**
	 * 接收数据报
	 * @param time	等待接收的超时时间，由于DatagramSocket.receive()是阻塞式方法，必须进行超时设置，否则程序将一直阻塞无法运行
	 * @return		所获取的数据包，若在等待时间内未接收到任何数据，将返回null
	 */
	public DatagramPacket receive(int time) {
		byte[] bytes = new byte[1500];
		DatagramPacket data = new DatagramPacket(bytes,bytes.length);
		try {
			socket.setSoTimeout(time);
			socket.receive(data);
		} catch(SocketTimeoutException e) {
			//数据包中端口号为-1，表示该等待时间内未接收任何数据包
			if(data.getPort()==-1) {
				return null;
			}
		}catch (IOException e) {
			e.printStackTrace();
		}
		return data;
	}
	
	
	/**
	 * 从数据报中获取所需信息：为ACK数据报或携带传输内容并进行相应处理。
	 * 接收到ACK后，窗口可能进行相应移动，且对应定时器需要进行关闭。
	 * 接收到数据后，需要获取传输的内容，并放入接收缓存或组装交给上层（此处进行简化，若为从receiveBase开始的连续数据
	 * ，则将其加入receive中）
	 * @param data	收到的数据报
	 * @return		数据报是否表示发送结束（数据报中Seq是否为0）
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
			System.out.println("接受ACK:"+seq);
			sendBuf.remove(seq);
			clock.deleteClock(seq);
			if(seq == sendBase) {
				ackOutOfOrder.remove(seq);
				//sendBase++;
				sendBase = (sendBase+1)%maxSeq;
				System.out.println("SendBase加1,现为"+sendBase);
				//如果Seq=sendBase的数据包曾收到过ACK，则窗口移动
				while(ackOutOfOrder.contains(sendBase)) {
					ackOutOfOrder.remove(sendBase);
					//sendBase++;
					sendBase = (sendBase+1)%maxSeq;
					System.out.println("SendBase加1,现为"+sendBase);
				}
			}
		}else {
			if(seq==receiveBase) {
				System.out.println("Seq:"+seq+" 等于receiveBase，按序到达，成功接收");
				receive.write(receiveData, 1, length-1);
				send(new byte[0],receiveBase,sendAddress,sendPort,0,0);
				receiveBase = (receiveBase+1)%maxSeq;
				System.out.println("ReceiveBase="+receiveBase);
				while(receiveBuf.containsKey(receiveBase)) {
					System.out.println("Seq:"+receiveBase+" 已接收,从缓存中取出数据报进行组装,receiveBase增加1");
					DatagramPacket Data = receiveBuf.remove(receiveBase);
					receive.write(Data.getData(),1,Data.getLength()-1);
					send(new byte[0],receiveBase,sendAddress,sendPort,0,0);
					receiveBase = (receiveBase+1)%maxSeq;
					System.out.println("ReceiveBase="+receiveBase);
				}
			}
			else if(((seq<receiveBase)&&(receiveBase+receiveWin)%maxSeq>seq)||
					((seq>receiveBase)&&(receiveBase+receiveWin)>seq)){
				System.err.println("Seq:"+seq+" 在接收窗口内，乱序到达，进入接收缓存");
				System.out.println("ReceiveBase="+receiveBase);
				receiveBuf.put(seq, data);
				send(new byte[0],seq,sendAddress,sendPort,0,0);
			}
			else {
				System.err.println("超出接收窗口，丢弃");
			}
		}
		return false;
	}

	
	/**
	 * 将收到的内容写入指定文件
	 * @param filepath	写入文件的路径
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
