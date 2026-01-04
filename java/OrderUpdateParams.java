package com.ofb.api;

public class OrderUpdateParams {
	public String orderId;
	public String externalId;
	public String barcode;
	public String orderName;
	public String statusId;
	
	public String toString() {
		return("\n orderId: " + orderId + "\n externalId:" + externalId + "\n barcode: " + barcode
				+ "\n orderName: " + orderName + "\n statusId: " + statusId);
	}
}