package com.ofb.api;

import java.util.Date;
import java.math.BigDecimal;

enum PromoType {
	BuyXItemsForZprice, BuyXItemsForZtotalPrice
}

public class PromotionInsertUpdatePromotionParams {
	public String promoId;
	public String promoName;
	public String promoText;
	public PromoType promoTypeId;  
	public Date startDate;    
	public Date endDate;
	public BigDecimal quantity;
	public BigDecimal amount;	

	public String toString() {
		return("promoId: " + promoId + "promoName: " + promoName + "promoText: " + promoText + "promoTypeId: " + promoTypeId
				+ " ; startDate: " + startDate + " ; endDate:" + endDate + " ; quantity:" + quantity + " ; amount:" + amount
				);
	}
}