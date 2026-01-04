package com.ofb.api;

import java.util.Date;

public class PaymentTotalsByTypesParams {
	public Date fromTime;
	public Date toTime;
	
	public String toString() {
		return("fromTime: " + fromTime + " ; toTime:" + toTime);
	}
}