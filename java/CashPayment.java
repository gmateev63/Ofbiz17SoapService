package com.ofb.api;

import java.util.Comparator;
import java.util.Date;

class MyComparator implements Comparator<CashPayment> {
    public int compare(CashPayment cp1, CashPayment cp2) {
    	
    	int cmpRes = cp1.upc.compareTo(cp2.upc);
    	
        if (cmpRes == 0) {
        	if (cp1.orderDate.equals(cp2.orderDate)) return 0;
        	else if (cp1.orderDate.after(cp2.orderDate)) return 1;
        	else return -1;
        }
        else if (cmpRes > 0) return 1;
        else return -1;
    }
}

public class CashPayment {
	public String upc;
	public Date orderDate;
	public String sourceOrderId;
}