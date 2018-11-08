package SR;

import java.util.HashMap;
import java.util.Set;

public class Time extends Thread{
	private Client client;
	private HashMap<Integer,Integer> restTime = new HashMap<>();
	public Time(Client client) {
		this.client = client;
	}
	
	public void setClock(int ack,int time) {
		restTime.put(ack, time);
	}
	
	public void deleteClock(int ack) {
		restTime.remove(ack);
	}
	
	public void run() {
		while(true) {
			HashMap<Integer,Integer> rest = new HashMap<>();
			try {
				Thread.sleep(100);
				synchronized(rest) {
					rest.putAll(restTime);
					Set<Integer> acks = rest.keySet();
					for(Integer ack:acks) {
						int time = rest.get(ack)-1;
						if(time==0) {
							restTime.remove(ack);
							if(client.containsAck(ack)) {
								System.err.println("数据包seq = "+ack+"等待ACK超时，进行重传");
								System.err.println();
								client.timeOut(ack);
							}
						}
						else
							restTime.put(ack, time);
					}
				}
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
}
