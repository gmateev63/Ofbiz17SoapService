package com.ofb.api;

import java.math.BigDecimal;

public class TransactionTotal {
	public String id;
	public String name;
	public double quantity;
	public double amount;
	public double tax;
	public String minOrderId;
	public String maxOrderId;
	public BigDecimal itemPrice;
	public String upc;
	public String upc_modifier;
	public String promoId;
	// --
	public int transactionCount; //  (=broi na orderi) v
	//public int itemCount; <- quantity v
	public double promoAmount;
	public int promoCount;
	
	public String toString() {
		return("id: " + id + "name: " + name + " ; quantity:" + quantity + " ; amount: " + amount 
				+ " ; tax:" + tax + " ; minOrderId:" + minOrderId + " ; maxOrderId:" + maxOrderId 
				+ " ; itemPrice:" + itemPrice + " ; upc:" + upc + " ; upc_modifier:" + upc_modifier
				+ " ; transactionCount:" + transactionCount + " ; promoAmount:" + promoAmount + " ; promoCount:" + promoCount);
	}
}
