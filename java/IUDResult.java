package com.ofb.api;

public class IUDResult {
	public int resultCode;
	public String resultText;
	public String token;
  
	public String toString() {
		return("resultCode: " + resultCode + "; resultText: " + resultText + "; token: " + token);
	}
}