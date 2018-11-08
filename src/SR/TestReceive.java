package SR;

public class TestReceive {
	public static void main(String args[]) {
		Client receive = new Client(88);
		receive.startReceive();
		receive.writeFile("src/SR/write.txt");
	}
}
