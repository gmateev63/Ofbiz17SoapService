package com.ofb.api;

import java.util.Date;

//enum AggregationLevel {
//	ITEM_AGGREGATION, DEPARTMENT_AGGREGATION, STORE_AGGREGATION, TAX_AGGREGATION
//}

//enum PaidState {
//	PAID, NOT_PAID, ALL
//}

public class OrderGetAggregationByDepartmentsParams {
	public String departmentId;
	public Date fromTime;
	public Date toTime;
	
	public String toString() {
		return("departmentId: " + departmentId + " ; fromTime: " + fromTime + " ; toTime:" + toTime);
	}

}