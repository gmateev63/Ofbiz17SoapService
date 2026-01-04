package com.ofb.api;

import javax.jws.WebService;

@WebService
public interface OrderService {
	OrderListOrdersResult listOrders(
		LoginParams loginParams,
		OrderListOrdersParams methodParams			
	);
	
	UPCOrderListResult retrieveUPCOrderList(
			LoginParams loginParams,
			UPCOrderListParams methodParams
	);
	
	OrderWithDetails orderDetails(
			LoginParams loginParams,
			OrderDetailsParams methodParams	
	);
	
	TransactionTotalListResult getTransactionTotals(
			LoginParams loginParams,
			TransactionTotalListParams methodParams
	);
	
	CustomerInfoResult getCustomerInfo(
			LoginParams loginParams,
			CustomerInfoParams methodParams			
	);
	
	IUDResult updateOrder(
			LoginParams loginParams,
			OrderUpdateParams methodParams
	);

	PaymentTotalsByTypesResult getPaymentTotalsByTypes (
			LoginParams loginParams,
			PaymentTotalsByTypesParams methodParams
	);

	/*OrderGetAggregationByDepartmentsResult getAggregationByDepartments(
			LoginParams loginParams,
			OrderGetAggregationByDepartmentsParams methodParams
	);*/
}