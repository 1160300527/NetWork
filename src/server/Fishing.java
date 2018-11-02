package server;

import java.util.HashMap;
import java.util.Map;

public class Fishing {
	private Map<String,String>fish = new HashMap<String,String> ();
	
	public void addFishing(String target,String myTarget) {
		fish.put(target, myTarget);
	}
	
	public String fishing(String target) {
		if(fish.containsKey(target))
			return fish.get(target);
		return null;
	}
}
