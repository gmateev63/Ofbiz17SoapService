package com.ofb.api;

import org.apache.ofbiz.base.util.*;
import java.text.SimpleDateFormat;
import org.apache.cxf.phase.PhaseInterceptorChain;
import java.sql.Timestamp;
import org.apache.ofbiz.entity.util.EntityQuery;
import java.util.Date;
import org.apache.ofbiz.entity.condition.*;
import org.apache.ofbiz.product.store.ProductStoreWorker;
import java.util.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.ofbiz.base.util.string.FlexibleStringExpander;
import org.apache.ofbiz.entity.util.EntityUtilProperties;

public class CategoryImpl implements CategoryService {
	
	public CategoryListCategoriesResult listCategories(LoginParams loginParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");

		Debug.logInfo ( "~~~ Call method listCategories", "soapapi");
	
		CategoryListCategoriesResult res = new CategoryListCategoriesResult();

		def tokenParty = Utils.soapUserLoginToken(loginParams.username,loginParams.password,loginParams.token,dispatcher.getDispatchContext());
		def party = null;
		if (tokenParty) {
			party = tokenParty["partyId"];
			res.token = tokenParty["token"];
		}
		
		if (!party) {
			res.resultCode = -1;
			res.resultText = "Wrong username or password";
			Debug.logInfo ( "~~~ Result for listCategories - " + res.toString(), "soapapi");
			return res;
		}

		try {
			def orderBy = UtilMisc.toList("productCategoryId");
			def allCategories = delegator.findList("ProductCategory", null, null, orderBy, null, false);
			
			def categoriesList = new ArrayList();
			for (cat in allCategories) {
				def category = new CategoryResult();
				
				if (cat.productCategoryTypeId.toString().equalsIgnoreCase("INTEGRATION_CATEGORY"))
					category.productCategoryTypeId = CategoryType.INTEGRATION_CATEGORY;
				else if (cat.productCategoryTypeId.toString().equalsIgnoreCase("TAX_CATEGORY"))
					category.productCategoryTypeId = CategoryType.TAX_CATEGORY;				
				else if (cat.productCategoryTypeId.toString().equalsIgnoreCase("ITEM_LIST"))
					category.productCategoryTypeId = CategoryType.ITEM_LIST;
				else if (cat.productCategoryTypeId.toString().equalsIgnoreCase("CATALOG_CATEGORY"))
					category.productCategoryTypeId = CategoryType.CATALOG_CATEGORY;
				else continue;
				
				category.productCategoryId = cat.productCategoryId
				category.categoryName = cat.categoryName
				category.primaryParentCategoryId = cat.primaryParentCategoryId
				category.description = cat.description
				category.longDescription = cat.longDescription
				category.categoryImageUrl = cat.categoryImageUrl
				categoriesList.add(category);
			}
			
			res.resultCode = 0;
			res.resultText = "Success";
			res.categories = categoriesList;
		
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for listCategories - " + res.toString(), "soapapi");
			return res;
		}
		
		Debug.logInfo ( "~~~ Result for listCategories - " + res.toString(), "soapapi");
		return res;
	}
		
	public IUDResult categoryInsertUpdate(LoginParams loginParams,Category methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		IUDResult res = new IUDResult();
				
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
		
		if (!methodParams.productCategoryId) {
			res.resultCode = 2;
			res.resultText = "Missing productCategoryId param";
			return res;
		}
		
		if (!methodParams.productCategoryTypeId) {
			res.resultCode = 3;
			res.resultText = "Missing productCategoryTypeId param";
			return res;
		}

		if (!methodParams.categoryName) {
			res.resultCode = 4;
			res.resultText = "Missing categoryName param";
			return res;
		}
		
		try {
			def productCategory = EntityQuery.use(delegator).from ("ProductCategory").where("productCategoryId",methodParams.productCategoryId).queryFirst();
			
			if (!productCategory) {
				productCategory = delegator.create("ProductCategory",[
					productCategoryId:methodParams.productCategoryId,
					productCategoryTypeId:methodParams.productCategoryTypeId.toString()
				]);
			}
			
			if (methodParams.categoryName) productCategory.categoryName = methodParams.categoryName;
			if (methodParams.primaryParentCategoryId) productCategory.primaryParentCategoryId = methodParams.primaryParentCategoryId;
			if (methodParams.description) productCategory.description = methodParams.description;
			if (methodParams.longDescription) productCategory.longDescription = methodParams.longDescription;
			
			// ------------ 2024-02-27 
			if (methodParams.image) {
				
			  if (methodParams.image.length()<5) {
				// delete images
				productCategory.categoryImageUrl = null;
				productCategory.linkOneImageUrl = null;
				productCategory.linkTwoImageUrl = null;
				def hashDb = EntityQuery.use(delegator).select().from("ProductCategoryAttribute").where("productCategoryId",methodParams.productCategoryId,"attrName","ORIG_IMAGE_MD5").queryFirst();
				if (hashDb) delegator.removeValue(hashDb);
			  } else {
				def imageFile;
				String hash = Utils.getHash(methodParams.image);
				Debug.logInfo ( "~~~ hash = " + hash, "soapapi");
				
				def hashDb = EntityQuery.use(delegator).select().from("ProductCategoryAttribute").where("productCategoryId",methodParams.productCategoryId,"attrName","ORIG_IMAGE_MD5").queryFirst();
				
				boolean processImage = true;
				
				if (hashDb) {
					if (hashDb.attrValue.equals(hash)) {
						def origImg = EntityQuery.use(delegator).select("categoryImageUrl").from("ProductCategory").where("productCategoryId",methodParams.productCategoryId).queryFirst();
						if (origImg && origImg.productCategoryId && origImg.productCategoryId.toString().length()>0 ) processImage = false;
					} else {
						Debug.logInfo ( "~~~ hashDb = " + hashDb, "soapapi");
						hashDb.attrValue = hash;
						delegator.store(hashDb);
					}
				} else {
					delegator.create("ProductCategoryAttribute",[
						productCategoryId:methodParams.productCategoryId,
						attrName:"ORIG_IMAGE_MD5",
						attrValue:hash,
						attrDescription:"Image hash code"
					]);
				}
				
				if (processImage) {
					Debug.logInfo ( "~~~ processImage", "soapapi");
					
					def context = [:];
					context.put("locale",new Locale("en", "US"));
					context.put("delegator",delegator);
					context.put("productId",methodParams.productCategoryId);
					
					File tempFile = null;
					
					def imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog", "image.server.path", delegator), context)
					imageServerPath = imageServerPath.endsWith("/") ? imageServerPath.substring(0, imageServerPath.length()-1) : imageServerPath
					context.imageServerPath = imageServerPath;
					
					def imagesSourcePath;
					def ext = "png";
					
					def imageSrc = methodParams.image.toLowerCase();
					
					if (imageSrc.startsWith("http://")||imageSrc.startsWith("https://")) {
						Debug.logInfo ( "~~~ IMAGE URL", "soapapi");
						imageFile = methodParams.image;
	
						Class groovyClass = new GroovyClassLoader(getClass().getClassLoader()).parseClass(new File("sigma/register/webapp/script/common/ofbPOSUtils.groovy"));
						def ofbPOSUtils = groovyClass.newInstance(dispatcher, delegator, Debug);
						imagesSourcePath = ofbPOSUtils.getParam("imagesSourcePath", null, "register");
						
						if (imageSrc.contains(".jpg")) ext = "jpg";
						else if (imageSrc.contains(".gif")) ext = "gif";
						
					} else {
						Debug.logInfo ( "~~~ IMAGE BASE64", "soapapi");
						byte[] decoded = Base64.getDecoder().decode(methodParams.image);
						tempFile = File.createTempFile("/tmp/img_", ".tmp");
						FileUtils.writeByteArrayToFile(tempFile, decoded);
						
						imageFile = tempFile.name;
						imagesSourcePath = "/tmp";
					} 
					
					if (methodParams.imageType) ext = methodParams.imageType;
					
					if (imageFile) {
					  String originalFileName = "original." + ext;
					  def imgMap = Utils.resizeAndStoreImages(true,imageFile,imageServerPath,methodParams.productCategoryId,originalFileName,imagesSourcePath,context);
					  if (imgMap) {
						  def imagesUrl = imgMap.get("small");
						  def lastIndex = imagesUrl.lastIndexOf("/");
						  imagesUrl = imagesUrl.substring(0,lastIndex+1);
						  productCategory.categoryImageUrl = imgMap.get("large");
						  productCategory.linkTwoImageUrl = imgMap.get("detail");
					  }
					  //Debug.logInfo ( "\n\n\n\n\n imgMap =>" + imgMap, "ItemFileLoader");
					  if (tempFile) tempFile.delete();
					}
				} // processImage
			  } // else
			} // image
						
			delegator.store(productCategory);
			
//-- 2023-09-05 commented 2023-10-14 uncommented
			if (methodParams.primaryParentCategoryId) {
				
				Date date = new Date();
				Timestamp tsFromDate = new Timestamp(date.getTime());
				
				def cond = makeRollupCond(methodParams.productCategoryId,methodParams.primaryParentCategoryId,tsFromDate);
				def productCategoryRollup = EntityQuery.use(delegator).from ("ProductCategoryRollup").where(cond).queryFirst();
				
				if (!productCategoryRollup) {
					delegator.create("ProductCategoryRollup",[
						productCategoryId:methodParams.productCategoryId,
						parentProductCategoryId:methodParams.primaryParentCategoryId,
						fromDate:tsFromDate
					]);
				}
							
				def productCategoryLink = EntityQuery.use(delegator).from ("ProductCategoryLink")
						.where("productCategoryId",methodParams.primaryParentCategoryId,"linkInfo",methodParams.productCategoryId).queryFirst();
				
				if (!productCategoryLink) {
					delegator.create("ProductCategoryLink",[
						productCategoryId:methodParams.primaryParentCategoryId,
						linkSeqId:"0",
						fromDate:tsFromDate,
						linkInfo:methodParams.productCategoryId,
						linkTypeEnumId: "PCLT_CAT_ID",
						titleText:methodParams.categoryName
					]);
				}
			}
			
			// Save tax % to TaxAuthorityRateProduct
			if (methodParams.productCategoryTypeId.equals(CategoryType.TAX_CATEGORY)) {
				String storeNumber = ProductStoreWorker.getProductStore(request).productStoreId;
				
				def taxAuthorityRateSeqId = storeNumber + "_" + methodParams.productCategoryId;
				//def taxAuthRec = EntityQuery.use(delegator).from("TaxAuthorityRateProduct").where("taxAuthorityRateSeqId",taxAuthorityRateSeqId).queryFirst();
				def taxAuthRec = EntityQuery.use(delegator).from("TaxAuthorityRateProduct").where("productStoreId",storeNumber,"productCategoryId",methodParams.productCategoryId).queryFirst();
				
				if (!taxAuthRec) {
					Date date = new Date();
					Timestamp tsFromDate = new Timestamp(date.getTime());
					
					taxAuthRec = delegator.create("TaxAuthorityRateProduct",[
						taxAuthorityRateSeqId:taxAuthorityRateSeqId,
						taxAuthGeoId:"USA",
						taxAuthPartyId:"USA_IRS",
						taxAuthorityRateTypeId:"SALES_TAX",
						productStoreId:storeNumber,
						productCategoryId:methodParams.productCategoryId,						
						taxShipping:"N",
						taxPromotions:"N",
						fromDate:tsFromDate
					]);
				}
				
				if (methodParams.description) productCategory.description = methodParams.description;
				
				taxAuthRec.description = (methodParams.description) ? methodParams.description : methodParams.categoryName;
				if (methodParams.taxPercentage) taxAuthRec.taxPercentage = methodParams.taxPercentage;

				delegator.store(taxAuthRec);
			}
		
		} catch(Exception e) {
			e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		  
		return res;
	}
	
	private EntityCondition makeRollupCond(String productCategoryId,String parentProductCategoryId,Timestamp tsFromDate) {
		List datePars = []
		datePars.add(EntityCondition.makeCondition("thruDate", EntityOperator.EQUALS, null))
		datePars.add(EntityCondition.makeCondition("thruDate", EntityOperator.GREATER_THAN, tsFromDate))
		def dateCond = EntityCondition.makeCondition(datePars, EntityOperator.OR)
		
		List categoryPars = []
		categoryPars.add(EntityCondition.makeCondition("productCategoryId", EntityOperator.EQUALS, productCategoryId,))
		categoryPars.add(EntityCondition.makeCondition("parentProductCategoryId", EntityOperator.EQUALS, parentProductCategoryId))
		def categoryCond = EntityCondition.makeCondition(categoryPars, EntityOperator.AND)

		EntityCondition cond = EntityCondition.makeCondition([dateCond, categoryCond], EntityOperator.AND)
		
		return cond;
	}
	
	public IUDResult categoryRollupLinkInsertUpdate(LoginParams loginParams,CategoryRollup methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		IUDResult res = new IUDResult();
				
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
		
		if (!methodParams.productCategoryId) {
			res.resultCode = 2;
			res.resultText = "Missing productCategoryId param";
			return res;
		}
		
		if (!methodParams.parentProductCategoryId) {
			res.resultCode = 3;
			res.resultText = "Missing parentProductCategoryId param";
			return res;
		}
		
		Date date = new Date();
		Timestamp tsFromDate = new Timestamp(date.getTime());
		
		try {
			def cond = makeRollupCond(methodParams.productCategoryId,methodParams.parentProductCategoryId,tsFromDate);
			def productCategoryRollup = EntityQuery.use(delegator).from ("ProductCategoryRollup").where(cond).queryFirst();
			
			if (!productCategoryRollup) {
				delegator.create("ProductCategoryRollup",[
					productCategoryId:methodParams.productCategoryId,
					parentProductCategoryId:methodParams.parentProductCategoryId,
					fromDate:tsFromDate
				]);

				productCategoryRollup = EntityQuery.use(delegator).from ("ProductCategoryRollup").where(cond).queryFirst();
			}
			
			if (methodParams.sequenceNum) productCategoryRollup.sequenceNum = methodParams.sequenceNum;
						
			delegator.store(productCategoryRollup);
						
			def productCategoryLink = EntityQuery.use(delegator).from ("ProductCategoryLink")
					.where("productCategoryId",methodParams.parentProductCategoryId,"linkInfo",methodParams.productCategoryId).queryFirst();
			
			if (!productCategoryLink) {
				delegator.create("ProductCategoryLink",[
					productCategoryId:methodParams.parentProductCategoryId,
					linkSeqId:"0",
					fromDate:tsFromDate,
					linkInfo:methodParams.productCategoryId,
					linkTypeEnumId: "PCLT_CAT_ID"
				]);

				productCategoryLink = EntityQuery.use(delegator).from ("ProductCategoryLink")
				   .where("productCategoryId",methodParams.parentProductCategoryId,"linkInfo",methodParams.productCategoryId,
					   "fromDate",tsFromDate).queryFirst();
			}
			
			if (methodParams.comments) productCategoryLink.comments = methodParams.comments;
			if (methodParams.sequenceNum) productCategoryLink.sequenceNum = methodParams.sequenceNum;
			if (methodParams.titleText) productCategoryLink.titleText = methodParams.titleText;
			if (methodParams.detailText) productCategoryLink.detailText = methodParams.detailText;
			if (methodParams.imageUrl) productCategoryLink.imageUrl = methodParams.imageUrl;
			if (methodParams.imageTwoUrl) productCategoryLink.imageTwoUrl = methodParams.imageTwoUrl;
			
			delegator.store(productCategoryLink);
			
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		  
		return res;
	}
		
	public IUDResult categoryRollupLinkDelete(LoginParams loginParams,CategoryRollupKey methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		IUDResult res = new IUDResult();
				
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
		
		if (!methodParams.productCategoryId) {
			res.resultCode = 2;
			res.resultText = "Missing productCategoryId param";
			return res;
		}
		
		if (!methodParams.parentProductCategoryId) {
			res.resultCode = 3;
			res.resultText = "Missing parentProductCategoryId param";
			return res;
		}
				
		try {
			Date date = new Date();
			Timestamp tsDate = new Timestamp(date.getTime());
				
			def cond = makeRollupCond(methodParams.productCategoryId,methodParams.parentProductCategoryId,tsDate);
			def productCategoryRollups = EntityQuery.use(delegator).from("ProductCategoryRollup").where(cond).queryList();
			
			if (productCategoryRollups) {
				for (rollup in productCategoryRollups) { 
					rollup.thruDate =  tsDate;
					delegator.store(rollup);
				}
					
			} else {
				res.resultCode = 2;
				res.resultText = "This Element not found";
				return res;
			}
			
			def productCategoryLinks = EntityQuery.use(delegator).from("ProductCategoryLink")
			  .where("productCategoryId",methodParams.parentProductCategoryId,"linkInfo",methodParams.productCategoryId).queryList();

			if (productCategoryLinks) {
				for (link in productCategoryLinks) delegator.removeValue(link);
			}
						
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		  
		return res;
	}
	
	
public CategoryListCategoryRollupLinksResult listCategoryRollupLinks(LoginParams loginParams, CategoryListCategoryRollupLinksParams methodParams) {
	def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
	def dispatcher = request.getAttribute("dispatcher");
	def delegator  = request.getAttribute("delegator");

	Debug.logInfo ( "~~~ Call method listCategoryRollupLinks", "soapapi");

	CategoryListCategoryRollupLinksResult res = new CategoryListCategoryRollupLinksResult();

	def tokenParty = Utils.soapUserLoginToken(loginParams.username,loginParams.password,loginParams.token,dispatcher.getDispatchContext());
	def party = null;
	if (tokenParty) {
		party = tokenParty["partyId"];
		res.token = tokenParty["token"];
	}
	
	if (!party) {
		res.resultCode = -1;
		res.resultText = "Wrong username or password";
		Debug.logInfo ( "~~~ Result for listCategoryRollupLinks - " + res.toString(), "soapapi");
		return res;
	}	

	try {
		Date date = new Date();
		Timestamp tsDate = new Timestamp(date.getTime());
		
		def condFrom = EntityCondition.makeCondition("fromDate", EntityOperator.LESS_THAN_EQUAL_TO, tsDate);
		List datePars = [];
		datePars.add(EntityCondition.makeCondition("thruDate", EntityOperator.EQUALS, null));
		datePars.add(EntityCondition.makeCondition("thruDate", EntityOperator.GREATER_THAN_EQUAL_TO, tsDate));
		def condThru = EntityCondition.makeCondition(datePars, EntityOperator.OR);
		
		def cond = EntityCondition.makeCondition(condFrom, EntityOperator.AND, condThru);
		
		if (methodParams.parentCategoryId) {
			def condCat = EntityCondition.makeCondition("parentProductCategoryId", EntityOperator.EQUALS, methodParams.parentCategoryId);
			cond = EntityCondition.makeCondition(cond, EntityOperator.AND, condCat);
		}
		
		def productCategoryRollups = EntityQuery.use(delegator).select("productCategoryId","parentProductCategoryId","fromDate","thruDate")
		     .from("ProductCategoryRollup").where(cond).orderBy("parentProductCategoryId","productCategoryId").queryList();
		
		def categoriesList = [];
		for (rollup in productCategoryRollups) {
			def cat = EntityQuery.use(delegator).select()
				.from("ProductCategory").where("productCategoryId",rollup.parentProductCategoryId).queryFirst();
			
			def category = new CategoryRollupLinkResult();
			
			if (cat.productCategoryTypeId.toString().equalsIgnoreCase("INTEGRATION_CATEGORY"))
				category.productCategoryTypeId = CategoryType.INTEGRATION_CATEGORY;
			else if (cat.productCategoryTypeId.toString().equalsIgnoreCase("TAX_CATEGORY"))
				category.productCategoryTypeId = CategoryType.TAX_CATEGORY;
			else if (cat.productCategoryTypeId.toString().equalsIgnoreCase("ITEM_LIST"))
				category.productCategoryTypeId = CategoryType.ITEM_LIST;
			else if (cat.productCategoryTypeId.toString().equalsIgnoreCase("CATALOG_CATEGORY"))
				category.productCategoryTypeId = CategoryType.CATALOG_CATEGORY;
			else continue;

			category.productCategoryId = rollup.productCategoryId;
			category.parentCategoryId = rollup.parentProductCategoryId;
			
			if (methodParams.productCategoryTypeId) 
				if (!category.productCategoryTypeId.equals(methodParams.productCategoryTypeId)) continue;
				
			categoriesList.add(category);
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		res.rollupLinks = categoriesList;
	
	} catch(Exception e) {
		e.printStackTrace();
		res.resultCode = 1;
		res.resultText = "System Error: " + e.getMessage();
		Debug.logInfo ( "~~~ Result for listCategoryRollupLinks - " + res.toString(), "soapapi");
		return res;
	}
	
	Debug.logInfo ( "~~~ Result for listCategoryRollupLinks - " + res.toString(), "soapapi");
	return res;
}
	
	
}
