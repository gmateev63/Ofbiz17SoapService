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
import org.apache.ofbiz.service.DispatchContext;
import java.time.LocalDateTime;
import static java.time.temporal.ChronoUnit.MILLIS;
import java.util.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.base.util.string.FlexibleStringExpander;
import org.apache.ofbiz.service.ModelService;

public class ProductImpl implements ProductService {

	def setPrice(delegator,methodParams) {
		def exprs = [
			EntityCondition.makeCondition("productId", EntityOperator.EQUALS, methodParams.productId),
			EntityCondition.makeCondition("productPriceTypeId", methodParams.priceType.toString()),
			EntityCondition.makeCondition("fromDate", EntityOperator.LESS_THAN_EQUAL_TO, new Timestamp(methodParams.priceStartingDate.getTime()))
			];
		def productPrice = EntityQuery.use(delegator).from ("ProductPrice").where(exprs).orderBy("-fromDate").queryFirst();
		
		if (productPrice) {
			if (productPrice.price == methodParams.price) return 0;
			if (productPrice.fromDate == methodParams.priceStartingDate) return 1;
			
			// set thruDate to the old priuce
			productPrice.thruDate = new Timestamp(methodParams.priceStartingDate.getTime());
			productPrice.store();
		}
		
		def thruDate = null;
		if (methodParams.priceEndingDate) thruDate = new Timestamp(methodParams.priceEndingDate.getTime());
		
        delegator.create("ProductPrice",[
        	productId:methodParams.productId,
        	productPriceTypeId:methodParams.priceType.toString(),
        	productPricePurposeId:"PURCHASE",
        	currencyUomId:"USD",
        	productStoreGroupId:"_NA_",
        	fromDate:new Timestamp(methodParams.priceStartingDate.getTime()),
        	thruDate:thruDate,
        	price:methodParams.price
        ]);		
        
        return 0;	
	}
	
	private boolean verifyRequiredParams(methodParams) {
		
		Debug.logInfo ( "\n\n\n\n\n methodParams.name" + methodParams.name, "soapapi");
		Debug.logInfo ( "\n methodParams.description" + methodParams.description, "soapapi");
		Debug.logInfo ( "\n methodParams.priceType" + methodParams.priceType, "soapapi");
		Debug.logInfo ( "\n methodParams.priceStartingDate" + methodParams.priceStartingDate, "soapapi");
		
		if (!methodParams.name || !methodParams.description || !methodParams.priceType || !methodParams.priceStartingDate) return false
		return true;
	}
	
	private def createProduct(def methodParams,def delegator) {
		def qtyUomId = null;
		if (methodParams.upc_modifier) qtyUomId = 'OTH_box';
		
		delegator.create("Product",[
			productId:methodParams.productId,
			productTypeId:"FINISHED_GOOD",
			internalName:methodParams.name,
			productName:methodParams.name,
			description:methodParams.description,
			longDescription:methodParams.description,
			requireInventory:'N',
			requireAmount:'N',
			taxable:'Y',
			isVirtual:'N',
			isVariant:'N',
			inShippingBox:'N',
			quantityUomId: qtyUomId,
			lotIdFilledIn:"Allowed"
		]);
	
		return EntityQuery.use(delegator).from("Product").where("productId",methodParams.productId).queryFirst();
	}

    public IUDResult updateItemAndPrice(LoginParams loginParams,ProductUpdateItemAndPriceParams methodParams) {
		
		def diffBeforeDelProductCategoryMembers=-1, diffBeforeTimeCategory=-1,  diffTimeCategory=-1,diffTimeGoodIdentRemove=-1,diffTimeFinal=-1, timeGoodIdentRemove=null;
		
		LocalDateTime beforeLogin = LocalDateTime.now();
		
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
	    IUDResult res = new IUDResult();		
		EntityCondition condProduct = null, condType = null, cond = null;
		
		//Debug.logInfo ( "~~~ Call method updateItemAndPrice", "soapapi");
		//Debug.logInfo ( "~~~ methodParams = " + methodParams.toString(), "soapapi");
		
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
	
		LocalDateTime afterLogin = LocalDateTime.now();
		def loginTime = MILLIS.between(beforeLogin,afterLogin);
		
        if (!methodParams.productId) {
		    res.resultCode = 2;
		    res.resultText = "Missing productId";
			Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
	        return res;
		}
		
		String sUpcModifier = null;
		if (methodParams.upc_modifier) {
			try {
				int iUpcModifier = Integer.valueOf(methodParams.upc_modifier);
				if (iUpcModifier>0) sUpcModifier = "" + iUpcModifier;
			} catch (NumberFormatException e) {
			    res.resultCode = 3;
			    res.resultText = "Error in upc_modifier param.";
				Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
		        return res;
			}
		}

   		try {
	        def product = EntityQuery.use(delegator).from("Product").where("productId",methodParams.productId).queryFirst();
			
			LocalDateTime afterFirstSelect = LocalDateTime.now();
			def diffAfterFirstSelect = MILLIS.between(afterLogin,afterFirstSelect);
			
			def prodDataLock = null;
	        if (!product) {
	        	if (!verifyRequiredParams(methodParams)) {
				    res.resultCode = 3;
				    res.resultText = "Missing required param";
					Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
			        return res;
	        	}

				product = createProduct(methodParams,delegator);
				//LocalDateTime afterCreateProduct = LocalDateTime.now();
				//timeCreateProduct = MILLIS.between(afterLogin,afterCreateProduct);
				
				 // Find Other product with the same UPC
				 if (methodParams.upc!=null) {
					 List<EntityExpr> exp1 = UtilMisc.toList(EntityCondition.makeCondition("goodIdentificationTypeId",EntityOperator.EQUALS,"UPCA"),
						 EntityCondition.makeCondition("goodIdentificationTypeId",EntityOperator.EQUALS,"UPCE"));
					 def cnd0 = EntityCondition.makeCondition(exp1,EntityOperator.OR);
					 def cnd1 = EntityCondition.makeCondition("idValue",EntityOperator.EQUALS,methodParams.upc);
					 def cnd2 = EntityCondition.makeCondition("productId",EntityOperator.NOT_EQUAL,methodParams.productId);
					 cond = EntityCondition.makeCondition(cnd0,EntityOperator.AND,cnd1);
					 cond = EntityCondition.makeCondition(cond,EntityOperator.AND,cnd2);
					 
					 def otherUpcs = EntityQuery.use(delegator).select("productId").from("GoodIdentification").where(cond).queryList();
					 //Debug.logInfo ( "\n\n\n~~~ otherUpcs = " + otherUpcs, "soapapi");
					 
					 // if exists other products with the same UPC
					 if (otherUpcs) {
						 if (!sUpcModifier) {
							 res.resultCode = 5;
							 res.resultText = "Missing upc_modifier Error";
							 return res;
						 } else {
							 def modifierExists = null;
							 def masterProductId = null;
							 for (otherUpc in otherUpcs) {
								 modifierExists = EntityQuery.use(delegator).from("GoodIdentification")
									.where("productId",otherUpc.productId,"goodIdentificationTypeId",'UPC_MODIFIER').queryFirst();
								 if (!modifierExists) {
									 masterProductId = otherUpc.productId;
									 break;
								 }
							 }
							 // Select and insert into product_assoc
							 if (masterProductId) {
								 def prodAssoc = EntityQuery.use(delegator).from("ProductAssoc")
									.where("productId",masterProductId,"productIdTo",methodParams.productId,"productAssocTypeId","ALTERNATIVE_ofb").queryFirst();
									
								 if (!prodAssoc) {
									 delegator.create("ProductAssoc",[
										 productId:masterProductId,
										 productIdTo:methodParams.productId,
										 productAssocTypeId:'ALTERNATIVE_ofb',
										 quantity:(methodParams.quantityIncluded)?methodParams.quantityIncluded:1,
										 fromDate:new Timestamp(methodParams.priceStartingDate.getTime())
									 ]);
								 }
								 
								 // TODO ?Update product set quantityUomId='OTH_ea' where productId=masterProductId?
								 def masterProduct = EntityQuery.use(delegator).from("Product").where("productId",masterProductId).queryFirst();
								 if (masterProduct) {
									 masterProduct.quantityUomId='OTH_ea';
									 delegator.store(masterProduct);
								 }
							 }
						 }
					 }
				 }
				 
				 //LocalDateTime afterFullCreateProduct = LocalDateTime.now();
				 //timeFullCreateProduct = MILLIS.between(afterLogin,afterFullCreateProduct);
				 timeGoodIdentRemove = LocalDateTime.now(); // ???
 
	        } else { // update
				
				// check for lock fields
				
				prodDataLock = EntityQuery.use(delegator).from("ProductAttribute").where("productId",methodParams.productId, "attrName", "DATA_LOCK").queryFirst();
				
				def vUpc = Utils.getUpc(methodParams.productId,delegator);
				if ((methodParams.upc!=null)&&(vUpc.upc!=null)&&(!methodParams.upc.equals(vUpc.upc))) {
					//Debug.logInfo ( "\n\n\n\n\n~~~ vUpc: " + vUpc, "soapapi");
					//Debug.logInfo ( "\n vUpc.upc: " + vUpc.upc, "soapapi");
					res.resultCode = 4;
					res.resultText = "upc Error";
					Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
					return res;
				}
							
				// delete links to categories INTEGRATION_CATEGORY and TAX_CATEGORY			
				List<EntityExpr> exprs = UtilMisc.toList(EntityCondition.makeCondition("productCategoryTypeId", EntityOperator.EQUALS, "INTEGRATION_CATEGORY"),
					EntityCondition.makeCondition("productCategoryTypeId", EntityOperator.EQUALS, "TAX_CATEGORY"));
				  
				cond = EntityCondition.makeCondition(exprs, EntityOperator.OR);
			
				LocalDateTime beforeTimeCategory = LocalDateTime.now();
				diffBeforeTimeCategory = MILLIS.between(afterFirstSelect,beforeTimeCategory);
				
				def productCatIntegrTaxList = EntityQuery.use(delegator).from ("ProductCategory").where(cond).queryList();
				
				for (def catIntegrTax : productCatIntegrTaxList) {
					if (catIntegrTax.productCategoryId.equals(product.primaryProductCategoryId)) product.primaryProductCategoryId = null;
					
					def productCategoryMembers = EntityQuery.use(delegator).from ("ProductCategoryMember")
					    .where("productId",methodParams.productId, "productCategoryId",catIntegrTax.productCategoryId).queryList();
				
					if (productCategoryMembers) {
						for (pcm in productCategoryMembers) delegator.removeValue(pcm);
					}
				}
				
				def timeCategory = LocalDateTime.now();
				diffTimeCategory = MILLIS.between(beforeTimeCategory,LocalDateTime.now());
				
				// Delete Good Identifications for this product
				condProduct = EntityCondition.makeCondition("productId", EntityOperator.EQUALS,methodParams.productId);
				condType = EntityCondition.makeCondition("goodIdentificationTypeId", EntityOperator.NOT_LIKE,"UPC%");
				cond = EntityCondition.makeCondition(condProduct,EntityOperator.AND,condType);
				
				def goodIdents = EntityQuery.use(delegator).from ("GoodIdentification").where(cond).queryList();
				
				if (goodIdents) {
					for (gid in goodIdents) delegator.removeValue(gid);
				}

				timeGoodIdentRemove = LocalDateTime.now();
				diffTimeGoodIdentRemove = MILLIS.between(timeCategory,timeGoodIdentRemove);
				
				// api update - api ne pipa name, desc I long desc ako db int name <> db name	
				if (methodParams.name) {
					if (product.productName == product.internalName) product.productName = methodParams.name;
					product.internalName = methodParams.name;
				}
				
				if (methodParams.description) {
					if ((!(prodDataLock && prodDataLock.attrValue.indexOf("d") != -1)) && (product.productName == product.internalName))   {
						product.description = methodParams.description;
						product.longDescription = methodParams.description;
					}
				}
				
			} // update
	
			
			if (methodParams.introductionDate) product.introductionDate = new Timestamp(methodParams.introductionDate.getTime());
			if (methodParams.salesDiscontinuationDate) product.salesDiscontinuationDate = new Timestamp(methodParams.salesDiscontinuationDate.getTime());
	        
			def productCategoryList = EntityQuery.use(delegator).from ("ProductCategory").queryList();
			def productCategoryMemberList = EntityQuery.use(delegator).from ("ProductCategoryMember").where("productId",methodParams.productId).queryList();
			
			if (methodParams.department) {
				def cat = methodParams.department;
				
				if (methodParams.adhockCategoryCreate) {
					if (!productCategoryList.productCategoryId.contains(cat)) {
						delegator.create("ProductCategory",[
							productCategoryId:cat,
							categoryName:cat,
							//productCategoryTypeId:"INTEGRATION_CATEGORY" // TODO Change
							productCategoryTypeId:"DEPARTMENT_CATEGORY" // TODO Change
						]);
					}
				} else {
					if (!productCategoryList.productCategoryId.contains(cat)) {
						res.resultCode = 4;
						res.resultText = "Product Category Error";
						Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
						return res;
					} else {
						def productCategoryRec = EntityQuery.use(delegator).from("ProductCategory").where("productCategoryId",cat).queryFirst();
						if (productCategoryRec) {
							productCategoryRec.productCategoryTypeId = "DEPARTMENT_CATEGORY";
							delegator.store(productCategoryRec);
						}
					}
				}

				// Add Category
				if (!productCategoryMemberList.productCategoryId.contains(cat)) {
					def catDate = methodParams.priceStartingDate;
					if (!catDate) catDate = new Date();
					delegator.create("ProductCategoryMember",[
						 productId:methodParams.productId,
						 productCategoryId:cat,
						 fromDate:new Timestamp(catDate.getTime()),
					 ]);
			     }

			     ////product.primaryProductCategoryId = cat;
				
			}

			if (methodParams.categories) {
				for (def cat : methodParams.categories) {

					if (methodParams.adhockCategoryCreate) {
						if (!productCategoryList.productCategoryId.contains(cat)) {
							delegator.create("ProductCategory",[
								productCategoryId:cat,
								categoryName:cat,
								productCategoryTypeId:"INTEGRATION_CATEGORY"
							]);
						}
					} else {
						if (!productCategoryList.productCategoryId.contains(cat)) {
							res.resultCode = 4;
					    	res.resultText = "Product Category Error";
							Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
				        	return res;
						}
					}
					
					if (!productCategoryMemberList.productCategoryId.contains(cat)) {
					       def catDate = methodParams.priceStartingDate;
					       if (!catDate) catDate = new Date();
					       delegator.create("ProductCategoryMember",[
	            				productId:methodParams.productId,
	            				productCategoryId:cat,
	            				fromDate:new Timestamp(catDate.getTime()),
	            			]);
					}                
				}
			}
			
			if (methodParams.taxGroups) {
				for (def cat : methodParams.taxGroups) {
					if (methodParams.adhockCategoryCreate) {
						if (!productCategoryList.productCategoryId.contains(cat)) {
							delegator.create("ProductCategory",[
								productCategoryId:cat,
								categoryName:cat,
								productCategoryTypeId:"TAX_CATEGORY"
							]);
						}
					} else {
						if (!productCategoryList.productCategoryId.contains(cat)) {
							res.resultCode = 4;
							res.resultText = "Product Category Error";
							Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
							return res;
						}
					}
					
					if (!productCategoryMemberList.productCategoryId.contains(cat)) {
						   def catDate = methodParams.priceStartingDate;
						   if (!catDate) catDate = new Date();
						   delegator.create("ProductCategoryMember",[
								productId:methodParams.productId,
								productCategoryId:cat,
								fromDate:new Timestamp(catDate.getTime()),
							]);
					}
				}
			}

			if (methodParams.quantityIncluded) product.quantityIncluded = methodParams.quantityIncluded;
			if (methodParams.piecesIncluded) product.piecesIncluded = methodParams.piecesIncluded;
			if (methodParams.isVirtual) product.isVirtual = methodParams.isVirtual.toString();
			if (methodParams.isVariant) product.isVariant = methodParams.isVariant.toString();
			if (methodParams.productTypeId) product.productTypeId = methodParams.productTypeId.toString();
			if (methodParams.virtualVariantMethodEnum) product.virtualVariantMethodEnum = methodParams.virtualVariantMethodEnum.toString();
			
			if (!methodParams.priceType) methodParams.priceType = "DEFAULT_PRICE";

			if (methodParams.price) {
				if (!methodParams.priceStartingDate) {
					res.resultCode = 5;
			    	res.resultText = "Missing startingDate";
					Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
		        	return res;
				}
				
				if (setPrice(delegator,methodParams)==1) {
					res.resultCode = 6;
			    	res.resultText = "The price for this startingDate exists";
					Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
		        	return res;
				}
			}
			
			condProduct = EntityCondition.makeCondition("productId", EntityOperator.EQUALS,methodParams.productId);
			condType = EntityCondition.makeCondition("goodIdentificationTypeId", EntityOperator.LIKE,"UPC%");
			cond = EntityCondition.makeCondition(condProduct,EntityOperator.AND,condType);
			
			def upcExists = EntityQuery.use(delegator).from("GoodIdentification").where(cond).queryFirst();			
			
			if (!upcExists) {	
				if (methodParams.upc) {
					methodParams.goodIdentification = [:];
					String upcIdent = (methodParams.upc.length() >= 6) ? "UPCA" : "PLU";
					methodParams.goodIdentification.put(upcIdent,methodParams.upc);
				}
				
				if (sUpcModifier) methodParams.goodIdentification.put("UPC_MODIFIER",sUpcModifier);
			}

			if (methodParams.goodIdentification) {
				//Debug.logInfo ( "~~~ Good Identification - " + methodParams.goodIdentification.toString(), "soapapi");
				for (def ident : methodParams.goodIdentification) {
					//Debug.logInfo ( "~~~ ident - key=" + ident.key + " value=" + ident.value , "soapapi");
					delegator.create("GoodIdentification",[
						productId:methodParams.productId,
						goodIdentificationTypeId:ident.key,
						idValue:ident.value
					]);
				}
			}
			
			// Search keywords
			/*
			String[] keywords = methodParams.name.split("\\s+");
			for (def keyword:keywords) {
				if (keyword.length()>2 && !keyword.equalsIgnoreCase("and")) {
					keyword = keyword.toLowerCase();
					
					// Check for existing keyword 	
					def existingKeyword = EntityQuery.use(delegator).from ("ProductKeyword").where("productId",methodParams.productId,"keyword",keyword).queryFirst();					
					if (!existingKeyword) {
						delegator.create("ProductKeyword",[
							productId:methodParams.productId,
							keyword:keyword,
							keywordTypeId:"KWT_KEYWORD",
							relevancyWeight:1l
						]);
					}
				}
			}
			*/
			 
			// ------------ 2024-02-21
			if (methodParams.image) {
				
			  if (methodParams.image.length()<5) {
				  // delete images
				  product.smallImageUrl = null;
				  product.mediumImageUrl = null;
				  product.largeImageUrl = null;
				  product.detailImageUrl = null;
				  product.originalImageUrl = null;
				  def hashDb = EntityQuery.use(delegator).select().from("ProductAttribute").where("productId",methodParams.productId,"attrType","IMAGE_ATTRIB","attrName","ORIG_IMAGE_MD5").queryFirst();
				  if (hashDb) delegator.removeValue(hashDb);
			  } else {
				def imageFile;
				String hash = Utils.getHash(methodParams.image);
				Debug.logInfo ( "~~~ hash = " + hash, "soapapi");
				
				def hashDb = EntityQuery.use(delegator).select().from("ProductAttribute").where("productId",methodParams.productId,"attrType","IMAGE_ATTRIB","attrName","ORIG_IMAGE_MD5").queryFirst();
				
				boolean processImage = true;
				
				if (hashDb) {
					if (hashDb.attrValue.equals(hash)) {
						def origImg = EntityQuery.use(delegator).select("originalImageUrl").from("Product").where("productId",methodParams.productId).queryFirst();
						if (origImg && origImg.originalImageUrl && origImg.originalImageUrl.toString().length()>0 ) processImage = false;
					} else {
						Debug.logInfo ( "~~~ hashDb = " + hashDb, "soapapi");
						hashDb.attrValue = hash;
						delegator.store(hashDb);
					} 
				} else {
					delegator.create("ProductAttribute",[
						productId:methodParams.productId,
						attrType:"IMAGE_ATTRIB",
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
					context.put("productId",methodParams.productId);
					
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
					  def imgMap = Utils.resizeAndStoreImages(false,imageFile,imageServerPath,methodParams.productId,originalFileName,imagesSourcePath,context);
					  if (imgMap) {
						  def imagesUrl = imgMap.get("small");
						  def lastIndex = imagesUrl.lastIndexOf("/");
						  imagesUrl = imagesUrl.substring(0,lastIndex+1);
						  //Debug.logInfo("\n imagesUrl: " + imagesUrl, "ItemFileLoader");
						  product.smallImageUrl = imgMap.get("small");
						  product.largeImageUrl = imgMap.get("large");
						  product.mediumImageUrl = imgMap.get("medium");
						  product.detailImageUrl = imgMap.get("detail");
						  product.originalImageUrl = imagesUrl + originalFileName;
					  }
					  //Debug.logInfo ( "\n\n\n\n\n imgMap =>" + imgMap, "ItemFileLoader");
					  if (tempFile) tempFile.delete();
					}
				} // processImage
			  } // else
			} // image
			
	        delegator.store(product);
			 
			def timeFinal = LocalDateTime.now();
			diffTimeFinal = MILLIS.between(timeGoodIdentRemove,timeFinal);
			Debug.logInfo (loginTime + ";" + diffAfterFirstSelect + ";" + diffBeforeTimeCategory + ";" + diffTimeCategory + ";" + diffTimeGoodIdentRemove + ";" + diffTimeFinal, "soapapi");
	
	    } catch(Exception e) {
			e.printStackTrace();
		    res.resultCode = 1;
		    res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
	        return res;    
	    }       
		
	    res.resultCode = 0;
	    res.resultText = "Success";
		//Debug.logInfo ( "~~~ Result for updateItemAndPrice - " + res.toString(), "soapapi");
		
	    return res;
    }
		
	public ProductDetailsResult productDetails(LoginParams loginParams,ProductDetailsParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");

		ProductDetailsResult res = new ProductDetailsResult();

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
			def prod = EntityQuery.use(delegator).from("Product").where("productId",methodParams.productId).queryFirst();
				
			if (!prod) {
				res.resultCode = 1;
				res.resultText = "The Item " + methodParams.productId + " is not found.";
				return res;
			}
			
			res.name = prod.internalName;
			res.description = prod.description;
			
			// OLD res.departmentId = prod.primaryProductCategoryId;
			// OLD res.departmentName = Utils.getDepartmentName(prod.primaryProductCategoryId,delegator);
			
			res.introductionDate = prod.introductionDate;
			res.salesDiscontinuationDate = prod.salesDiscontinuationDate;
			
			Date date = new Date();
			Timestamp tsFromDate = new Timestamp(date.getTime());
			List datePars = []
			datePars.add(EntityCondition.makeCondition("thruDate", EntityOperator.EQUALS, null))
			datePars.add(EntityCondition.makeCondition("thruDate", EntityOperator.GREATER_THAN, tsFromDate))
			EntityCondition dateCond = EntityCondition.makeCondition(datePars, EntityOperator.OR)
			
			EntityCondition dateCondFrom = EntityCondition.makeCondition("fromDate", EntityOperator.LESS_THAN, tsFromDate);
			
			List categoryPars = []
			EntityCondition productCond = EntityCondition.makeCondition("productId", EntityOperator.EQUALS, methodParams.productId)
			EntityCondition dateAndProductCond = EntityCondition.makeCondition([dateCond, dateCondFrom, productCond], EntityOperator.AND)
			
			def cats = EntityQuery.use(delegator)
			  .select("productCategoryId").from("ProductCategoryMember").where(dateAndProductCond).queryList();
		    
			def categoriesList = new ArrayList();
			def taxList = new ArrayList();
			
			//println("\n\n\n dateAndProductCond=" + dateAndProductCond)
			//println("\n\n\n cats=" + cats)
			
			for (cat in cats) {
				//println("\n\n\n cat=" + cat)
				def catType = EntityQuery.use(delegator)
			      .select("productCategoryTypeId").from("ProductCategory").where("productCategoryId",cat.productCategoryId).queryFirst();
				  
				Debug.logInfo ( "~~~ CATTTTT - " + cat.productCategoryId + " : " + catType.productCategoryTypeId, "soapapi");
				if (catType.productCategoryTypeId.equals("INTEGRATION_CATEGORY")) categoriesList.add(cat.productCategoryId);
				else if (catType.productCategoryTypeId.equals("TAX_CATEGORY")) taxList.add(cat.productCategoryId);
				else if (catType.productCategoryTypeId.equals("DEPARTMENT_CATEGORY")) res.departmentId = cat.productCategoryId;
		    }
			
			if (res.departmentId) res.departmentName = Utils.getDepartmentName(res.departmentId,delegator);
			
			res.categories = categoriesList;
			res.taxGroups = taxList;

			res.piecesIncluded = prod.piecesIncluded;
			res.quantityIncluded = prod.quantityIncluded;
			
			def vUpc = Utils.getUpc(methodParams.productId,delegator);
			res.upc = vUpc.upc;
			res.upc_modifier = vUpc.upc_modifier;
			res.goodIdentification = vUpc.gim
			
			def prodPrices = EntityQuery.use(delegator)
			   .select("productPriceTypeId","price","fromDate","thruDate").from("ProductPrice").where(dateAndProductCond).queryList();
			   
			Debug.logInfo ( "~~~ Prices - " + prodPrices, "soapapi");

			HashMap prices = new HashMap<String,BigDecimal>();
			for (prodPrice in prodPrices) prices.put(prodPrice.productPriceTypeId,Utils.rnd(prodPrice.price));
			res.prices = prices;
						
			res.resultCode = 0;
			res.resultText = "Success";

		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
		
		return res;
	
	}
	
	public ProductListProductsResult listProducts(LoginParams loginParams,ProductListProductsParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");

		ProductListProductsResult res = new ProductListProductsResult();

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
			def productListRecs = null;
			
			if (methodParams.name) exprs.add(EntityCondition.makeCondition("productName", EntityOperator.LIKE,methodParams.name));
			if (methodParams.description) exprs.add(EntityCondition.makeCondition("description", EntityOperator.LIKE,methodParams.description));
			
			def orderBy = UtilMisc.toList("productId");
			def cond = EntityCondition.makeCondition(exprs, EntityOperator.AND);
			def productListRecsPre = EntityQuery.use(delegator)
			   .select("productId").from("Product").where(cond).orderBy(orderBy).queryList();
			def productListPre = EntityUtil.getFieldListFromEntityList(productListRecsPre, "productId", true);
			   
			if (methodParams.goodIdentification) {
				exprs = [];
				exprs.add(EntityCondition.makeCondition("idValue", EntityOperator.EQUALS,methodParams.goodIdentification));
				if (methodParams.name||methodParams.description) exprs.add(EntityCondition.makeCondition("productId", EntityOperator.IN,productListPre));
				cond = EntityCondition.makeCondition(exprs, EntityOperator.AND);
				productListRecs = EntityQuery.use(delegator).select("productId").from("GoodIdentification").where(cond).orderBy(orderBy).queryList();
			} else productListRecs = productListRecsPre;
			   
			def productList = [];
			for (rec in productListRecs) productList.add(rec.productId);
						
			res.resultCode = 0;
			res.resultText = "Success";
			res.products = productList;
			
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
	
		return res;
	}
		
	public ProductListProductConfigResult listProductConfig(LoginParams loginParams,ProductListProductConfigParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");

		ProductListProductConfigResult res = new ProductListProductConfigResult();

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
			if (methodParams.productId) exprs.add(EntityCondition.makeCondition("productId", EntityOperator.EQUALS,methodParams.productId));
			if (methodParams.configItemId) exprs.add(EntityCondition.makeCondition("configItemId", EntityOperator.EQUALS,methodParams.configItemId));
			def cond = EntityCondition.makeCondition(exprs, EntityOperator.AND);
			def orderBy = UtilMisc.toList("productId","configItemId");
			
			def productConfigRecs = EntityQuery.use(delegator)
				.select().from("ProductConfig").where(cond).orderBy(orderBy).queryList();

			def prodConfigList = [];
			for (productConfigRec in productConfigRecs) {
				def prodConfig = new ProductConfig();
				prodConfig.productId = productConfigRec.productId;
				prodConfig.configItemId = productConfigRec.configItemId;
				prodConfig.sequenceNum = productConfigRec.sequenceNum;
				prodConfig.description = productConfigRec.description;
				prodConfig.longDescription = productConfigRec.longDescription;
				prodConfig.fromDate = productConfigRec.fromDate;
				prodConfig.thruDate = productConfigRec.thruDate;
				prodConfig.defaultConfigOptionId = productConfigRec.defaultConfigOptionId;
				prodConfig.isMandatory = productConfigRec.isMandatory;
				
				prodConfigList.add(prodConfig);
			}

			res.resultCode = 0;
			res.resultText = "Success";
			res.productConfigs = prodConfigList;
			
		} catch(Exception e) {
			//e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
	
		return res;
	}

	public ProductListProductConfigItemResult listProductConfigItem(LoginParams loginParams,ProductListProductConfigItemParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");

		ProductListProductConfigItemResult res = new ProductListProductConfigItemResult();

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
			if (methodParams.configItemId) exprs.add(EntityCondition.makeCondition("configItemId", EntityOperator.EQUALS,methodParams.configItemId));
			def cond = EntityCondition.makeCondition(exprs, EntityOperator.AND);
			def orderBy = UtilMisc.toList("configItemId");
			
			def productConfigItemRecs = EntityQuery.use(delegator)
				.select().from("ProductConfigItem").where(cond).orderBy(orderBy).queryList();

			def prodConfigItemList = [];
			
			for (productConfigItemRec in productConfigItemRecs) {
				def prodConfigItem = new ProductConfigItem();
				prodConfigItem.configItemId = productConfigItemRec.configItemId;
				prodConfigItem.configItemTypeId = productConfigItemRec.configItemTypeId;
				prodConfigItem.configItemName = productConfigItemRec.configItemName;		
				prodConfigItem.description = productConfigItemRec.description;
				prodConfigItem.longDescription = productConfigItemRec.longDescription;
				prodConfigItem.imageUrl = productConfigItemRec.imageUrl;
								
				prodConfigItemList.add(prodConfigItem);
			}

			res.resultCode = 0;
			res.resultText = "Success";
			res.productConfigItems = prodConfigItemList;
		
		} catch(Exception e) {
			//e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
	
		return res;
	}

	public ProductListProductConfigOptionResult listProductConfigOption(LoginParams loginParams,ProductListProductConfigOptionParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");

		ProductListProductConfigOptionResult res = new ProductListProductConfigOptionResult();

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
			if (methodParams.configItemId) exprs.add(EntityCondition.makeCondition("configItemId", EntityOperator.EQUALS,methodParams.configItemId));
			def cond = EntityCondition.makeCondition(exprs, EntityOperator.AND);
			def orderBy = UtilMisc.toList("configItemId");
			
			def productConfigOptionRecs = EntityQuery.use(delegator)
				.select().from("ProductConfigOption").where(cond).orderBy(orderBy).queryList();

			def prodConfigOptionList = [];
			
			for (productConfigOptionRec in productConfigOptionRecs) {
				def prodConfigOption = new ProductConfigOption();
				prodConfigOption.configItemId = productConfigOptionRec.configItemId;
				prodConfigOption.configOptionId = productConfigOptionRec.configOptionId;
				prodConfigOption.configOptionName = productConfigOptionRec.configOptionName;
				prodConfigOption.description = productConfigOptionRec.description;
				prodConfigOption.sequenceNum = productConfigOptionRec.sequenceNum;
				
				prodConfigOptionList.add(prodConfigOption);
			}

			res.resultCode = 0;
			res.resultText = "Success";
			res.productConfigOptions = prodConfigOptionList;
		
		} catch(Exception e) {
			//e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
	
		return res;
	}
	
	public ProductListProductConfigProductResult listProductConfigProduct(LoginParams loginParams,ProductListProductConfigProductParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");

		ProductListProductConfigProductResult res = new ProductListProductConfigProductResult();

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
			if (methodParams.configItemId) exprs.add(EntityCondition.makeCondition("configItemId", EntityOperator.EQUALS,methodParams.configItemId));
			if (methodParams.productId) exprs.add(EntityCondition.makeCondition("productId", EntityOperator.EQUALS,methodParams.productId));
			def cond = EntityCondition.makeCondition(exprs, EntityOperator.AND);
			def orderBy = UtilMisc.toList("configItemId","configOptionId");
			
			def productConfigProductRecs = EntityQuery.use(delegator)
				.select().from("ProductConfigProduct").where(cond).orderBy(orderBy).queryList();

			def prodConfigProductList = [];
			
			for (productConfigProductRec in productConfigProductRecs) {
				def prodConfigProduct = new ProductConfigProduct();
				prodConfigProduct.configItemId = productConfigProductRec.configItemId;
				prodConfigProduct.configOptionId = productConfigProductRec.configOptionId;				
				prodConfigProduct.productId = productConfigProductRec.productId;
				prodConfigProduct.quantity = productConfigProductRec.quantity;
				prodConfigProduct.sequenceNum = productConfigProductRec.sequenceNum;
				
				prodConfigProductList.add(prodConfigProduct);
			}

			res.resultCode = 0;
			res.resultText = "Success";
			res.productConfigProducts = prodConfigProductList;
		
		} catch(Exception e) {
			//e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			return res;
		}
	
		return res;
	}

	private boolean verifyRequiredInsertUpdateDeleteProductConfigParams(methodParams) {
		if (!methodParams.productId || !methodParams.configItemId || !methodParams.sequenceNum || !methodParams.fromDate) return false;
		return true;
	}

	public IUDResult insertUpdateProductConfig(LoginParams loginParams,ProductInsertUpdateProductConfigParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		
	    IUDResult res = new IUDResult();		
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method insertUpdateProductConfig", "soapapi");
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
			Debug.logInfo ( "~~~ Result for insertUpdateProductConfig - " + res.toString(), "soapapi");
	        return res;
		}
		
		if (!verifyRequiredInsertUpdateDeleteProductConfigParams(methodParams)) {
			res.resultCode = 3;
			res.resultText = "Missing required param";
			Debug.logInfo ( "~~~ Result for insertUpdateProductConfig - " + res.toString(), "soapapi");
			return res;
		}

   		try {
			def parFromDate = new Timestamp(methodParams.fromDate.getTime());
			
	        def productConfigRec = EntityQuery.use(delegator).from("ProductConfig")
			    .where("productId",methodParams.productId,"configItemId",methodParams.configItemId,
				"sequenceNum",methodParams.sequenceNum,"fromDate",parFromDate).queryFirst();
	        
			Debug.logInfo("\n\n\n productConfigRec = " + productConfigRec, "soapapi");
			
			if (!productConfigRec) {
				// Check for productConfigRec w/o thru_date			
				def exprs1 = [
					EntityCondition.makeCondition("productId", EntityOperator.EQUALS,methodParams.productId),
					EntityCondition.makeCondition("configItemId", EntityOperator.EQUALS,methodParams.configItemId),
					EntityCondition.makeCondition("sequenceNum", EntityOperator.EQUALS,methodParams.sequenceNum),
					EntityCondition.makeCondition("fromDate", EntityOperator.LESS_THAN,parFromDate)
				];
				def cond1 = EntityCondition.makeCondition(exprs1, EntityOperator.AND);
	
				def exprs2 = [
					EntityCondition.makeCondition("thruDate", EntityOperator.GREATER_THAN,parFromDate),
					EntityCondition.makeCondition("thruDate", EntityOperator.EQUALS,null)
				];
				def cond2 = EntityCondition.makeCondition(exprs2, EntityOperator.OR);
				
				def conda = EntityCondition.makeCondition([cond1,cond2], EntityOperator.AND);
				//Debug.logInfo("\n\n\n conda = " + conda, "soapapi");
				
				def productConfigValidRecs = EntityQuery.use(delegator).from("ProductConfig").where(conda).queryList();
				//Debug.logInfo("\n\n\n productConfigValidRecs = " + productConfigValidRecs, "soapapi");
				
				for (rec in productConfigValidRecs) {
					rec.thruDate = new Timestamp(methodParams.fromDate.getTime());
					delegator.store(rec);
				}
				
				// TODO Insert
				productConfigRec = delegator.create("ProductConfig",[
					productId:methodParams.productId,
					configItemId:methodParams.configItemId,
					sequenceNum:methodParams.sequenceNum,
					fromDate:parFromDate
				]);
				
			}
			
			// Update				
			if (methodParams.description) productConfigRec.description = methodParams.description;
			if (methodParams.longDescription) productConfigRec.longDescription = methodParams.longDescription;			
			if (methodParams.configTypeId) productConfigRec.configTypeId = methodParams.configTypeId;
			if (methodParams.defaultConfigOptionId) productConfigRec.defaultConfigOptionId = methodParams.defaultConfigOptionId;
			if (methodParams.thruDate) productConfigRec.thruDate = new Timestamp(methodParams.thruDate.getTime()); // methodParams.thruDate;
			if (methodParams.isMandatory) productConfigRec.isMandatory = methodParams.isMandatory;
			
			delegator.store(productConfigRec);

	    } catch(Exception e) {
		    res.resultCode = 1;
		    res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for insertUpdateProductConfig - " + res.toString(), "soapapi");
	        return res;    
	    }       
		
	    res.resultCode = 0;
	    res.resultText = "Success";
	    
		Debug.logInfo ( "~~~ Result for insertUpdateProductConfig - " + res.toString(), "soapapi");
	    return res;
    }
	
	public IUDResult insertUpdateProductConfigItem(LoginParams loginParams,ProductInsertUpdateProductConfigItemParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;

		Debug.logInfo ( "~~~ Call method insertUpdateProductConfigItem", "soapapi");
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
			Debug.logInfo ( "~~~ Result for insertUpdateProductConfigItem - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.configItemId) {
			res.resultCode = 3;
			res.resultText = "Missing required param";
			Debug.logInfo ( "~~~ Result for insertUpdateProductConfigItem - " + res.toString(), "soapapi");
			return res;
		}	

		try {
			def productConfigItemRec = EntityQuery.use(delegator).from("ProductConfigItem")
				.where("configItemId",methodParams.configItemId).queryFirst();
			
			Debug.logInfo("\n\n\n productConfigItemRec = " + productConfigItemRec, "soapapi");
			
			if (!productConfigItemRec) {
				productConfigItemRec = delegator.create("ProductConfigItem",[
					configItemId:methodParams.configItemId
				]);
			}
			
			// Update
			if (methodParams.configItemId) productConfigItemRec.configItemId = methodParams.configItemId;
			if (methodParams.configItemTypeId) productConfigItemRec.configItemTypeId = methodParams.configItemTypeId;
			if (methodParams.configItemName) productConfigItemRec.configItemName = methodParams.configItemName;
			if (methodParams.description) productConfigItemRec.description = methodParams.description;
			if (methodParams.longDescription) productConfigItemRec.longDescription = methodParams.longDescription;
			if (methodParams.imageUrl) productConfigItemRec.imageUrl = methodParams.imageUrl;
			
			delegator.store(productConfigItemRec);

		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for insertUpdateProductConfigItem - " + res.toString(), "soapapi");
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for insertUpdateProductConfigItem - " + res.toString(), "soapapi");
		return res;
	}
	
	public IUDResult insertUpdateProductConfigOption(LoginParams loginParams,ProductInsertUpdateProductConfigOptionParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method insertUpdateProductConfigOption", "soapapi");
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
			Debug.logInfo ( "~~~ Result for insertUpdateProductConfigOption - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.configItemId||!methodParams.configOptionId) {
			res.resultCode = 3;
			res.resultText = "Missing required param";
			Debug.logInfo ( "~~~ Result for insertUpdateProductConfigOption - " + res.toString(), "soapapi");
			return res;
		}
		
		try {
			def productConfigOptionRec = EntityQuery.use(delegator).from("ProductConfigOption")
				.where("configItemId",methodParams.configItemId,"configOptionId",methodParams.configOptionId).queryFirst();
			
			Debug.logInfo("\n\n\n productConfigOptionRec = " + productConfigOptionRec, "soapapi");
			
			if (!productConfigOptionRec) {
				productConfigOptionRec = delegator.create("ProductConfigOption",[
					configItemId:methodParams.configItemId,
					configOptionId:methodParams.configOptionId
				]);
			}
			
			// Update
			if (methodParams.configOptionName) productConfigOptionRec.configOptionName = methodParams.configOptionName;
			if (methodParams.description) productConfigOptionRec.description = methodParams.description;
			if (methodParams.sequenceNum) productConfigOptionRec.sequenceNum = methodParams.sequenceNum;
			
			delegator.store(productConfigOptionRec);
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for insertUpdateProductConfigOption - " + res.toString(), "soapapi");
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for insertUpdateProductConfigOption - " + res.toString(), "soapapi");
		return res;
	}

	public IUDResult insertUpdateProductConfigProduct(LoginParams loginParams,ProductInsertUpdateProductConfigProductParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method insertUpdateProductConfigProduct", "soapapi");
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
			Debug.logInfo ( "~~~ Result for insertUpdateProductConfigProduct - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.configItemId||!methodParams.configOptionId||!methodParams.productId) {
			res.resultCode = 3;
			res.resultText = "Missing required param";
			Debug.logInfo ( "~~~ Result for insertUpdateProductConfigProduct - " + res.toString(), "soapapi");
			return res;
		}
	
		try {
			def productConfigProductRec = EntityQuery.use(delegator).from("ProductConfigProduct")
				.where("configItemId",methodParams.configItemId,"configOptionId",methodParams.configOptionId,"productId",methodParams.productId).queryFirst();
			
			Debug.logInfo("\n\n\n productConfigProductRec = " + productConfigProductRec, "soapapi");
			
			if (!productConfigProductRec) {
				productConfigProductRec = delegator.create("ProductConfigProduct",[
					configItemId:methodParams.configItemId,
					configOptionId:methodParams.configOptionId,
					productId:methodParams.productId
				]);
			}

			// Update
			if (methodParams.quantity) productConfigProductRec.quantity = methodParams.quantity;
			if (methodParams.sequenceNum) productConfigProductRec.sequenceNum = methodParams.sequenceNum;
			
			delegator.store(productConfigProductRec);
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for insertUpdateProductConfigProduct - " + res.toString(), "soapapi");
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for insertUpdateProductConfigProduct - " + res.toString(), "soapapi");
		return res;
	}

	public IUDResult deleteProductConfig(LoginParams loginParams,ProductDeleteProductConfigParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method deleteProductConfig", "soapapi");
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
			Debug.logInfo ( "~~~ Result for deleteProductConfig - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!verifyRequiredInsertUpdateDeleteProductConfigParams(methodParams)) {
			res.resultCode = 3;
			res.resultText = "Missing required param";
			Debug.logInfo ( "~~~ Result for deleteProductConfig - " + res.toString(), "soapapi");
			return res;
		}

	   try {
			def parFromDate = new Timestamp(methodParams.fromDate.getTime());
			
			def productConfigRec = EntityQuery.use(delegator).from("ProductConfig")
				.where("productId",methodParams.productId,"configItemId",methodParams.configItemId,
				"sequenceNum",methodParams.sequenceNum,"fromDate",parFromDate).queryFirst();
			
			if (productConfigRec) delegator.removeValue(productConfigRec);			
			else {
				res.resultCode = 4;
				res.resultText = "Record not found";
				Debug.logInfo ( "~~~ Error: " + res.toString(), "soapapi");
				return res;
			}
			
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for deleteProductConfig - " + res.toString(), "soapapi");
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for deleteProductConfig - " + res.toString(), "soapapi");
		return res;
	}
	
	public IUDResult deleteProductConfigItem(LoginParams loginParams,ProductDeleteProductConfigItemParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method deleteProductConfig", "soapapi");
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
			Debug.logInfo ( "~~~ Result for deleteProductConfigItem - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.configItemId) {
			res.resultCode = 3;
			res.resultText = "Missing required param";
			Debug.logInfo ( "~~~ Result for deleteProductConfigItem - " + res.toString(), "soapapi");
			return res;
		}

	   try {
			def productConfigItemRec = EntityQuery.use(delegator).from("ProductConfigItem")
				.where("configItemId",methodParams.configItemId).queryFirst();
			
			if (productConfigItemRec) delegator.removeValue(productConfigItemRec);
			else {
				res.resultCode = 4;
				res.resultText = "Record not found";
				Debug.logInfo ( "~~~ Error: " + res.toString(), "soapapi");
				return res;
			}
			
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for deleteProductConfigItem - " + res.toString(), "soapapi");
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for deleteProductConfigItem - " + res.toString(), "soapapi");
		return res;
	}

	public IUDResult deleteProductConfigOption(LoginParams loginParams,ProductDeleteProductConfigOptionParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method deleteProductConfigOption", "soapapi");
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
			Debug.logInfo ( "~~~ Result for deleteProductConfigOption - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.configItemId||!methodParams.configOptionId) {
			res.resultCode = 3;
			res.resultText = "Missing required param";
			Debug.logInfo ( "~~~ Result for deleteProductConfigOption - " + res.toString(), "soapapi");
			return res;
		}

	   try {
			def productConfigOptionRec = EntityQuery.use(delegator).from("ProductConfigOption")
				.where("configItemId",methodParams.configItemId,"configOptionId",methodParams.configOptionId).queryFirst();
			
			Debug.logInfo("\n\n\n productConfigOptionRec = " + productConfigOptionRec, "soapapi");
			
			if (productConfigOptionRec) delegator.removeValue(productConfigOptionRec);
			else {
				res.resultCode = 4;
				res.resultText = "Record not found";
				Debug.logInfo ( "~~~ Error: " + res.toString(), "soapapi");
				return res;
			}
			
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for deleteProductConfigOption - " + res.toString(), "soapapi");
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for deleteProductConfigOption - " + res.toString(), "soapapi");
		return res;
	}

	public IUDResult deleteProductConfigProduct(LoginParams loginParams,ProductDeleteProductConfigProductParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method deleteProductConfigProduct", "soapapi");
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
			Debug.logInfo ( "~~~ Result for deleteProductConfigProduct - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.configItemId||!methodParams.configOptionId||!methodParams.productId) {
			res.resultCode = 3;
			res.resultText = "Missing required param";
			Debug.logInfo ( "~~~ Result for deleteProductConfigProduct - " + res.toString(), "soapapi");
			return res;
		}
		   try {
			def productConfigProductRec = EntityQuery.use(delegator).from("ProductConfigProduct")
				.where("configItemId",methodParams.configItemId,"configOptionId",methodParams.configOptionId,"productId",methodParams.productId).queryFirst();
			
			Debug.logInfo("\n\n\n productConfigProductRec = " + productConfigProductRec, "soapapi");
			 
			if (productConfigProductRec) delegator.removeValue(productConfigProductRec);
			else {
				res.resultCode = 4;
				res.resultText = "Record not found";
				Debug.logInfo ( "~~~ Error: " + res.toString(), "soapapi");
				return res;
			}
			
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for deleteProductConfigProduct - " + res.toString(), "soapapi");
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";

		Debug.logInfo ( "~~~ Result for deleteProductConfigProduct - " + res.toString(), "soapapi");
		return res;
	}
 
	public IUDResult removeCategoryFromItem(LoginParams loginParams,ProductRemoveCategoryFromItemParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method removeCategoryFromItem", "soapapi");
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
			Debug.logInfo ( "~~~ Result for removeCategoryFromItem - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.productId) {
			res.resultCode = 2;
			res.resultText = "Missing productId param";
			Debug.logInfo ( "~~~ Result for removeCategoryFromItem - " + res.toString(), "soapapi");
			return res;
		}
		
		def product = EntityQuery.use(delegator).from("Product").where("productId",methodParams.productId).queryFirst();
		if (!product) {
			res.resultCode = 5;
			res.resultText = "Missing Product";
			Debug.logInfo ( "~~~ Result for removeCategoryFromItem - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.categoryId) {
			res.resultCode = 3;
			res.resultText = "Missing categoryId param";
			Debug.logInfo ( "~~~ Result for removeCategoryFromItem - " + res.toString(), "soapapi");
			return res;
		}
		
		def category = EntityQuery.use(delegator).from("ProductCategory").where("productCategoryId",methodParams.categoryId).queryFirst();
		if (!category) {
			res.resultCode = 5;
			res.resultText = "Missing Category";
			Debug.logInfo ( "~~~ Result for removeCategoryFromItem - " + res.toString(), "soapapi");
			return res;
		}

		try {
			def prod = EntityQuery.use(delegator).from("Product")
				.where("productId",methodParams.productId,"primaryProductCategoryId",methodParams.categoryId,).queryFirst();
			
			if (prod) {
				prod.primaryProductCategoryId = null;
				delegator.store(prod);	
			}
			
			def pcm = EntityQuery.use(delegator).from("ProductCategoryMember")
				.where("productCategoryId",methodParams.categoryId,"productId",methodParams.productId).queryFirst();
			
			if (pcm) delegator.removeValue(pcm);
			else {
				res.resultCode = 6;
				res.resultText = "This category is not related to the product";
				Debug.logInfo ( "~~~ Result for removeCategoryFromItem - " + res.toString(), "soapapi");
				return res;
			}
			
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for removeCategoryFromItem - " + res.toString(), "soapapi");
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for removeCategoryFromItem - " + res.toString(), "soapapi");
		return res;
	}
	
	public IUDResult addCategoryToProductList(LoginParams loginParams,ProductCategoryProductListParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method addCategoryToProductList", "soapapi");
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
			Debug.logInfo ( "~~~ Result for addCategoryToProductList - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.categoryId) {
			res.resultCode = 2;
			res.resultText = "Missing categoryId param";
			Debug.logInfo ( "~~~ Result for addCategoryToProductList - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.productsList) {
			res.resultCode = 3;
			res.resultText = "Missing productsList param";
			Debug.logInfo ( "~~~ Result for addCategoryToProductList - " + res.toString(), "soapapi");
			return res;
		}
		
		def catRec = EntityQuery.use(delegator).select("productCategoryTypeId").from("ProductCategory").where("productCategoryId",methodParams.categoryId).queryFirst();
		if (!catRec) {
			res.resultCode = 4;
			res.resultText = "Missing Category '" + methodParams.categoryId + "'";
			Debug.logInfo ( "~~~ Result for addCategoryToProductList - " + res.toString(), "soapapi");
			return res;
		}
		
		def permittedCatTypes = ["ITEM_LIST","INTEGRATION_CATEGORY"];
		if (!permittedCatTypes.contains(catRec.productCategoryTypeId)) {
			res.resultCode = 5;
			res.resultText = "'" + catRec.productCategoryTypeId + "' - category type of '" + methodParams.categoryId + "' is not permited.";
			Debug.logInfo ( "~~~ Result for addCategoryToProductList - " + res.toString(), "soapapi");
			return res;

		}

		try {
			
			for (product in methodParams.productsList) {
				def prodRec = EntityQuery.use(delegator).select("productId").from("Product").where("productId",product).queryFirst();
				if (prodRec) {
					def prodCatMemberRec = EntityQuery.use(delegator).select().from("ProductCategoryMember").where("productCategoryId",methodParams.categoryId,"productId",product,"thruDate",null).queryFirst();
					if (!prodCatMemberRec) {
						Date date = new Date();
						Timestamp tsFromDate = new Timestamp(date.getTime());
						
						delegator.create("ProductCategoryMember",[
							productCategoryId:methodParams.categoryId,
							productId:product,
							fromDate:tsFromDate
						]);
					}
				}
			}
			
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for removeCategoryFromItem - " + res.toString(), "soapapi");
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for addCategoryToProductList - " + res.toString(), "soapapi");
		return res;
	}

	public IUDResult removeCategoryFromProductList(LoginParams loginParams,ProductCategoryProductListParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method removeCategoryFromProductList", "soapapi");
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
			Debug.logInfo ( "~~~ Result for removeCategoryFromProductList - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.categoryId) {
			res.resultCode = 2;
			res.resultText = "Missing categoryId param";
			Debug.logInfo ( "~~~ Result for addCategoryToProductList - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.productsList) {
			res.resultCode = 3;
			res.resultText = "Missing productsList param";
			Debug.logInfo ( "~~~ Result for addCategoryToProductList - " + res.toString(), "soapapi");
			return res;
		}
		
		def catRec = EntityQuery.use(delegator).select("productCategoryTypeId").from("ProductCategory").where("productCategoryId",methodParams.categoryId).queryFirst();
		if (!catRec) {
			res.resultCode = 4;
			res.resultText = "Missing Category '" + methodParams.categoryId + "'";
			Debug.logInfo ( "~~~ Result for addCategoryToProductList - " + res.toString(), "soapapi");
			return res;
		}
		
		def permittedCatTypes = ["ITEM_LIST","INTEGRATION_CATEGORY"];
		if (!permittedCatTypes.contains(catRec.productCategoryTypeId)) {
			res.resultCode = 5;
			res.resultText = "'" + catRec.productCategoryTypeId + "' - category type of '" + methodParams.categoryId + "' is not permited.";
			Debug.logInfo ( "~~~ Result for addCategoryToProductList - " + res.toString(), "soapapi");
			return res;

		}

		try {
			
			for (product in methodParams.productsList) {
				def prodRec = EntityQuery.use(delegator).select("productId").from("Product").where("productId",product).queryFirst();
				if (prodRec) {
					def prodCatMemberRecs = EntityQuery.use(delegator).select().from("ProductCategoryMember").where("productCategoryId",methodParams.categoryId,"productId",product).queryList();
					if (prodCatMemberRecs) delegator.removeAll(prodCatMemberRecs);
				}
			}
			
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for removeCategoryFromProductList - " + res.toString(), "soapapi");
			return res;
		}

		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for removeCategoryFromProductList - " + res.toString(), "soapapi");
		return res;
	}

	public IUDResult removeAllProductsFromCategory(LoginParams loginParams,ProductRemoveAllProductsFromCategoryParams methodParams) {
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method removeAllProductsFromCategory", "soapapi");
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
			Debug.logInfo ( "~~~ Result for removeAllProductsFromCategory - " + res.toString(), "soapapi");
			return res;
		}
		
		if (!methodParams.categoryId) {
			res.resultCode = 2;
			res.resultText = "Missing categoryId param";
			Debug.logInfo ( "~~~ Result for removeAllProductsFromCategory - " + res.toString(), "soapapi");
			return res;
		}
		
		
		def catRec = EntityQuery.use(delegator).select("productCategoryTypeId").from("ProductCategory").where("productCategoryId",methodParams.categoryId).queryFirst();
		if (!catRec) {
			res.resultCode = 3;
			res.resultText = "Missing Category '" + methodParams.categoryId + "'";
			Debug.logInfo ( "~~~ Result for removeAllProductsFromCategory - " + res.toString(), "soapapi");
			return res;
		}
		
		def permittedCatTypes = ["ITEM_LIST","INTEGRATION_CATEGORY"];
		if (!permittedCatTypes.contains(catRec.productCategoryTypeId)) {
			res.resultCode = 4;
			res.resultText = "'" + catRec.productCategoryTypeId + "' - category type of '" + methodParams.categoryId + "' is not permited.";
			Debug.logInfo ( "~~~ Result for removeAllProductsFromCategory - " + res.toString(), "soapapi");
			return res;

		}

		try {
			def prodCatMemberRecs = EntityQuery.use(delegator).select().from("ProductCategoryMember").where("productCategoryId",methodParams.categoryId).queryList();
			if (prodCatMemberRecs) delegator.removeAll(prodCatMemberRecs);
		} catch(Exception e) {
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for removeAllProductsFromCategory - " + res.toString(), "soapapi");
			return res;
		}

		res.resultCode = 0;
		res.resultText = "Success";
		
		Debug.logInfo ( "~~~ Result for removeAllProductsFromCategory - " + res.toString(), "soapapi");
		return res;
	}

	
	
/*
	public IUDResult deleteProduct(LoginParams loginParams,ProductDeleteProductParams methodParams) {
		LocalDateTime beforeLogin = LocalDateTime.now();
		
		def request = PhaseInterceptorChain.getCurrentMessage().get("HTTP.REQUEST");
		def dispatcher = request.getAttribute("dispatcher");
		def delegator  = request.getAttribute("delegator");
		IUDResult res = new IUDResult();
		EntityCondition condProduct = null, condType = null, cond = null;
		
		Debug.logInfo ( "~~~ Call method deleteProduct", "soapapi");
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
			Debug.logInfo ( "~~~ Result for deleteProduct - " + res.toString(), "soapapi");
			return res;
		}
				
		if (!methodParams.productId) {
			res.resultCode = 2;
			res.resultText = "Missing productId";
			Debug.logInfo ( "~~~ Result for deleteProduct - " + res.toString(), "soapapi");
			return res;
		}
		
		try {
			def product = EntityQuery.use(delegator).from("Product").where("productId",methodParams.productId).queryFirst();
			
			Debug.logInfo ( "\n\n\n\n\n~~~ product - " + product, "soapapi");
			
			if (product) {
				
				// OLD Check foreign keys
				// OLD delegator.removeValue(product);
				
				// TODO Update set internalName = deleted item ; salesDiscontuationDate now()
				
				
				
				// delete search keywords
				def keywords = EntityQuery.use(delegator).from ("ProductKeyword").where("productId",methodParams.productId).queryList();
				Debug.logInfo ( "\n\n\n\n\n~~~ keywords - " + keywords, "soapapi");
				
				if (keywords) delegator.removeAll(keywords);
				
				
				
				// TODO delete categories
			} else {
				res.resultCode = 3;
				res.resultText = "Missing product";
				Debug.logInfo ( "~~~ Result for deleteProduct - " + res.toString(), "soapapi");
				return res;
			}

			
			delegator.store(product);
			 
		} catch(Exception e) {
			e.printStackTrace();
			res.resultCode = 1;
			res.resultText = "System Error: " + e.getMessage();
			Debug.logInfo ( "~~~ Result for deleteProduct - " + res.toString(), "soapapi");
			return res;
		}
		
		res.resultCode = 0;
		res.resultText = "Success";
		
		
		return res;
	}
*/
	
/*	
	BigDecimal rnd(double inp) {
		int n = (int)Math.round(inp*100);
		return n/100;
	}
*/
}
