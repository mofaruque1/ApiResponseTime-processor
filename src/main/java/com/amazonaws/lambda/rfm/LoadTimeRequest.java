package com.amazonaws.lambda.rfm;
import com.google.gson.Gson;

public class LoadTimeRequest {

	private String email;
	private int loadTime;
	private boolean isPlanA;
	
	public LoadTimeRequest(String jsonObj) {
		Gson gson = new Gson();
		LoadTimeRequest temp = gson.fromJson(jsonObj, LoadTimeRequest.class);

		this.email=temp.getEmail();
		this.loadTime = temp.getLoadTime();
		this.isPlanA = temp.isPlanA();
	}
	

	public String getEmail() {
		return email;
	}
	public void setEmail(String email) {
		this.email = email;
	}
	public int getLoadTime() {
		return loadTime;
	}
	public void setLoadTime(int loadTime) {
		this.loadTime = loadTime;
	}
	
	public boolean isPlanA() {
		return isPlanA;
	}

	public void setPlanA(boolean isPlanA) {
		this.isPlanA = isPlanA;
	}

}


