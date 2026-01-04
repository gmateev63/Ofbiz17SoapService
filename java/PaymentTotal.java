package com.ofb.api;

//import java.math.BigDecimal;

public class PaymentTotal {
	public String tenderType;
	public String tenderSubType;
	public String tenderMethod;
	public double totalAmount;
	public int countPayments;
	public int countOrders;
//	BigDecimal;
	
	public String toString() {
		return("tenderType: " + tenderType + " ;  tenderSubType: " + tenderSubType + " ; tenderMethod:" + tenderMethod 
				+ " ; totalAmount:" + totalAmount + " ; countPayments:" + countPayments + " ; countOrders:" + countOrders);
	}
}
