package com.ofb.api;

public class InsertUpdatePromotionResult {
    public int resultCode;
    public String resultText;
    public String token;
	public String promoId;
	
	public String toString() {
		return("\n promoId: " + promoId + "; token: " + token);
	}
}