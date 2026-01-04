package com.ofb.api;

import java.util.Date;

enum PaidState {
	PAID, NOT_PAID, ALL
}

public class OrderListOrdersParams {
	public Date fromTime;
	public Date toTime;
	public PaidState paid;
	public boolean withDetails;
}