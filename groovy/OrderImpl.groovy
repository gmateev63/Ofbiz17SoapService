package com.ofb.api;

import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.entity.condition.*;
import org.apache.ofbiz.entity.util.*;
import java.lang.reflect.Field;
import java.util.*;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.apache.ofbiz.product.price.PriceServices;
import com.ofb.api.Utils;

import groovy.ui.SystemOutputInterceptor

public class OrderImpl implements OrderService {

    public UPCOrderListResult retrieveUPCOrderList(LoginParams loginParams,UPCOrderListParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");    

		def res = new UPCOrderListResult();

		def tokenParty = Utils.soapUserLoginToken(loginParams.username,loginParams.password,loginParams.token,dispatcher.getDispatchContext());
		def party = null;
		if (tokenParty) {
			party = tokenParty["partyId"];
			res.token = tokenParty["token"];
		}
		
		if (!party) {
		    res.resultCode = -1;
		    res.resultText = "Wrong username or password";
	        return res;
		}		

		try {
			def fromTime = methodParams.fromTime;
			def toTime = methodParams.toTime;
			
			// order by UPC
			Collections.sort(methodParams.cashPayments,new MyComparator());

			// select order headers from database
			def exprs = [];
			exprs.add(EntityCondition.makeCondition("orderDate", EntityOperator.GREATER_THAN, new java.sql.Timestamp (fromTime.getTime())));
			exprs.add(EntityCondition.makeCondition("orderDate", EntityOperator.LESS_THAN, new java.sql.Timestamp (toTime.getTime())));
			
			def orderBy = UtilMisc.toList("internalCode");			
			def allOrders = delegator.findList("OrderHeader", EntityCondition.makeCondition(exprs, EntityOperator.AND), null, orderBy, null, false);
			def ordItr = allOrders.iterator();
			
			def orderList = new ArrayList();
			for (CashPayment cp : methodParams.cashPayments) {
				String cpUpc = cp.upc;
				Debug.logInfo ( "~~~ Looking for cpUpc = " + cpUpc, "soapapi");
				
				while (ordItr.hasNext()) {
					def ord = ordItr.next();
					Debug.logInfo ( "~~~ cpUpc = " + cpUpc + " ord id = " + ord.orderId + " ord.internalCode = " + ord.internalCode, "soapapi");
					
					if (ord.internalCode == null) break;
					int cmpRes = cpUpc.compareTo(ord.internalCode);
					
					if (cmpRes == 0) {
						Debug.logInfo ( "~~~ Found Equals and break in " + ord.orderId, "soapapi");
						// create output order element
						def order = new OrderWithDetails();
						orderAddMain(order,ord,null,cp.sourceOrderId,delegator);				
						orderAddDetails(order,delegator,ord,dispatcher,loginParams.username);
												
						def orderIsPayed = getOrderIsPayed(ord,delegator);
						
						if (methodParams.paid.equals(PaidState.ALL)) orderList.add(order);
						if (methodParams.paid.equals(PaidState.PAID) && orderIsPayed) orderList.add(order);
						if (methodParams.paid.equals(PaidState.NOT_PAID) && !orderIsPayed) orderList.add(order);
						
						break;					                  
					} else if (cmpRes < 0) {
						Debug.logInfo ( "~~~ Break in " + ord.orderId, "soapapi");
						break;					                  
					}
				} // while
			} // for
		    res.resultCode = 0;
		    res.resultText = "Success";
			res.orders = orderList;	

		} catch(Exception e) {
		    res.resultCode = 1;
		    res.resultText = "System Error: " + e.getMessage();
		    return res;    
		}   
	
    	return res;
    }			
	
	def orderAddMain(def order,def ord, def ordExtraInfo, def sourceOrderId, def delegator) {
		order.orderId = ord.orderId;
		order.orderTypeId = ord.orderTypeId;
		order.salesChannelEnumId = ord.salesChannelEnumId;
		order.orderDate = ord.orderDate;
		order.statusId = ord.statusId;
		order.createdBy = ord.createdBy;
		order.currencyUom = ord.currencyUom;
		order.productStoreId = ord.productStoreId;
		order.terminalId = ord.terminalId;
		order.billingAccountId = ord.billingAccountId;
		order.transactionId = ord.transactionId;
		order.remainingSubTotal = ord.remainingSubTotal;
		order.grandTotal = ord.grandTotal;
		order.upc = ord.internalCode;
		order.externalId = ord.externalId;
		order.orderName = ord.orderName;
		order.internalCode = ord.internalCode;
		order.sourceOrderId = sourceOrderId;
		
		def taxes = EntityQuery.use(delegator).select("amount").from("OrderAdjustment")
						   .where("orderId",ord.orderId,"orderAdjustmentTypeId","SALES_TAX").queryList();
		def tax = 0;			   
		for (taxRec in taxes) tax += taxRec.amount;
		order.taxAmount = tax;
		//
		if (ordExtraInfo) {
			order.note = ordExtraInfo.note;		
			order.responseNote = ordExtraInfo.responseNote;
			order.customerName = ordExtraInfo.customerName;
			order.loyaltyCard = ordExtraInfo.loyaltyCard;
			order.datePickup = ordExtraInfo.datePickup;
			order.applicationId = ordExtraInfo.applicationId;
		}
		//
		order.lastUpdatedStamp = ord.lastUpdatedStamp;
		order.lastUpdatedTxStamp = ord.lastUpdatedTxStamp;
		order.createdStamp = ord.createdStamp;
		order.createdTxStamp = ord.createdTxStamp;
		
		if (ord.createdBy) order.crPartyId = EntityQuery.use(delegator).select("partyId").from("UserLogin").where("userLoginId",ord.createdBy).queryFirst().partyId;
	}
	
	def populateOrderItem(def orderItem, def item, def depIdRec, def delegator) {
		orderItem.orderId = item.orderId;
		orderItem.orderItemSeqId = item.orderItemSeqId;
		orderItem.externalId = item.externalId;
		orderItem.orderItemTypeId = item.orderItemTypeId;
		orderItem.orderItemGroupSeqId = item.orderItemGroupSeqId;
		orderItem.isItemGroupPrimary = item.isItemGroupPrimary;
		orderItem.fromInventoryItemId = item.fromInventoryItemId;
		orderItem.budgetId = item.budgetId;
		orderItem.budgetItemSeqId = item.budgetItemSeqId;
		orderItem.productId = item.productId;
		orderItem.supplierProductId = item.supplierProductId;
		orderItem.productFeatureId = item.productFeatureId;
		orderItem.prodCatalogId = item.prodCatalogId;
		orderItem.productCategoryId = item.productCategoryId;
		orderItem.isPromo = item.isPromo;
		orderItem.quoteId = item.quoteId;
		orderItem.quoteItemSeqId = item.quoteItemSeqId;
		orderItem.shoppingListId = item.shoppingListId;
		orderItem.shoppingListItemSeqId = item.shoppingListItemSeqId;
		orderItem.subscriptionId = item.subscriptionId;
		orderItem.deploymentId = item.deploymentId;
		orderItem.quantity = item.quantity;
		orderItem.cancelQuantity = item.cancelQuantity;
		orderItem.selectedAmount = item.selectedAmount;
		orderItem.unitPrice = item.unitPrice;
		orderItem.unitListPrice = item.unitListPrice;
		orderItem.unitAverageCost = item.unitAverageCost;
		orderItem.unitRecurringPrice = item.unitRecurringPrice;
		orderItem.isModifiedPrice = item.isModifiedPrice;
		orderItem.recurringFreqUomId = item.recurringFreqUomId;
		orderItem.itemDescription = item.itemDescription;
		orderItem.comments = item.comments;
		orderItem.correspondingPoId = item.correspondingPoId;
		orderItem.statusId = item.statusId;
		orderItem.syncStatusId = item.syncStatusId;
		orderItem.estimatedShipDate = item.estimatedShipDate;
		orderItem.estimatedDeliveryDate = item.estimatedDeliveryDate;
		orderItem.autoCancelDate = item.autoCancelDate;
		orderItem.dontCancelSetDate = item.dontCancelSetDate;
		orderItem.dontCancelSetUserLogin = item.dontCancelSetUserLogin;
		orderItem.shipBeforeDate = item.shipBeforeDate;
		orderItem.shipAfterDate = item.shipAfterDate;
		orderItem.cancelBackOrderDate = item.cancelBackOrderDate;
		orderItem.overrideGlAccountId = item.overrideGlAccountId;
		orderItem.salesOpportunityId = item.salesOpportunityId;
		orderItem.changeByUserLoginId = item.changeByUserLoginId;
		orderItem.lastUpdatedStamp = item.lastUpdatedStamp;
		orderItem.lastUpdatedTxStamp = item.lastUpdatedTxStamp;
		orderItem.createdStamp = item.createdStamp;
		orderItem.createdTxStamp = item.createdTxStamp;
		
		// Change 24.06.2025
		// OLD orderItem.departmentId = depIdRec.primaryProductCategoryId;
		// OLD orderItem.departmentName = Utils.getDepartmentName(depIdRec.primaryProductCategoryId,delegator);
		def catList = EntityQuery.use(delegator).select("productCategoryId").from("ProductCategoryMember").where("productId",item.productId).orderBy("sequenceNum").queryList();
		for (cat in catList) {
			def depRec = EntityQuery.use(delegator).select("productCategoryId").from("ProductCategory").where("productCategoryId",cat.productCategoryId,"productCategoryTypeId","DEPARTMENT_CATEGORY").queryFirst();
			if (depRec) {
				orderItem.departmentId = depRec.productCategoryId;
				break;
			}
		}
		
		orderItem.departmentName = Utils.getDepartmentName(orderItem.departmentId,delegator);		
		// End of Change 24.06.2025
		
		def vUpc = Utils.getUpc(orderItem.productId,delegator);
		orderItem.upc = vUpc.upc;
		orderItem.upc_modifier = vUpc.upc_modifier;
	}
	
	/*
	def getDepartmentName(def depId, def delegator) {
		def result = null;
		def depNameRec = EntityQuery.use(delegator).select("categoryName").from("ProductCategory").where("productCategoryId",depId).queryFirst();
		if (depNameRec) result = depNameRec.categoryName;
		return result;
	}
	*/
	
	def getProductPrice(def productRec, def delegator, def dispatcher, def userLogin, def currencyUom) {
		def dctx = dispatcher.getDispatchContext();
		
		def userLoginRec = EntityQuery.use(delegator).select("partyId").from("UserLogin").where("userLoginId",userLogin).queryFirst();
		
		def priceContext = [product : productRec, currencyUomId : currencyUom, partyId : userLoginRec.partyId, userLogin : userLogin]
		def priceMap = PriceServices.calculateProductPrice(dctx,priceContext);
		
		return Utils.rnd(priceMap.price);
	}

	def orderAddDetails(def order,def delegator,def ord, def dispatcher, def userLogin) {
		// OrderItems		            
		def itemList = EntityQuery.use(delegator)
                           .from("OrderItem")
                           .where("orderId",ord.orderId)
                           .orderBy("orderItemSeqId")
                           .queryList();
        def orderItemsList = [];          
        for (item in itemList) {
			def depIdRec = EntityQuery.use(delegator).select("primaryProductCategoryId","productTypeId","internalName").from("Product").where("productId",item.productId).queryFirst();
			
			if (depIdRec) {
				def orderItem = new OrderItems();
				// base virtual item
				populateOrderItem(orderItem,item,depIdRec,delegator);
				orderItemsList.add(orderItem);	
				def vUpc;
			
				if (depIdRec.productTypeId.equals("AGGREGATED_CONF") ) {			
					//Debug.logInfo ( "\n\n\n\n\n depIdRec.internalName = " + depIdRec.internalName, "soapapi");
					def prod = Utils.parseInternalName(depIdRec.internalName);
					def productMaster = prod["productMaster"];
					def configId = prod["configId"];
					
					// master item
					def masterProdRec = EntityQuery.use(delegator).from("Product").where("productId",productMaster).queryFirst();
					orderItem = new OrderItems();
					orderItem.orderId = item.orderId;
					orderItem.productId = productMaster;
					
					vUpc = Utils.getUpc(orderItem.productId,delegator);
					orderItem.upc = vUpc.upc; 
					orderItem.upc_modifier = vUpc.upc_modifier;
					//Debug.logInfo ( "\n\n\n~~~ masterProdRec = " + masterProdRec, "soapapi");
					
					orderItem.parentProductId = item.productId;
					
					// Change 24.06.2025
					// OLD orderItem.departmentId = masterProdRec.primaryProductCategoryId;
					// OLD orderItem.departmentName = Utils.getDepartmentName(masterProdRec.primaryProductCategoryId,delegator);
					def catList = EntityQuery.use(delegator).select("productCategoryId").from("ProductCategoryMember").where("productId",productMaster).orderBy("sequenceNum").queryList();
					for (cat in catList) {
						def depRec = EntityQuery.use(delegator).select("productCategoryId").from("ProductCategory").where("productCategoryId",cat.productCategoryId,"productCategoryTypeId","DEPARTMENT_CATEGORY").queryFirst();
						if (depRec) {
							orderItem.departmentId = depRec.productCategoryId;
							break;
						}
					}
					
					orderItem.departmentName = Utils.getDepartmentName(orderItem.departmentId,delegator);
					// End of Change 24.06.2025
					
					
					// End of Change 24.06.2025
					
					orderItem.unitPrice = getProductPrice(masterProdRec,delegator,dispatcher,userLogin,ord.currencyUom);
					orderItem.quantity = item.quantity;
					// TODO ? get more fields
					orderItemsList.add(orderItem);

					// config items
					def confRec = EntityQuery.use(delegator).select("configItemId","configOptionId").from("ProductConfigConfig").where("configId",configId).queryList();
					for (conf in confRec) {
						def prodConfRec = EntityQuery.use(delegator).select("productId").from("ProductConfigProduct")
						    .where("configItemId",conf.configItemId,"configOptionId",conf.configOptionId).queryFirst();
						
						if (prodConfRec) {	
							def detailProdRec = EntityQuery.use(delegator).from("Product").where("productId",prodConfRec.productId).queryFirst();
							
							orderItem = new OrderItems();													
							orderItem.orderId = item.orderId;
							orderItem.productId = prodConfRec.productId;
							
							vUpc = Utils.getUpc(orderItem.productId,delegator);
							orderItem.upc = vUpc.upc;
							orderItem.upc_modifier = vUpc.upc_modifier;
		
							orderItem.parentProductId = masterProdRec.productId;
							orderItem.departmentId = detailProdRec.primaryProductCategoryId;						
							orderItem.departmentName = Utils.getDepartmentName(detailProdRec.primaryProductCategoryId,delegator);
							orderItem.unitPrice = getProductPrice(detailProdRec,delegator,dispatcher,userLogin,ord.currencyUom);
							orderItem.quantity = item.quantity;
							// TODO ? get more fields
	
							orderItemsList.add(orderItem);
						}
					} // for
				} // if aggregated
			}
        }
		order.orderItems = orderItemsList;
		
		// OrderPayments    
	    def orderBy = UtilMisc.toList("lastUpdatedStamp");
        def paymentsList = delegator.findList("Payment",EntityCondition.makeCondition("paymentId", EntityOperator.LIKE,ord.orderId + "-%"), null, orderBy, null, false);                   
        def orderPaymentsList = [];            
        for (payment in paymentsList) {
			def orderPayment = new OrderPayments();
			
			def paymentSubType = "CASH";
			def authCode = null;
			if (payment.paymentMethodTypeId.equals("EFT_ACCOUNT")||payment.paymentMethodTypeId.equals("CREDIT_CARD")) {
				def paymentTrans = EntityQuery.use(delegator).select("paymentType","authCode").from("ofbPaymentTransaction").where("transactionId",payment.paymentRefNum,"transactionStatus",1L).queryFirst();
				paymentSubType = (paymentTrans.paymentType) ? paymentTrans.paymentType : "Other";
				authCode = paymentTrans.authCode;
			}

	        Field[] fields = orderPayment.getClass().getDeclaredFields();
	        for (field in fields) {
	        	String key = field.getName();
				
	        	field.setAccessible(true);
				
				def val;
				if (key.equals("paymentSubType")) val = paymentSubType;
				else if (key.equals("authCode")) val = authCode;
				else val = payment.get(key);
				
				field.set(orderPayment,val);
	        }
			orderPaymentsList.add(orderPayment);
        }
		order.orderPayments = orderPaymentsList;

		// OrderAdjustments       
		def adjustmentsList = EntityQuery.use(delegator)
                           .from("OrderAdjustment")
                           .where("orderId",ord.orderId)
                           .orderBy("lastUpdatedStamp")
                           .queryList();
        def orderAdjustmentsList = [];            
        for (adjustment in adjustmentsList) {
			def orderAdjusment = new OrderAdjustments();
	        Field[] fields = orderAdjusment.getClass().getDeclaredFields();
	        for (field in fields) {
	        	String key = field.getName(); 
	        	field.setAccessible(true);
        		field.set(orderAdjusment,adjustment.get(key));
	        }
			orderAdjustmentsList.add(orderAdjusment);
        }
		order.orderAdjustments = orderAdjustmentsList;

		// OrderAttributes   
		def attributesList = EntityQuery.use(delegator)
                           .from("OrderAttribute")
                           .where("orderId",ord.orderId)
                           .orderBy("lastUpdatedStamp")
                           .queryList();
        def orderAttributesList = [];            
        for (attribute in attributesList) {
			def orderAttribute = new OrderAttributes();
			Field[] fields = orderAttribute.getClass().getDeclaredFields();
	        for (field in fields) {
	        	String key = field.getName(); 
	        	field.setAccessible(true);
        		field.set(orderAttribute,attribute.get(key));
	        }
			orderAttributesList.add(orderAttribute);
        }
		order.orderAttributes = orderAttributesList;
	}			
    
    public OrderListOrdersResult listOrders(LoginParams loginParams,OrderListOrdersParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");    

		OrderListOrdersResult res = new OrderListOrdersResult();

		def tokenParty = Utils.soapUserLoginToken(loginParams.username,loginParams.password,loginParams.token,dispatcher.getDispatchContext());
		def party = null;
		if (tokenParty) {
			party = tokenParty["partyId"];
			res.token = tokenParty["token"];
		}
		
		if (!party) {
		    res.resultCode = -1;
		    res.resultText = "Wrong username or password";
	        return res;
		}		

		try {
			def fromTime = methodParams.fromTime;
			def toTime = methodParams.toTime;
		
			def exprs = [];
			exprs.add(EntityCondition.makeCondition("orderDate", EntityOperator.GREATER_THAN, new java.sql.Timestamp (fromTime.getTime())));
			exprs.add(EntityCondition.makeCondition("orderDate", EntityOperator.LESS_THAN, new java.sql.Timestamp (toTime.getTime())));
			def orderBy = UtilMisc.toList("orderId");
			def allOrders = delegator.findList("OrderHeader", EntityCondition.makeCondition(exprs, EntityOperator.AND), null, orderBy, null, false);
			
			def orderList = new ArrayList();
			for (ord in allOrders) {
				def order = new OrderWithDetails();				
				def ordExtraInfo = EntityQuery.use(delegator).select().from("orderExtraInfo").where("orderId",ord.orderId).queryFirst();
				orderAddMain(order,ord,ordExtraInfo,null,delegator);				
				if (methodParams.withDetails) orderAddDetails(order,delegator,ord,dispatcher,loginParams.username);
				def orderIsPayed = getOrderIsPayed(ord,delegator);
				
				if (methodParams.paid.equals(PaidState.ALL)) orderList.add(order);
				if (methodParams.paid.equals(PaidState.PAID) && orderIsPayed) orderList.add(order);
				if (methodParams.paid.equals(PaidState.NOT_PAID) && !orderIsPayed) orderList.add(order);
			}	
			
		    res.resultCode = 0;
		    res.resultText = "Success";
			res.orders = orderList;
		
		} catch(Exception e) {
			e.printStackTrace();
		    res.resultCode = 1;
		    res.resultText = "System Error: " + e.getMessage();
		    return res;    
		}   
	
    	return res;
    }
    
    public OrderWithDetails orderDetails(LoginParams loginParams,OrderDetailsParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");    

		OrderWithDetails res = new OrderWithDetails();

		def tokenParty = Utils.soapUserLoginToken(loginParams.username,loginParams.password,loginParams.token,dispatcher.getDispatchContext());
		def party = null;
		if (tokenParty) {
			party = tokenParty["partyId"];
			res.token = tokenParty["token"];
		}
		
		if (!party) {
		    res.resultCode = -1;
		    res.resultText = "Wrong username or password";
	        return res;
		}		

		try {
			def ord = EntityQuery.use(delegator)
				.from("OrderHeader")
				.where("orderId",methodParams.orderId)
				.queryFirst();
				
			if (ord) {
				def ordExtraInfo = EntityQuery.use(delegator).select().from("orderExtraInfo").where("orderId",ord.orderId).queryFirst();
				orderAddMain(res,ord,ordExtraInfo,null,delegator);
				orderAddDetails(res,delegator,ord,dispatcher,loginParams.username);
			}

			res.resultCode = 0;
			res.resultText = "Success";

		} catch(Exception e) {
			//e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
		
		return res;
    
    }
	
	def getOrderIsPayed(def ord, def delegator) {
		def condId = EntityCondition.makeCondition("paymentId", EntityOperator.LIKE,ord.orderId + "-%");
		def condStatus = EntityCondition.makeCondition("statusId", EntityOperator.EQUALS,'PMNT_SENT');
		def cond = EntityCondition.makeCondition(condId,EntityOperator.AND,condStatus);
		
		def paymentsList = EntityQuery.use(delegator).select("amount").from("Payment").where(cond).queryList();
		
		double orderAmount = 0.0;
		for (pmt in paymentsList) orderAmount += pmt.amount;
		
		// TODO Payment criteria may be revised, ask Toros
		boolean orderIsPayed = false;
		if (orderAmount>=ord.grandTotal) orderIsPayed = true;
		
		return orderIsPayed;
	}

	public TransactionTotalListResult getTransactionTotals(LoginParams loginParams,TransactionTotalListParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");

		Map resMap = new HashMap<String,TransactionTotal>();
		
		TransactionTotalListResult res = new TransactionTotalListResult();
		res.transactionTotals = new ArrayList<TransactionTotal>();

		def tokenParty = Utils.soapUserLoginToken(loginParams.username,loginParams.password,loginParams.token,dispatcher.getDispatchContext());
		def party = null;
		if (tokenParty) {
			party = tokenParty["partyId"];
			res.token = tokenParty["token"];
		}
		
		if (!party) {
			res.resultCode = -1;
			res.resultText = "Wrong username or password";
			return res;
		}
		
		try {
			def fromTime = methodParams.fromTime;
			def toTime = methodParams.toTime;
			
			if (methodParams.aggregationLevel==null) {
					res.resultCode = 2;
					res.resultText = "Invalid Aggregation Level.";
					return res;
			}

			if (methodParams.paid==null) {
					res.resultCode = 3;
					res.resultText = "Invalid 'paid' parameter.";
					return res;
			}

			def productsFiltered = ["_EMPTY_"];
			
			if (methodParams.departmentId) {
				// check for valid department
				def categoryExists = EntityQuery.use(delegator)select("productCategoryId")
				  .from ("ProductCategory").where("productCategoryId",methodParams.departmentId).queryFirst();
				
				if (!categoryExists) {
					res.resultCode = 4;
					res.resultText = "Invalid 'departmentId' param.";
					return res;
				}
				
				// create array of filtered products
				def productCatRecs = EntityQuery.use(delegator)
					.select("productCategoryId").from("ProductCategoryRollup").where("parentProductCategoryId",methodParams.departmentId).queryList();
				   
				//def catList = ["_EMPTY_"]; ???
				def catList = [methodParams.departmentId];
				
				for (rec in productCatRecs) catList.add(rec.productCategoryId);
				
				def prodRecs = EntityQuery.use(delegator)
					.select("productId").from("ProductCategoryMember").where(EntityCondition.makeCondition("productCategoryId",EntityOperator.IN,catList)).queryList();
			 
				for (rec in prodRecs) productsFiltered.add(rec.productId);
			}
			
			List cond = [];
			cond.add(EntityCondition.makeCondition("orderDate", EntityOperator.GREATER_THAN, new java.sql.Timestamp (fromTime.getTime())));
			cond.add(EntityCondition.makeCondition("orderDate", EntityOperator.LESS_THAN, new java.sql.Timestamp (toTime.getTime())));

			def allOrders = EntityQuery.use(delegator)select("orderId","productStoreId","grandTotal")
							.from ("OrderHeader").where(cond).orderBy("productStoreId","orderId").queryList();
							
			for (ord in allOrders) {
				def orderIsPayed = getOrderIsPayed(ord,delegator);
				
				if (methodParams.paid.equals(PaidState.PAID) && !orderIsPayed) continue;
				if (methodParams.paid.equals(PaidState.NOT_PAID) && orderIsPayed) continue;

				// Items
				cond = [];
				cond.add(EntityCondition.makeCondition("orderId",EntityOperator.EQUALS,ord.orderId));
				if (methodParams.departmentId) cond.add(EntityCondition.makeCondition("productId",EntityOperator.IN,productsFiltered));
				
				def itemList = EntityQuery.use(delegator).select("productId","quantity","unitPrice","orderItemSeqId")
				               .from("OrderItem").where(cond).queryList();
							   
				def store = EntityQuery.use(delegator).select("storeName")
							   .from("ProductStore").where("productStoreId",ord.productStoreId).queryFirst();
				def storeName = (store) ? store.storeName : null;
				
				for (item in itemList) {
					double tax = 0.0, promoAmount = 0.0;
					int promoCount = 0;
					def amount = item.quantity * item.unitPrice; // + promoAmount ?
					
					def ordAdjTaxList = EntityQuery.use(delegator).select("amount","comments","taxAuthorityRateSeqId").from("OrderAdjustment")
						.where("orderId",ord.orderId,"orderItemSeqId",item.orderItemSeqId,"orderAdjustmentTypeId","SALES_TAX").queryList();
					
					for (ordAdjTax in ordAdjTaxList) tax += ordAdjTax.amount;
					
					def ordAdjProductPromoList = EntityQuery.use(delegator).select("amount","productPromoId").from("OrderAdjustment")
						.where("orderId",ord.orderId,"orderItemSeqId",item.orderItemSeqId,"orderAdjustmentTypeId","PROMOTION_ADJUSTMENT").queryList();
						
					for (ordAdjPromo in ordAdjProductPromoList) {
						promoAmount += ordAdjPromo.amount;
						promoCount += 1;
					}
					
					if (methodParams.aggregationLevel.equals(AggregationLevel.ITEM_AGGREGATION)) {
						def product = EntityQuery.use(delegator).select("productName")
						    .from("Product").where("productId",item.productId).queryFirst();
						resMap = getData(resMap,item.productId,item.quantity,amount,item.productId,item.unitPrice,tax,promoCount,promoAmount,product.productName,ord.orderId,null,methodParams.aggregationLevel,delegator);
						
					} else if (methodParams.aggregationLevel.equals(AggregationLevel.ITEM_PROMO_AGGREGATION)) {
						def product = EntityQuery.use(delegator).select("productName")
							.from("Product").where("productId",item.productId).queryFirst();
						
						boolean isCalc = false;
						def idx = item.productId + "|";
						for (ordAdjPromo in ordAdjProductPromoList) {
							idx +=  ordAdjPromo.productPromoId;
							resMap = getData(resMap,idx,item.quantity,amount,item.productId,item.unitPrice,tax,promoCount,promoAmount,product.productName,ord.orderId,ordAdjPromo.productPromoId,methodParams.aggregationLevel,delegator);
							isCalc = true;
						}
							
						if (!isCalc)
							resMap = getData(resMap,item.productId + "|",item.quantity,amount,item.productId,item.unitPrice,tax,promoCount,promoAmount,product.productName,ord.orderId,null,methodParams.aggregationLevel,delegator);
				 		
					} else if (methodParams.aggregationLevel.equals(AggregationLevel.DEPARTMENT_AGGREGATION)) {
						//def product = EntityQuery.use(delegator).select("primaryProductCategoryId")
						//	.from("Product").where("productId",item.productId).queryFirst();
							
						// select department	
						def department = null;
						def productCatMemberList = EntityQuery.use(delegator).select("productCategoryId").from("ProductCategoryMember").where("productId",item.productId).orderBy("sequenceNum").queryList();
						for (productCatMemberRec in productCatMemberList) {
							def categoryRec = EntityQuery.use(delegator).select("productCategoryId").from("ProductCategory")
							  .where("productCategoryId",productCatMemberRec.productCategoryId,"productCategoryTypeId","DEPARTMENT_CATEGORY").queryFirst();
							if (categoryRec) {
								department = categoryRec.productCategoryId;
								break;
							}							
						}
						
						// OLD def department = product.primaryProductCategoryId; // TODO change !!!						
						// OLD def cat = EntityQuery.use(delegator).select("categoryName").from("ProductCategory").where("productCategoryId",product.primaryProductCategoryId).queryFirst();
						def cat = EntityQuery.use(delegator).select("categoryName").from("ProductCategory").where("productCategoryId",department).queryFirst();
						def name = (cat) ? cat.categoryName : null;
						// OLD resMap = getData(resMap,product.primaryProductCategoryId,item.quantity,amount,item.productId,item.unitPrice,tax,promoCount,promoAmount,name,ord.orderId,null,methodParams.aggregationLevel,delegator);
						resMap = getData(resMap,department,item.quantity,amount,item.productId,item.unitPrice,tax,promoCount,promoAmount,name,ord.orderId,null,methodParams.aggregationLevel,delegator);
						
					} else if (methodParams.aggregationLevel.equals(AggregationLevel.TAX_AGGREGATION)) {
						for (ordAdj in ordAdjTaxList) {
							def taxAuthRateRec = EntityQuery.use(delegator).select("productCategoryId")
							          .from("TaxAuthorityRateProduct").where("taxAuthorityRateSeqId",ordAdj.taxAuthorityRateSeqId).queryFirst();
							def catId = "Other";
							if (taxAuthRateRec) catId = taxAuthRateRec.productCategoryId;
							resMap = getData(resMap,catId,item.quantity,amount,item.productId,item.unitPrice,ordAdj.amount,promoCount,promoAmount,ordAdj.comments,ord.orderId,null,methodParams.aggregationLevel,delegator);
						}
						
					} else if (methodParams.aggregationLevel.equals(AggregationLevel.PROMO_AGGREGATION)) {
						for (ordAdjPromo in ordAdjProductPromoList) {
							def promoType = getPromoType(ordAdjPromo.productPromoId,delegator);
							resMap = getData(resMap,ordAdjPromo.productPromoId,item.quantity,amount,item.productId,item.unitPrice,tax,promoCount,promoAmount,promoType,ord.orderId,ordAdjPromo.productPromoId,methodParams.aggregationLevel,delegator);
						}
						
					} else {  // AggregationLevel.STORE_AGGREGATION
						resMap = getData(resMap,ord.productStoreId,item.quantity,amount,item.productId,item.unitPrice,tax,promoCount,promoAmount,storeName,ord.orderId,null,methodParams.aggregationLevel,delegator);
					}
				
				} // for items
								
				if (methodParams.aggregationLevel.equals(AggregationLevel.PROMO_AGGREGATION)||methodParams.aggregationLevel.equals(AggregationLevel.STORE_AGGREGATION)) {
					def ordAdjPromoList = EntityQuery.use(delegator).select("amount","productPromoId").from("OrderAdjustment")
					   .where("orderId",ord.orderId,"orderItemSeqId","_NA_","orderAdjustmentTypeId","PROMOTION_ADJUSTMENT").queryList();

					if (ordAdjPromoList) {
						for (ordAdjPromo in ordAdjPromoList) {
							def promoType = getPromoType(ordAdjPromo.productPromoId,delegator); 
							def idx = ord.productStoreId; // STORE_AFGGREGATION
							def name = storeName;
							if (methodParams.aggregationLevel.equals(AggregationLevel.PROMO_AGGREGATION)) {
								idx = ordAdjPromo.productPromoId;
								name = promoType;
							}
							resMap = getData(resMap,idx,0,0,null,0,0,1,ordAdjPromo.amount,name,ord.orderId,ordAdjPromo.productPromoId,methodParams.aggregationLevel,delegator);		
						}
					}
				}
			} // for orders
			
			for (rmp in resMap.values()) {
				rmp.quantity = Utils.rnd(rmp.quantity);				
				rmp.amount = Utils.rnd(rmp.amount);
				rmp.tax = Utils.rnd(rmp.tax);
				res.transactionTotals.add(rmp);
			}
			
			res.resultCode = 0;
			res.resultText = "Success";
			
		} catch(Exception e) {
			e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
	
		return res;
	}
	
	private String getPromoType(def productPromoId,def delegator) {
		def act = EntityQuery.use(delegator).select("productPromoActionSeqId")
		  .from("ProductPromoAction").where("productPromoId",productPromoId,"productPromoActionSeqId","A1").queryFirst();
		if (act) return "MIX_MATCH";
		
		act = EntityQuery.use(delegator).select("productPromoActionSeqId")
		  .from("ProductPromoAction").where("productPromoId",productPromoId,"productPromoActionSeqId","S2").queryFirst();
		if (act) return "COMBO_DEALS";
		  
		return null;
	}

	private getContactFromMech(def contactMechId, def contactMechTypeId, def delegator) {
		def result = null;
		
		def rec =  EntityQuery.use(delegator)
			.select("infoString").from("ContactMech").where("contactMechId",contactMechId,"contactMechTypeId",contactMechTypeId).queryFirst();
		if (rec) result = rec.infoString;
		
		return result;
	}
	
	public CustomerInfoResult getCustomerInfo(LoginParams loginParams,CustomerInfoParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");

		CustomerInfoResult res = new CustomerInfoResult();

		def tokenParty = Utils.soapUserLoginToken(loginParams.username,loginParams.password,loginParams.token,dispatcher.getDispatchContext());
		def party = null;
		if (tokenParty) {
			party = tokenParty["partyId"];
			res.token = tokenParty["token"];
		}
		
		if (!party) {
			res.resultCode = -1;
			res.resultText = "Wrong username or password";
			return res;
		}

		try {
			Date date = new Date();
			Timestamp tsFromDate = new Timestamp(date.getTime());
			List datePars = []
			datePars.add(EntityCondition.makeCondition("thruDate", EntityOperator.EQUALS, null))
			datePars.add(EntityCondition.makeCondition("thruDate", EntityOperator.GREATER_THAN, tsFromDate))
			EntityCondition dateCond = EntityCondition.makeCondition(datePars, EntityOperator.OR)
			
			EntityCondition dateCondFrom = EntityCondition.makeCondition("fromDate", EntityOperator.LESS_THAN, tsFromDate);
			
			EntityCondition partyCond = EntityCondition.makeCondition("partyId", EntityOperator.EQUALS, methodParams.partyId);
			
			EntityCondition cond = EntityCondition.makeCondition([dateCond, dateCondFrom, partyCond], EntityOperator.AND)
			
			def mechs = EntityQuery.use(delegator).select("contactMechId").from("PartyContactMech").where(cond).queryList();
				
			for (mech in mechs) {
				def tmp = getContactFromMech(mech.contactMechId,"TELECOM_NUMBER",delegator);
				if (tmp) res.phone = tmp;
				   
				tmp = getContactFromMech(mech.contactMechId,"EMAIL_ADDRESS",delegator);
				if (tmp) res.email = tmp;

				tmp = getContactFromMech(mech.contactMechId,"POSTAL_ADDRESS",delegator);
				if (tmp) res.postalAddress = tmp;
				
				tmp = getContactFromMech(mech.contactMechId,"WEB_ADDRESS",delegator);
				if (tmp) res.webAddress = tmp;
				
				tmp = getContactFromMech(mech.contactMechId,"IP_ADDRESS",delegator);
				if (tmp) res.ipAddress = tmp;
				
				tmp = getContactFromMech(mech.contactMechId,"ELECTRONIC_ADDRESS",delegator);
				if (tmp) res.electronicAddress = tmp;
			} 
			
			def person = EntityQuery.use(delegator)
				.select("firstName","middleName","lastName").from("Person").where("partyId",methodParams.partyId).queryFirst();
				
			if (person) {
				res.firstName = person.firstName;
				res.middleName = person.middleName;
				res.lastName = person.lastName;
			}			
				
			res.resultCode = 0;
			res.resultText = "Success";

		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
		
		return res;
	
	}
	
	def setMinMaxOrder(def type,def newOrderId,def curOrderId) {
		def result = curOrderId;
		try {
			if (type.equals("min")) {
				if (Integer.parseInt(newOrderId) < Integer.parseInt(curOrderId)) result = newOrderId;
			} else { // max
				if (Integer.parseInt(newOrderId) > Integer.parseInt(curOrderId)) result = newOrderId;
			}
		} catch (Exception e) {
			result = null;
		}
		
		return result;
	}
	
	def getData(def resMap, def dataId, def quantity, def amount, def productId, def itemPrice, def tax, def promoCount, def promoAmount, def dataName, def orderId, def promoId, def aggregationLevel, def delegator) {	
		TransactionTotal unt = resMap.get(dataId); 
		if (unt==null) {
			TransactionTotal transactionTotal = new TransactionTotal();
			transactionTotal.transactionCount = 1;
			
			transactionTotal.id = dataId;
			if (aggregationLevel==AggregationLevel.ITEM_PROMO_AGGREGATION) transactionTotal.id = productId;
			
			transactionTotal.name = dataName;
			transactionTotal.quantity = quantity;
			transactionTotal.amount = amount;
			transactionTotal.tax = tax;
			transactionTotal.minOrderId = orderId;
			transactionTotal.maxOrderId = orderId;
			transactionTotal.promoCount = promoCount;
			transactionTotal.promoAmount = promoAmount;
			transactionTotal.promoId = promoId;
			
			if (aggregationLevel.equals(AggregationLevel.ITEM_AGGREGATION)||aggregationLevel.equals(AggregationLevel.ITEM_PROMO_AGGREGATION)) {
				def vUpc = Utils.getUpc(productId,delegator);
				transactionTotal.itemPrice = Utils.rnd(itemPrice);
				transactionTotal.upc = vUpc.upc;
				transactionTotal.upc_modifier = vUpc.upc_modifier;
			}
			resMap.put(dataId,transactionTotal);
		} else {
			unt.transactionCount += 1;
			unt.quantity += quantity;
			unt.amount += amount;
			unt.tax += tax;
			unt.minOrderId = setMinMaxOrder("min",orderId,unt.minOrderId);
			unt.maxOrderId = setMinMaxOrder("max",orderId,unt.maxOrderId);
			unt.promoCount += promoCount;
			unt.promoAmount += promoAmount;

			resMap.put(dataId,unt);
		}

		return resMap;
	}

	public IUDResult updateOrder(LoginParams loginParams,OrderUpdateParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");  // getContent(Class<T> format)
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method updateOrder", "soapapi");
		Debug.logInfo ( "~~~ methodParams = " + methodParams.toString(), "soapapi");
		
		def tokenParty = Utils.soapUserLoginToken(loginParams.username,loginParams.password,loginParams.token,dispatcher.getDispatchContext());
		def party = null;
		if (tokenParty) {
			party = tokenParty["partyId"];
			res.token = tokenParty["token"];
		}
		
		if (!party) {
			res.resultCode = -1;
			res.resultText = "Wrong username or password";
			Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.orderId) {
			res.resultCode = 2;
			res.resultText = "Missing orderId";
			Debug.logInfo ( "~~~ Result for updateOrder - " + res.toString(), "soapapi");
			return res;
		}
		
		try {
			def order = EntityQuery.use(delegator).from("OrderHeader").where("orderId",methodParams.orderId).queryFirst();
			
			if (!order) {
				res.resultCode = 3;
				res.resultText = "Missing Order";
				Debug.logInfo ( "~~~ Result for updateOrder - " + res.toString(), "soapapi");
				return res;
			} 
			
			def oldStatusId = order.statusId;
			
			if (methodParams.externalId) order.externalId = methodParams.externalId;

			if (methodParams.orderName) order.orderName = methodParams.orderName;
			
			if (methodParams.statusId) {
				order.statusId = methodParams.statusId;
			}
						
			if (methodParams.barcode) {
				def orderExtraInfo = EntityQuery.use(delegator).from("orderExtraInfo").where("orderId",methodParams.orderId).queryFirst();
				def genKey = methodParams.barcode;
				if (genKey.length() > 250)  genKey = genKey.substring(0, 250) + "..";
				if (!orderExtraInfo) {
					delegator.create("orderExtraInfo",[
						orderId:methodParams.orderId,
						genKey:genKey,
					]);
				} else {
					orderExtraInfo.genKey = genKey;
					delegator.store(orderExtraInfo);
				}
			}
			
			delegator.store(order);
			
			if (methodParams.statusId) {
				if (oldStatusId != methodParams.statusId && methodParams.statusId == "ORDER_PROCESSING"){
					dispatcher.runAsync("sendOrderChangeNotification", [orderId : order.orderId]);
					dispatcher.runAsync("sendSMSNotification", [orderId : order.orderId, messageTypeId : "change"]);
				}
				if (oldStatusId != methodParams.statusId && methodParams.statusId == "READY_FOR_DELIVERY"){
					dispatcher.runAsync("sendOrderCompleteNotification", [orderId : order.orderId]);
					dispatcher.runAsync("sendSMSNotification", [orderId : order.orderId, messageTypeId : "ready"]);
				}
			}
		
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for updateOrder - " + res.toString(), "soapapi");
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for updateOrder - " + res.toString(), "soapapi");
		return res;
	}
	
	public PaymentTotalsByTypesResult getPaymentTotalsByTypes(LoginParams loginParams,PaymentTotalsByTypesParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");

		PaymentTotalsByTypesResult res = new PaymentTotalsByTypesResult();

		def tokenParty = Utils.soapUserLoginToken(loginParams.username,loginParams.password,loginParams.token,dispatcher.getDispatchContext());
		def party = null;
		if (tokenParty) {
			party = tokenParty["partyId"];
			res.token = tokenParty["token"];
		}
		
		if (!party) {
			res.resultCode = -1;
			res.resultText = "Wrong username or password";
			return res;
		}

		try {			
			def fromTime = methodParams.fromTime;
			def toTime = methodParams.toTime;
		
			def exprs = [];
			exprs.add(EntityCondition.makeCondition("effectiveDate", EntityOperator.GREATER_THAN, new java.sql.Timestamp (fromTime.getTime())));
			exprs.add(EntityCondition.makeCondition("effectiveDate", EntityOperator.LESS_THAN, new java.sql.Timestamp (toTime.getTime())));
			exprs.add(EntityCondition.makeCondition("statusId", EntityOperator.EQUALS, "PMNT_SENT"));
			def cond = EntityCondition.makeCondition(exprs, EntityOperator.AND);
			def paymentRecs = EntityQuery.use(delegator).select("paymentId","paymentMethodTypeId","amount","paymentRefNum","effectiveDate").from("Payment").where(cond)
				.orderBy("paymentMethodTypeId","effectiveDate").queryList();
					
			//Debug.logInfo ( "\n\n paymentRecs = " + paymentRecs, "soapapi");
				
			def map = [:];
			
			def tenderType = "OTHER";
			def tenderSubType = "OTHER";
			def tenderMethod = "MANUAL"
			def paymentList = [];
			def paidOrderList = [];
			
			
			for (paymentRec in paymentRecs) {
				if (paymentRec.paymentMethodTypeId.equals("EFT_ACCOUNT")||paymentRec.paymentMethodTypeId.equals("CREDIT_CARD")) {
					tenderType = "EFT";
					def paymentTransRec = EntityQuery.use(delegator).select("paymentType").from("ofbPaymentTransaction")
						.where("transactionId",paymentRec.paymentRefNum,"transactionStatus",1L).queryFirst();
						
					tenderSubType = "OTHER";
					if (paymentTransRec) 
							if (paymentTransRec.paymentType && !paymentTransRec.paymentType.equals("")) tenderSubType = paymentTransRec.paymentType;
				} else if (paymentRec.paymentMethodTypeId.equals("CASH")) {
					tenderType = "CASH";
					tenderSubType = "CASH";
				} else if (paymentRec.paymentMethodTypeId.equals("COMPANY_CHECK")) {
					tenderType = "CHECK";
					tenderSubType = "CHECK";
				} else if (paymentRec.paymentMethodTypeId.equals("GIFT_CARD")) {
					tenderType = "ofb";
					tenderSubType = "GIFT";
				}
				
				def ind = tenderType + "_" + tenderSubType + "_" + tenderMethod;				
				def elem = map.get(ind);
				
				if (!elem) {
					elem = new PaymentTotal();
					elem.tenderType = tenderType;
					elem.tenderSubType = tenderSubType;
					elem.tenderMethod = tenderMethod;
					paidOrderList = [];
				}
				
				elem.totalAmount += paymentRec.amount;
				elem.countPayments += 1;

				def orderId = Utils.parsePaymentForOrder(paymentRec.paymentId);
				def orderRec = EntityQuery.use(delegator).select("orderId").from("OrderHeader").where("orderId",orderId).queryFirst();
				
				if (orderRec && !paidOrderList.contains(orderId))   paidOrderList.add(orderId);

				elem.countOrders = paidOrderList.size();
				
				map.put(ind,elem);
			}

			for (line in map.values()) paymentList.add(line);
				
			res.resultCode = 0;
			res.resultText = "Success";
			res.paymentTotals = paymentList;
		
		} catch(Exception e) {
			e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
	
		return res;
	}
	
}
