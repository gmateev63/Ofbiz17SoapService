package com.ofb.api;

public class PromotionAddRemoveCategoryToPromotionParams {
	public String promoId;
	public String categoryId;

	public String toString() {
		return("promoId: " + promoId + "categoryId: " + categoryId);
	}
}