package com.ofb.api;

import org.apache.ofbiz.base.util.*;
import org.apache.ofbiz.content.content.ContentSearch.KeywordConstraint
import java.text.SimpleDateFormat;
import org.apache.cxf.phase.PhaseInterceptorChain;
import java.math.BigDecimal
import java.sql.Timestamp;
import org.apache.ofbiz.entity.util.EntityQuery;
import java.util.Date;
import org.apache.ofbiz.entity.GenericDelegator
import org.apache.ofbiz.entity.condition.*;
import com.ofb.api.Utils;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.product.store.ProductStoreWorker;

public class PromotionImpl implements PromotionService {

	public InsertUpdatePromotionResult insertUpdatePromotion(LoginParams loginParams,PromotionInsertUpdatePromotionParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");  // getContent(Class<T> format)
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		InsertUpdatePromotionResult res = new InsertUpdatePromotionResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method insertUpdatePromotion", "soapapi");
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
			Debug.logInfo ( "~~~ Result for insertUpdatePromotion - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.promoTypeId) {
			res.resultCode = 2;
			res.resultText = "Missing or Bad promoTypeId param";
			Debug.logInfo ( "~~~ Result for insertUpdatePromotion - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.startDate) {
			res.resultCode = 3;
			res.resultText = "Missing startDate param";
			Debug.logInfo ( "~~~ Result for insertUpdatePromotion - " + res.toString(), "soapapi");
			return res;
		}

		if (!methodParams.endDate) {
			res.resultCode = 4;
			res.resultText = "Missing endDate param";
			Debug.logInfo ( "~~~ Result for insertUpdatePromotion - " + res.toString(), "soapapi");
			return res;
		}
		
		def productPromoId = (methodParams.promoId) ? methodParams.promoId : delegator.getNextSeqId("ProductPromo");
		
		Date date = new Date();
		Timestamp actualDate= new Timestamp(date.getTime());

	    try {
			def promo = EntityQuery.use(delegator).from("ProductPromo").where("productPromoId",methodParams.promoId).queryFirst();
		
			if (!promo) {
				promo = delegator.create("ProductPromo",[
						productPromoId:productPromoId,
						userEntered:"Y",
						showToCustomer:"Y",
						requireCode:"N",
						createdDate:actualDate,
						createdByUserLogin:loginParams.username
				]);
			}
		
			promo.lastModifiedDate = actualDate;
			promo.lastModifiedByUserLogin = loginParams.username
			if (methodParams.promoName) promo.promoName = methodParams.promoName;
			if (methodParams.promoText) promo.promoText = methodParams.promoText;
			
			delegator.store(promo);
			
			def startDate = new Timestamp(methodParams.startDate.getTime());
			def endDate = new Timestamp(methodParams.endDate.getTime());
			String storeNumber = ProductStoreWorker.getProductStore(request).productStoreId;
			//Debug.logInfo ( "\n\n\n\n\n\n storeNumber = " + storeNumber, "soapapi");
		
			def storePromo = EntityQuery.use(delegator).from("ProductStorePromoAppl").where("productStoreId",storeNumber,"productPromoId",methodParams.promoId,"fromDate",startDate).queryFirst();
			
			if (!storePromo) {
				storePromo = delegator.create("ProductStorePromoAppl",[
					productStoreId:storeNumber,
					productPromoId:productPromoId,
					fromDate:startDate
				]);
			}
			
			storePromo.thruDate = endDate;
			delegator.store(storePromo);
			
			// ProductPromoRule
			def promoRule = EntityQuery.use(delegator)
				.from("ProductPromoRule").where("productPromoId",methodParams.promoId,"productPromoRuleId","A1").queryFirst();
		
			if (!promoRule) {
				promoRule = delegator.create("ProductPromoRule",[
						productPromoId:methodParams.promoId,
						productPromoRuleId:"A1",
						ruleName:"RuleA1"
				]);
			}
				
			// ProductPromoAction
			def promoAction = EntityQuery.use(delegator)
				.from("ProductPromoAction").where("productPromoId",methodParams.promoId,"productPromoRuleId","A1").queryFirst();
	
			if (!promoAction) {
				promoAction = delegator.create("ProductPromoAction",[
						productPromoId:methodParams.promoId,
						productPromoRuleId:"A1",
						productPromoActionSeqId:"A1",
						productPromoActionEnumId:"PROMO_PROD_PRICE",
						orderAdjustmentTypeId:"PROMOTION_ADJUSTMENT",
						useCartQuantity:"N"
				]);
			}
			
			// update
			promoAction.quantity = methodParams.quantity;

			if (methodParams.promoTypeId.equals(PromoType.BuyXItemsForZprice)) promoAction.amount = methodParams.amount / methodParams.quantity;
			else promoAction.amount = methodParams.amount;
			
			delegator.store(promoAction);
			
		} catch(Exception e) {
			//e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
			return res;
		}
		
		res.promoId = productPromoId;
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for insertUpdatePromotion - " + res.toString(), "soapapi");
		return res;
	}
	
	
	
	private IUDResult addRemoveProductToPromotion(LoginParams loginParams,PromotionAddRemoveProductToPromotionParams methodParams,String productAction) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		IUDResult res = new IUDResult();
		
		String methodName = (productAction.equals("add")) ? "addProductToPromotion" : "removeProductToPromotion";
		
		Debug.logInfo ( "~~~ Call method " + methodName, "soapapi");
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
			Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
			return res;
		}
				
		if (!methodParams.promoId) {
			res.resultCode = 2;
			res.resultText = "Missing promoId param";
			Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
			return res;
		}
		
		def promo = EntityQuery.use(delegator).from("ProductPromo").where("productPromoId",methodParams.promoId).queryFirst();
		if (!promo) {
			res.resultCode = 3;
			res.resultText = "Missing Promotion";
			Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
			return res;
		}
		
		def productIdList = ["_EMPTY_"];
		if (methodParams.productId) productIdList.add(methodParams.productId);
		///def product = EntityQuery.use(delegator).from("Product").where("productId",methodParams.productId).queryFirst();
		///if (product) productIdList.add(methodParams.productId);

		def cond;
		if (methodParams.upc) {
			def cond1 = EntityCondition.makeCondition("goodIdentificationTypeId", EntityOperator.LIKE,"UPC%");
			def cond2 = EntityCondition.makeCondition("idValue", EntityOperator.EQUALS,methodParams.upc);
			cond = EntityCondition.makeCondition(cond1,EntityOperator.AND,cond2);
			//Debug.logInfo ( "\n\n\n\n\n cond - " + cond.toString(), "soapapi");
			
			def goodIdentRes = EntityQuery.use(delegator).select("productId").from("GoodIdentification").where(cond).queryList();
			//Debug.logInfo ( "\n\n\n\n\n res - " + goodIdentRes, "soapapi");
			
			if (goodIdentRes) {
				if (methodParams.upc_modifier) {
					for (pr in goodIdentRes) {
						def upcModRec = EntityQuery.use(delegator).select("idValue").from("GoodIdentification")
							.where("goodIdentificationTypeId","UPC_MODIFIER","productId",pr.productId).queryFirst();
						if (upcModRec && methodParams.upc_modifier.equals(upcModRec.idValue.toString())) productIdList.add(pr.productId);
					}
					
				} else for (pr in goodIdentRes) productIdList.add(pr.productId);
			}
		}
		
		cond = EntityCondition.makeCondition("productId",EntityOperator.IN,productIdList);
		//Debug.logInfo ( "\n\n\n\n\n cond - " + cond.toString(), "soapapi");
		def productRecs = EntityQuery.use(delegator).select("productId").from("Product").where(cond).queryList();
		//Debug.logInfo ( "\n\n\n\n\n productRecs - " + productRecs.toString(), "soapapi");
		
		try {
			for (productRec in productRecs) {
				// find product in promotion
				def promoProduct = EntityQuery.use(delegator).from("ProductPromoProduct")
					.where("productPromoId",methodParams.promoId,"productPromoRuleId","A1","productPromoActionSeqId","A1",
						  "productPromoCondSeqId","_NA_","productId",productRec.productId).queryFirst();
				
				if (productAction.equals("add")) {
					// method add - create product in promotion
					if (!promoProduct) {
						promoProduct = delegator.create("ProductPromoProduct",[
							  productPromoId:methodParams.promoId,
							  productPromoRuleId:"A1",
							  productPromoActionSeqId:"A1",
							  productPromoCondSeqId:"_NA_",
							  productId:productRec.productId,
							  productPromoApplEnumId:"PPPA_INCLUDE"
						]);
					}
				} else { 
					// method remove
					if (promoProduct) delegator.removeValue(promoProduct);
				}
			}
		} catch(Exception e) {
			//e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
		
		return res;
	}
	
	
	public IUDResult addProductToPromotion(LoginParams loginParams,PromotionAddRemoveProductToPromotionParams methodParams) {
		return addRemoveProductToPromotion(loginParams,methodParams,"add");
	}
	
	public IUDResult removeProductFromPromotion(LoginParams loginParams,PromotionAddRemoveProductToPromotionParams methodParams) {
		return addRemoveProductToPromotion(loginParams,methodParams,"remove");
	}

	private IUDResult addRemoveCategoryPromotion(LoginParams loginParams,PromotionAddRemoveCategoryToPromotionParams methodParams,String categoryAction) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		String methodName = (categoryAction.equals("add")) ? "addCategoryToPromotion" : "removeCategoryToPromotion";
		
		Debug.logInfo ( "~~~ Call method " + methodName, "soapapi");
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
			Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
			return res;
		}
				
		if (!methodParams.promoId) {
			res.resultCode = 2;
			res.resultText = "Missing promoId param";
			Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
			return res;
		}
		
		def promo = EntityQuery.use(delegator).from("ProductPromo").where("productPromoId",methodParams.promoId).queryFirst();
		if (!promo) {
			res.resultCode = 3;
			res.resultText = "Missing Promotion";
			Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.categoryId) {
			res.resultCode = 4;
			res.resultText = "Missing categoryId param";
			Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
			return res;
		}

		def category = EntityQuery.use(delegator).from("ProductCategory").where("productCategoryId",methodParams.categoryId).queryFirst();
		if (!category) {
			res.resultCode = 5;
			res.resultText = "Missing Category";
			Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
			return res;
		}
	
		try {
			// find category in promotion
			def promoCategory = EntityQuery.use(delegator).from("ProductPromoCategory")
				.where("productPromoId",methodParams.promoId,"productPromoRuleId","A1","productPromoActionSeqId","A1",
					  "productPromoCondSeqId","_NA_","productCategoryId",methodParams.categoryId).queryFirst();
			
			// create product in promotion
			if (categoryAction.equals("add")) {
				if (!promoCategory) {
					promoCategory = delegator.create("ProductPromoCategory",[
						  productPromoId:methodParams.promoId,
						  productPromoRuleId:"A1",
						  productPromoActionSeqId:"A1",
						  productPromoCondSeqId:"_NA_",
						  productCategoryId:methodParams.categoryId,
						  productPromoApplEnumId:"PPPA_INCLUDE",
						  andGroupId:"_NA_"
					]);
				} else {
					res.resultCode = 6;
					res.resultText = "This category is already exists for the promotion";
					Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
					return res;
				}
			} else {
				// categoryAction = "remove"
				if (promoCategory) delegator.removeValue(promoCategory);
				else {
					res.resultCode = 6;
					res.resultText = "That category is not found in that promotion";
					Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
					return res;
				}
			}
		} catch(Exception e) {
			//e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
			return res;
		}
	
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for " + methodName + " - " + res.toString(), "soapapi");
		return res;
	}
		
	public IUDResult addCategoryToPromotion(LoginParams loginParams,PromotionAddRemoveCategoryToPromotionParams methodParams) {
		return addRemoveCategoryPromotion(loginParams,methodParams,"add");
	}
	
	public IUDResult removeCategoryFromPromotion(LoginParams loginParams,PromotionAddRemoveCategoryToPromotionParams methodParams) {
		return addRemoveCategoryPromotion(loginParams,methodParams,"remove");
	}
	
	public PromotionListPromotionsResult listPromotions(LoginParams loginParams,PromotionListPromotionsParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");

		PromotionListPromotionsResult res = new PromotionListPromotionsResult();

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
			def exprs = [];
			
			if (methodParams.name) exprs.add(EntityCondition.makeCondition("promoName", EntityOperator.LIKE,methodParams.name));
			
			def orderBy = UtilMisc.toList("promoName");
			def cond = EntityCondition.makeCondition(exprs, EntityOperator.AND);
			def productListRecs = EntityQuery.use(delegator)
			   .select("productPromoId","promoName","promoText").from("ProductPromo").where(cond).orderBy(orderBy).queryList();
			      
			def productList = [];
			for (rec in productListRecs) {
				def promo = new Promotion();
				promo.productPromoId = rec.productPromoId;
				promo.promoName = rec.promoName;
				promo.promoText = rec.promoText;
				productList.add(promo);
			}
						
			res.resultCode = 0;
			res.resultText = "Success";
			res.promotions = productList;
		
		} catch(Exception e) {
			//e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
		
		return res;
	}

	public InsertUpdatePromotionResult insertUpdateComboPromotion(LoginParams loginParams,PromotionInsertUpdateComboPromoParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");  // getContent(Class<T> format)
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		InsertUpdatePromotionResult res = new InsertUpdatePromotionResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method insertUpdateComboPromotion", "soapapi");
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
			Debug.logInfo ( "~~~ Result for insertUpdateComboPromotion - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.startDate) {
			res.resultCode = 3;
			res.resultText = "Missing startDate param";
			Debug.logInfo ( "~~~ Result for insertUpdateComboPromotion - " + res.toString(), "soapapi");
			return res;
		}

		if (!methodParams.endDate) {
			res.resultCode = 4;
			res.resultText = "Missing endDate param";
			Debug.logInfo ( "~~~ Result for insertUpdateComboPromotion - " + res.toString(), "soapapi");
			return res;
		}
		
		def productPromoId = (methodParams.promoId) ? methodParams.promoId : delegator.getNextSeqId("ProductPromo");
		
		Date date = new Date();
		Timestamp actualDate= new Timestamp(date.getTime());

		try {
			def promo = EntityQuery.use(delegator).from("ProductPromo").where("productPromoId",methodParams.promoId).queryFirst();
		
			if (!promo) {
				promo = delegator.create("ProductPromo",[
						productPromoId:productPromoId,
						userEntered:"Y",
						showToCustomer:"Y",
						requireCode:"N",
						createdDate:actualDate,
						createdByUserLogin:loginParams.username
				]);
			}
		
			promo.lastModifiedDate = actualDate;
			promo.lastModifiedByUserLogin = loginParams.username
			if (methodParams.promoName) promo.promoName = methodParams.promoName;
			if (methodParams.promoText) promo.promoText = methodParams.promoText;
			
			delegator.store(promo);
			
			def hasPartThird = methodParams.categoryPartThird && methodParams.categoryPartThird.categoryId;
			def hasPartFourth = methodParams.categoryPartFourth && methodParams.categoryPartFourth.categoryId;
			
			// ProductPromoRule
			def promoRule = EntityQuery.use(delegator)
				.from("ProductPromoRule").where("productPromoId",methodParams.promoId,"productPromoRuleId","R1").queryFirst();
		
			if (!promoRule) {
				promoRule = delegator.create("ProductPromoRule",[
						productPromoId:productPromoId,
						productPromoRuleId:"R1",
						ruleName:"Rule R1"
				]);
			}
			
			// conditions	
			createUpdateComboPromoCondition(productPromoId,"R1","1",methodParams.categoryPartFirst.quantity,delegator);
			createUpdateComboPromoCondition(productPromoId,"R1","2",methodParams.categoryPartSecond.quantity,delegator);
			if (hasPartThird) createUpdateComboPromoCondition(productPromoId,"R1","3",methodParams.categoryPartSecond.quantity,delegator);
			if (hasPartThird && hasPartFourth) createUpdateComboPromoCondition(productPromoId,"R1","4",methodParams.categoryPartSecond.quantity,delegator);
			
			// actions
			createUpdateComboPromoAction(productPromoId,"R1","S1",methodParams.categoryPartFirst,delegator);
			createUpdateComboPromoAction(productPromoId,"R1","S2",methodParams.categoryPartSecond,delegator);
			if (hasPartThird) createUpdateComboPromoAction(productPromoId,"R1","S1",methodParams.categoryPartThird,delegator);
			if (hasPartThird && hasPartFourth) createUpdateComboPromoAction(productPromoId,"R1","S1",methodParams.categoryPartFourth,delegator);

			removePromoCategories(productPromoId,"R1",delegator);
						
			// action categories
			createUpdateComboPromoCategory(productPromoId,"R1","S1","_NA_",methodParams.categoryPartFirst.categoryId,delegator);
			createUpdateComboPromoCategory(productPromoId,"R1","S2","_NA_",methodParams.categoryPartSecond.categoryId,delegator);			
			if (hasPartThird) createUpdateComboPromoCategory(productPromoId,"R1","S3","_NA_",methodParams.categoryPartThird.categoryId,delegator);
			if (hasPartThird && hasPartFourth) createUpdateComboPromoCategory(productPromoId,"R1","S4","_NA_",methodParams.categoryPartFourth.categoryId,delegator);
	
			// condition categories
			createUpdateComboPromoCategory(productPromoId,"R1","_NA_","1",methodParams.categoryPartFirst.categoryId,delegator);
			createUpdateComboPromoCategory(productPromoId,"R1","_NA_","2",methodParams.categoryPartSecond.categoryId,delegator);
			if (hasPartThird) createUpdateComboPromoCategory(productPromoId,"R1","_NA_","3",methodParams.categoryPartThird.categoryId,delegator);
			if (hasPartThird && hasPartFourth) createUpdateComboPromoCategory(productPromoId,"R1","_NA_","4",methodParams.categoryPartFourth.categoryId,delegator);

			// calendar
			Calendar calStartDate = Calendar.getInstance(), calEndDate = Calendar.getInstance();
			calStartDate.setTime(methodParams.startDate);
			calEndDate.setTime(methodParams.endDate);
			
			Calendar calDate = calStartDate;
			
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

			def productStoreId = ProductStoreWorker.getProductStore(request).productStoreId;

			def promoApplRecs = EntityQuery.use(delegator).from("ProductStorePromoAppl").where("productPromoId",productPromoId).queryList();
			delegator.removeAll(promoApplRecs);

			// check for weekly
			if (!methodParams.sundayAviability && !methodParams.mondayAviability && !methodParams.tuesdayAviability && !methodParams.wednesayAviability &&
				!methodParams.thursdayAviability && !methodParams.fridayAviability && !methodParams.saturdayAviability) {
			
				Timestamp tsStartDtime = new Timestamp(methodParams.startDate.getTime());
				Timestamp tsEndDtime = new Timestamp(methodParams.endDate.getTime());
				
				delegator.create("ProductStorePromoAppl",[
					productStoreId:productStoreId,
					productPromoId:productPromoId,
					fromDate:tsStartDtime,
					thruDate:tsEndDtime
				]);
			} else {
				while (calDate.compareTo(calEndDate)<=0) {
					Date dDate = calDate.getTime();
					String sDate = dateFormat.format(dDate);
		
					switch(calDate.get(Calendar.DAY_OF_WEEK)) {
						case Calendar.SUNDAY:					
							createProductStorePromoApplRec(methodParams.sundayAviability,productStoreId,productPromoId,sDate,delegator);
						  break;
						case Calendar.MONDAY:
							createProductStorePromoApplRec(methodParams.mondayAviability,productStoreId,productPromoId,sDate,delegator);
						  break;
						case Calendar.TUESDAY:
							createProductStorePromoApplRec(methodParams.tuesdayAviability,productStoreId,productPromoId,sDate,delegator);
						  break;
						case Calendar.WEDNESDAY:
							createProductStorePromoApplRec(methodParams.wednesayAviability,productStoreId,productPromoId,sDate,delegator);
						  break;
						case Calendar.THURSDAY:
							createProductStorePromoApplRec(methodParams.thursdayAviability,productStoreId,productPromoId,sDate,delegator);
						  break;
						case Calendar.FRIDAY:
							createProductStorePromoApplRec(methodParams.fridayAviability,productStoreId,productPromoId,sDate,delegator);
						  break;
						case Calendar.SATURDAY:
							createProductStorePromoApplRec(methodParams.saturdayAviability,productStoreId,productPromoId,sDate,delegator);
						  break;
					  }
					  
					calDate.add(Calendar.DATE, 1);
				}
			}
			
		} catch(Exception e) {
			e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for insertUpdateComboPromotion - " + res.toString(), "soapapi");
			return res;
		}
		
		res.promoId = productPromoId;
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for insertUpdateComboPromotion - " + res.toString(), "soapapi");
		return res;
	}
	
	def createProductStorePromoApplRec(def weekdayAviability, def productStoreId, def productPromoId, def sDate, def delegator) {
		SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
		
		if (weekdayAviability && weekdayAviability.startTime && weekdayAviability.endTime) {		
			Date startDtime = dateTimeFormat.parse(sDate + " " + weekdayAviability.startTime);
			Date endDtime = dateTimeFormat.parse(sDate + " " + weekdayAviability.endTime);
			
			Timestamp tsStartDtime = new Timestamp(startDtime.getTime());
			Timestamp tsEndDtime = new Timestamp(endDtime.getTime());
			
			delegator.create("ProductStorePromoAppl",[
				productStoreId:productStoreId,
				productPromoId:productPromoId,
				fromDate:tsStartDtime,
				thruDate:tsEndDtime
			]);
		}
	}
		
	def createUpdateComboPromoAction(def productPromoId, def productPromoRuleId, def productPromoActionSeqId, def categoryPart, def delegator) {
		def promoAction = EntityQuery.use(delegator)
			.from("ProductPromoAction").where("productPromoId",productPromoId,"productPromoRuleId",productPromoRuleId,"productPromoActionSeqId",productPromoActionSeqId).queryList();
		delegator.removeAll(promoAction);	
		
		def productPromoActionEnumId = null;
		if ((!categoryPart.method)||(categoryPart.method == PromoMethod.NoAction)) return false;
		else if (categoryPart.method == PromoMethod.XItemsForYPrice) productPromoActionEnumId = "PROMO_PROD_PRICE";
		else if (categoryPart.method == PromoMethod.XItemsForYDiscount) productPromoActionEnumId = "PROMO_PROD_AMDISC";
		else if (categoryPart.method == PromoMethod.XItemsForYPercentDiscount) productPromoActionEnumId = "PROMO_PROD_DISC";
		else return false;

		promoAction = delegator.create("ProductPromoAction",[
				productPromoId:productPromoId,
				productPromoRuleId:productPromoRuleId,
				productPromoActionSeqId:productPromoActionSeqId,
				productPromoActionEnumId:productPromoActionEnumId, // OLD - "PROMO_PROD_PRICE" - X Product for Y Price  ; PROMO_PROD_AMDISC - X Product for Y Discount ; PROMO_PROD_DISC - X Product for Y% Discount
				orderAdjustmentTypeId:"PROMOTION_ADJUSTMENT",
				useCartQuantity:"N",
				quantity:categoryPart.quantity,
				amount:categoryPart.amount
		]);
			
		return true;
	}

	def createUpdateComboPromoCondition(def productPromoId, def productPromoRuleId, def productPromoCondSeqId, def condValue, def delegator) {
		def promoCond = EntityQuery.use(delegator)
			.from("ProductPromoCond").where("productPromoId",productPromoId,"productPromoRuleId",productPromoRuleId,"productPromoCondSeqId",productPromoCondSeqId).queryFirst();
			
		if (!promoCond) {
			promoCond = delegator.create("ProductPromoCond",[
					productPromoId:productPromoId,
					productPromoRuleId:productPromoRuleId,
					productPromoCondSeqId:productPromoCondSeqId,
					inputParamEnumId:"PPIP_PRODUCT_QUANT",
					operatorEnumId:"PPC_GTE"
			]);
		}
		
		promoCond.condValue = "" + condValue;		
		delegator.store(promoCond);
			
		return true;
	}
	
	def removePromoCategories(def productPromoId, def productPromoRuleId, def delegator) {
		def promoCategories = EntityQuery.use(delegator).from("ProductPromoCategory")
			.where("productPromoId",productPromoId,"productPromoRuleId",productPromoRuleId).queryList();
			
		delegator.removeAll(promoCategories);
	}

	def createUpdateComboPromoCategory(def productPromoId, def productPromoRuleId, def productPromoActionSeqId, def productPromoCondSeqId, def productCategoryId, def delegator) {
		//def promoCategory = EntityQuery.use(delegator).from("ProductPromoCategory")
		//	.where("productPromoId",productPromoId,"productPromoRuleId",productPromoRuleId,"productPromoActionSeqId",productPromoActionSeqId,
		//	  "productPromoCondSeqId",productPromoCondSeqId,"productCategoryId",productCategoryId).queryFirst();
	
		//if (!promoCategory) {
		delegator.create("ProductPromoCategory",[
		  productPromoId:productPromoId,
		  productPromoRuleId:productPromoRuleId,
		  productPromoActionSeqId:productPromoActionSeqId,
		  productPromoCondSeqId:productPromoCondSeqId,
		  productCategoryId:productCategoryId,
		  productPromoApplEnumId:"PPPA_INCLUDE",
		  andGroupId:"_NA_"
		]);
		//}
		
		//promoCategory.productCategoryId = productCategoryId;		
		//delegator.store(promoCategory);
			
		return true;
	}

	
	
	
//PromotionDetailsResult promotionDetails(
//		LoginParams loginParams,
//		PromotionDetailsParams methodParams
//);

	
}
