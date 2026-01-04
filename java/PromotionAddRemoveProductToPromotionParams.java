package com.ofb.api;

public class PromotionAddRemoveProductToPromotionParams {
	public String promoId;
	public String productId;
	public String upc;
	public String upc_modifier;

	public String toString() {
		return("promoId: " + promoId + "productId: " + productId + "upc: " + upc + "upc_modifier: " + upc_modifier);
	}
}