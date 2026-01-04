package com.ofb.api;

import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericDelegator;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.GenericEntityException;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ImagingOpException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.security.SecureRandom;
import org.apache.ofbiz.common.image.ImageTransform;
import org.apache.ofbiz.common.login.LoginServices;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ModelService;
import org.apache.ofbiz.service.ServiceUtil;
import org.jdom.JDOMException;
import org.apache.ofbiz.base.location.FlexibleLocation;
import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilMisc;
import org.apache.ofbiz.base.util.UtilProperties;
import org.apache.ofbiz.base.util.cache.UtilCache;
import org.apache.ofbiz.base.util.string.FlexibleStringExpander;
import java.security.MessageDigest;

class Upc {
	public String upc;
	public String upc_modifier;
	HashMap<String,String> gim;
}

public class Utils {
	
	private static final String tokenCache = "MTokenCache";
     
	public static Upc getUpc(String productId, GenericDelegator delegator) {
		Upc upc = new Upc();

		upc.gim = new HashMap<String,String>();
		
		try {
			List<GenericValue> goodIdents = EntityQuery.use(delegator)
					   .select("goodIdentificationTypeId","idValue").from("GoodIdentification").where("productId",productId).queryList();
			
			for (GenericValue goodIdent : goodIdents) {
				String type = goodIdent.get("goodIdentificationTypeId").toString();
			    
				if (type.equalsIgnoreCase("UPCA")
					||type.equalsIgnoreCase("UPCB") 
					||type.equalsIgnoreCase("UPCE")) upc.upc = goodIdent.get("idValue").toString();
				else if (type.equalsIgnoreCase("UPC_MODIFIER")) upc.upc_modifier = goodIdent.get("idValue").toString();	
				else upc.gim.put(type,goodIdent.get("idValue").toString());
			}
			
		} catch (GenericEntityException e) {
			upc = null;
			//e.printStackTrace();
		}
				
		return upc;
	}
	
	public static String getDepartmentName(String depId, GenericDelegator delegator) throws GenericEntityException {
		String result = null;
		GenericValue depNameRec = EntityQuery.use(delegator).select("categoryName").from("ProductCategory").where("productCategoryId",depId).queryFirst();
		if (depNameRec!=null) result = depNameRec.get("categoryName").toString();
		return result;
	}

	public static String generateNewToken() {
	    SecureRandom secureRandom = new SecureRandom();
	    Base64.Encoder base64Encoder = Base64.getUrlEncoder();
	    byte[] randomBytes = new byte[24];
	    secureRandom.nextBytes(randomBytes);
	    
	    return base64Encoder.encodeToString(randomBytes);
	}		

	public static String soapUserLoginCheck(String userName, String userPassword, DispatchContext dispatcher) {
			Locale locale = new Locale("en", "US");	
			HashMap<String,Object> ctxLogin = new HashMap<String,Object>();
			ctxLogin.put("username",userName);
			ctxLogin.put("password",userPassword);
			ctxLogin.put("locale",locale);
			
			Map<String,Object> userlmap = LoginServices.userLogin(dispatcher, ctxLogin);
			String response = (String)userlmap.get("responseMessage");			
			GenericValue party = null;
			
			String partyId = null;
			if (response.equalsIgnoreCase("success")) {
				party = (GenericValue)userlmap.get("userLogin");
				partyId = party.getString("partyId");
			}
			
			return partyId;
	}

	public static HashMap<String,String> soapUserLoginToken(String userName, String userPassword, String token, DispatchContext dispatcher) {
		HashMap<String,String> result = new HashMap<String,String>();

		long timeout = 30 * 60 * 1000; // 30 minutes
		UtilCache<String,String> ofbMessagesCache = UtilCache.getOrCreateUtilCache(tokenCache, 0, 0, timeout, true, tokenCache);

		String partyId = ofbMessagesCache.get(token);
		
		if (partyId==null) {
			partyId = soapUserLoginCheck(userName,userPassword,dispatcher);
			
			if (partyId==null) return null;
			
			token = generateNewToken();
			ofbMessagesCache.put(token,partyId);
		}
		
		result.put("partyId",partyId);
		result.put("token",token);
		
		return result;	
	}

	public static HashMap<String,String> parseInternalName(String internalName) {
		HashMap<String,String> result = null;
		
		int pos = internalName.lastIndexOf('_');
		
		if (pos>=0) {
			String productMaster = internalName.substring(0,pos);
			String configId = internalName.substring(pos + 1);
			
			result = new HashMap<String,String>();
			result.put("productMaster",productMaster);
			result.put("configId",configId);
		};
		
		return result;	
	}
	
	public static String parsePaymentForOrder(String internalName) {
		String result = null;
		
		int pos = internalName.lastIndexOf('-');
		if (pos>=0) result = internalName.substring(0,pos);
		
		return result;	
	}
	
    public static Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days); //minus number would decrement the days
        return cal.getTime();
    }
    
    public static BigDecimal rnd(Double inp) {
    	BigDecimal bd = null;
    	if (inp!=null) {
    		bd = new BigDecimal(inp);
    		bd = bd.setScale(2, RoundingMode.HALF_EVEN);
    	}
		return bd;
	}
    
    public static void deleteFilesFromDirectory(File dir) {
        for (final File fileEntry : dir.listFiles()) {
            if (fileEntry.isDirectory()) {
            	deleteFilesFromDirectory(fileEntry);
            } else {
                fileEntry.delete();
				Debug.logInfo ( "\n Deleted file: " + fileEntry.getAbsolutePath(), "com.ofb.api.Utils");
            }
        }
    }

    public static void downloadUsingStream(String urlStr, String file) throws IOException{
    	URL url = new URL(urlStr);
    	BufferedInputStream bis = new BufferedInputStream(url.openStream());
    	FileOutputStream fis = new FileOutputStream(file);
    	byte[] buffer = new byte[1024];
    	int count=0;
    	while((count = bis.read(buffer,0,1024)) != -1) fis.write(buffer, 0, count);
    	fis.close();
    	bis.close();
    }

    public static Map<String, Object> resizeAndStoreImages(boolean isCategory, String imageFile, String imageServerPath, String id, String originalFileName, String imagesSourcePath, Map<String, Object> context) throws IllegalArgumentException, ImagingOpException, IOException, JDOMException {
    	Map<String, Object> imgMap = null;
    	
    	boolean isHttpUrl = imageFile.startsWith("http://")||imageFile.startsWith("https://");
    	
    	// Check for existing source file
    	String sourceFilePath;
    	
    	File sourceFile = null;
    	if (isHttpUrl) {
    		//URL url = new URL(sourceFilePath);
    		sourceFilePath = imageFile;
    	} else {
    		sourceFilePath = imagesSourcePath + "/" + imageFile;
    		sourceFile = new File(sourceFilePath);
    	}
    	
    	Debug.logInfo("\n filePath = " + sourceFilePath + " for id = " + id, "ItemFileLoader");
    	
    	if (isHttpUrl||sourceFile.exists()) {
    		Debug.logInfo ( "\n File exists: " + sourceFilePath, "ItemFileLoader");
    		
    		String subPath = (isCategory) ? "/categories/" : "/products/";
    		String productImagesPath = imageServerPath + subPath + id;
    		File productImagesDir = new File(productImagesPath);
    		if (productImagesDir.exists()) {
    			Debug.logInfo ( "\n Dir exists: " + productImagesPath, "ItemFileLoader");
    		} else {
    			if (productImagesDir.mkdir()) Debug.logInfo( "\n Dir created: " + productImagesPath, "ItemFileLoader");
    			else Debug.logError( "\n Failed to create directory: " + productImagesPath, "ItemFileLoader");
    		}
    		
    		if (productImagesDir.exists()) {
    			Utils.deleteFilesFromDirectory(productImagesDir);
    			Debug.logInfo( "\n All files deleted from: " + productImagesDir, "ItemFileLoader");

    			// Copy file
    			String destinationFileName = productImagesPath + "/" + originalFileName;
    			File destinationFile = new File(destinationFileName);
    			
    			if (isHttpUrl) {
    				try {
    					Utils.downloadUsingStream(imageFile,destinationFileName);
    					Debug.logInfo("\n File copied successfully: " + sourceFilePath, "ItemFileLoader");
    				} catch (Exception e) {
    					Debug.logError("\n Failed to copy file. " + e.getMessage(), "ItemFileLoader");
    				}
    			} else {
    				try {
    					InputStream inputStream = new FileInputStream(sourceFile);
    					OutputStream outputStream = new FileOutputStream(destinationFile);
    			
    					byte[] buffer = new byte[1024];
    					int bytesRead;
    			
    					while ((bytesRead = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, bytesRead);
    			
    					inputStream.close();
    					outputStream.close();
    				
    					Debug.logInfo("\n File copied successfully: " + sourceFilePath, "ItemFileLoader");
    				} catch (Exception e) {
    					Debug.logError("\n Failed to copy file. " + e.getMessage(), "ItemFileLoader");
    				}
    			} // else
    			
    			// Create any sizes images for product
    			String filenameToUse = originalFileName;   			
    			context.put("filenameToUse",filenameToUse);
    			context.put("clientFileName",imageFile);
    			context.put("productId",id);

    			// Map<String, Object> result = org.apache.ofbiz.product.image.ScaleImage.scaleImageInAllSize(context, filenameToUse, "main", "0");
    			Map<String, Object> result = scaleImageInAllSize(isCategory, context, filenameToUse, "main", "0");
    			
    			Debug.logInfo("\n result = " + result, "ItemFileLoader");
    			
    			if (result.containsKey("responseMessage") && "success".equals(result.get("responseMessage"))) imgMap = (Map<String, Object>) result.get("imageUrlMap");
    		}
    		
    	} else Debug.logInfo ( "\n File does not exist: " + sourceFilePath, "ItemFileLoader");
    	
    	return imgMap;
    }
        
    private static Map<String, Object> scaleImageInAllSize(boolean isCategory, Map<String, ? extends Object> context, String filenameToUse, String viewType, String viewNumber)
            throws IllegalArgumentException, ImagingOpException, IOException, JDOMException {

	        String module = Utils.class.getName();
	        String resource = "ProductErrorUiLabels";    
	        final List<String> sizeTypeList = UtilMisc.toList("small", "medium", "large", "detail");
    	
            /* VARIABLES */
            Locale locale = (Locale) context.get("locale");

            int index;
            Map<String, Map<String, String>> imgPropertyMap = new HashMap<>();
            BufferedImage bufImg, bufNewImg;
            double imgHeight, imgWidth;
            Map<String, String> imgUrlMap = new HashMap<>();
            Map<String, Object> resultXMLMap = new HashMap<>();
            Map<String, Object> resultBufImgMap = new HashMap<>();
            Map<String, Object> resultScaleImgMap = new HashMap<>();
            Map<String, Object> result = new HashMap<>();

            /* ImageProperties.xml */
            String fileName = "component://product/config/ImageProperties.xml";
            String imgPropertyFullPath = FlexibleLocation.resolveLocation(fileName).getFile();
            resultXMLMap.putAll(ImageTransform.getXMLValue(imgPropertyFullPath, locale));
            if (resultXMLMap.containsKey("responseMessage") && "success".equals(resultXMLMap.get("responseMessage"))) {
                imgPropertyMap.putAll(UtilGenerics.<Map<String, Map<String, String>>>cast(resultXMLMap.get("xml")));
            } else {
                String errMsg = UtilProperties.getMessage(resource, "ScaleImage.unable_to_parse", locale) + " : ImageProperties.xml";
                Debug.logError(errMsg, module);
                result.put(ModelService.ERROR_MESSAGE, errMsg);
                return result;
            }

            /* IMAGE */
            // get Name and Extension
            index = filenameToUse.lastIndexOf('.');
            String imgExtension = filenameToUse.substring(index + 1);
            // paths

            Map<String, Object> imageContext = new HashMap<>();
            imageContext.putAll(context);
            imageContext.put("tenantId",((Delegator)context.get("delegator")).getDelegatorTenantId());
            String imageServerPath = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog", "image.server.path", (Delegator)context.get("delegator")), imageContext);
            String imageUrlPrefix = FlexibleStringExpander.expandString(EntityUtilProperties.getPropertyValue("catalog", "image.url.prefix", (Delegator)context.get("delegator")), imageContext);
            imageServerPath = imageServerPath.endsWith("/") ? imageServerPath.substring(0, imageServerPath.length()-1) : imageServerPath;
            imageUrlPrefix = imageUrlPrefix.endsWith("/") ? imageUrlPrefix.substring(0, imageUrlPrefix.length()-1) : imageUrlPrefix;
            FlexibleStringExpander filenameExpander;
            String fileLocation = null;
            String id = null;
            
            String prodCat = (isCategory) ? "categories" : "products";
            
            if (viewType.toLowerCase(Locale.getDefault()).contains("main")) {
                String filenameFormat = EntityUtilProperties.getPropertyValue("catalog", "image.filename.format", (Delegator) context.get("delegator"));
                filenameExpander = FlexibleStringExpander.getInstance(filenameFormat);
                id = (String) context.get("productId");
                fileLocation = filenameExpander.expandString(UtilMisc.toMap("location", prodCat, "id", id, "type", "original"));
            } else if (viewType.toLowerCase(Locale.getDefault()).contains("additional") && viewNumber != null && !"0".equals(viewNumber)) {
                String filenameFormat = EntityUtilProperties.getPropertyValue("catalog", "image.filename.additionalviewsize.format", (Delegator) context.get("delegator"));
                filenameExpander = FlexibleStringExpander.getInstance(filenameFormat);
                id = (String) context.get("productId");
                if (filenameFormat.endsWith("${id}")) {
                    id = id + "_View_" + viewNumber;
                } else {
                    viewType = "additional" + viewNumber;
                }
                fileLocation = filenameExpander.expandString(UtilMisc.toMap("location", prodCat, "id", id, "viewtype", viewType, "sizetype", "original"));
            } else {
                return ServiceUtil.returnError(UtilProperties.getMessage(resource, "ProductImageViewType", UtilMisc.toMap("viewType", viewType), locale));
            }

            /* get original BUFFERED IMAGE */
            resultBufImgMap.putAll(ImageTransform.getBufferedImage(imageServerPath + "/" + fileLocation + "." + imgExtension, locale));

            if (resultBufImgMap.containsKey("responseMessage") && "success".equals(resultBufImgMap.get("responseMessage"))) {
                bufImg = (BufferedImage) resultBufImgMap.get("bufferedImage");

                // get Dimensions
                imgHeight = bufImg.getHeight();
                imgWidth = bufImg.getWidth();
                if (imgHeight == 0.0 || imgWidth == 0.0) {
                    String errMsg = UtilProperties.getMessage(resource, "ScaleImage.one_current_image_dimension_is_null", locale) + " : imgHeight = " + imgHeight + " ; imgWidth = " + imgWidth;
                    Debug.logError(errMsg, module);
                    result.put(ModelService.ERROR_MESSAGE, errMsg);
                    return result;
                }

                /* Scale image for each size from ImageProperties.xml */
                for (Map.Entry<String, Map<String, String>> entry : imgPropertyMap.entrySet()) {
                    String sizeType = entry.getKey();

                    // Scale
                    resultScaleImgMap.putAll(ImageTransform.scaleImage(bufImg, imgHeight, imgWidth, imgPropertyMap, sizeType, locale));

                    /* Write the new image file */
                    if (resultScaleImgMap.containsKey("responseMessage") && "success".equals(resultScaleImgMap.get("responseMessage"))) {
                        bufNewImg = (BufferedImage) resultScaleImgMap.get("bufferedImage");

                        // Build full path for the new scaled image
                        String newFileLocation = null;
                        filenameToUse = sizeType + filenameToUse.substring(filenameToUse.lastIndexOf('.'));
                        if (viewType.toLowerCase(Locale.getDefault()).contains("main")) {
                            newFileLocation = filenameExpander.expandString(UtilMisc.toMap("location", prodCat, "id", id, "type", sizeType));
                        } else if (viewType.toLowerCase(Locale.getDefault()).contains("additional")) {
                            newFileLocation = filenameExpander.expandString(UtilMisc.toMap("location", prodCat, "id", id, "viewtype", viewType, "sizetype", sizeType));
                        }
                        String newFilePathPrefix = "";
                        if (newFileLocation != null && newFileLocation.lastIndexOf('/') != -1) {
                            newFilePathPrefix = newFileLocation.substring(0, newFileLocation.lastIndexOf('/') + 1); // adding 1 to include the trailing slash
                        }
                        // Directory
                        String targetDirectory = imageServerPath + "/" + newFilePathPrefix;
                        try {
                            // Create the new directory
                            File targetDir = new File(targetDirectory);
                            if (!targetDir.exists()) {
                                boolean created = targetDir.mkdirs();
                                if (!created) {
                                    String errMsg = UtilProperties.getMessage(resource, "ScaleImage.unable_to_create_target_directory", locale) + " - " + targetDirectory;
                                    Debug.logFatal(errMsg, module);
                                    return ServiceUtil.returnError(errMsg);
                                }
                            // Delete existing image files
                            // Images aren't ordered by productId (${location}/${viewtype}/${sizetype}/${id}) !!! BE CAREFUL !!!
                            } else if (newFileLocation.endsWith("/" + id)) {
                                try {
                                    File[] files = targetDir.listFiles();
                                    for (File file : files) {
                                        if (file.isFile() && file.getName().startsWith(id)) {
                                            if (!file.delete()) {
                                                Debug.logError("File :" + file.getName() + ", couldn't be deleted", module);
                                            }
                                        }
                                    }
                                } catch (SecurityException e) {
                                    Debug.logError(e,module);
                                }
                            }
                        } catch (NullPointerException e) {
                            Debug.logError(e,module);
                        }

                        // write new image
                        try {
                            ImageIO.write(bufNewImg, imgExtension, new File(imageServerPath + "/" + newFileLocation + "." + imgExtension));
                        } catch (IllegalArgumentException e) {
                            String errMsg = UtilProperties.getMessage(resource, "ScaleImage.one_parameter_is_null", locale) + e.toString();
                            Debug.logError(errMsg, module);
                            result.put(ModelService.ERROR_MESSAGE, errMsg);
                            return result;
                        } catch (IOException e) {
                            String errMsg = UtilProperties.getMessage(resource, "ScaleImage.error_occurs_during_writing", locale) + e.toString();
                            Debug.logError(errMsg, module);
                            result.put(ModelService.ERROR_MESSAGE, errMsg);
                            return result;
                        }

                        // Save each Url
                        if (sizeTypeList.contains(sizeType)) {
                            String imageUrl = imageUrlPrefix + "/" + newFileLocation + "." + imgExtension;
                            imgUrlMap.put(sizeType, imageUrl);
                        }

                    } // scaleImgMap
                } // Loop over sizeType

                result.put("responseMessage", "success");
                result.put("imageUrlMap", imgUrlMap);
                result.put("original", resultBufImgMap);
                return result;

            } else {
                String errMsg = UtilProperties.getMessage(resource, "ScaleImage.unable_to_scale_original_image", locale) + " : " + filenameToUse;
                Debug.logError(errMsg, module);
                result.put(ModelService.ERROR_MESSAGE, errMsg);
                return ServiceUtil.returnError(errMsg);
            }
        }    
    
    public static String getHash(String inStr) {
        String result = null;
    	try {
	    	MessageDigest md = MessageDigest.getInstance("MD5");             
	        md.update(inStr.getBytes());
	        byte[] hash = md.digest();
	        result = Base64.getEncoder().encodeToString(hash);
    	} catch (java.security.NoSuchAlgorithmException e) {}
        return result;
     }

}