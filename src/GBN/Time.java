package GBN;

import java.util.HashMap;
import java.util.Set;

public class Time extends Thread{
	private Client client;
	private int restTime;
	public Time(Client client) {
		this.client = client;
	}
	
	public void begin() {
		restTime = 3;
	}
	
	public void run() {
		while(true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			restTime--;
			if(restTime == 0) {
				System.err.println("超时，进行重传");
				client.timeOut();
			}
		}
	}
	
	public void Stop() {
		restTime = 1000;
	}
	
}
