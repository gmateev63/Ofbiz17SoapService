package com.ofb.api;

import java.util.Date;
import java.util.List;

//enum PaidState {
//	PAID, NOT_PAID, ALL
//}

public class UPCOrderListParams {
	public Date fromTime;
	public Date toTime;
	public PaidState paid;
	public List <CashPayment> cashPayments;
}