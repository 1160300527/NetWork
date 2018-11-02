package server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class BlackList {
	private Set<String> addressblackList = new HashSet<String>();
	private Set<String> userblackList = new HashSet<String>();
	
	public BlackList(String addressBlacklist,String userBlacklist) {
		File file1 = new File(addressBlacklist);
		File file2 = new File(userBlacklist);
		try {
			BufferedReader address = new BufferedReader(new FileReader(file1));
			BufferedReader user = new BufferedReader(new FileReader(file2));
			String line = null;
			while((line=address.readLine())!=null) {
				addressblackList.add(line);
			}
			address.close();
			while((line=user.readLine())!=null) {
				userblackList.add(line);
			}
			user.close();
		} catch (FileNotFoundException e) {
			System.err.println("Don't find the file");
		} catch(IOException e) {
			
		}
	}
	
	public Set<String> getAddressBlackList() {
		return addressblackList;
	}
	
	public Set<String> getUserBlackList() {
		return userblackList;
	}
}
