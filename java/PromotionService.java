package com.ofb.api;

import javax.jws.WebService;

@WebService
public interface PromotionService {

	InsertUpdatePromotionResult insertUpdatePromotion(
			LoginParams loginParams,
			PromotionInsertUpdatePromotionParams methodParams			
		);
	
	IUDResult addProductToPromotion(
			LoginParams loginParams,
			PromotionAddRemoveProductToPromotionParams methodParams			
		);
	
	IUDResult removeProductFromPromotion(
			LoginParams loginParams,
			PromotionAddRemoveProductToPromotionParams methodParams			
		);

	IUDResult addCategoryToPromotion(
			LoginParams loginParams,
			PromotionAddRemoveCategoryToPromotionParams methodParams			
		);
	
	IUDResult removeCategoryFromPromotion(
			LoginParams loginParams,
			PromotionAddRemoveCategoryToPromotionParams methodParams			
		);
	
	PromotionListPromotionsResult listPromotions(
			LoginParams loginParams,
			PromotionListPromotionsParams methodParams			
		);

	InsertUpdatePromotionResult insertUpdateComboPromotion(
			LoginParams loginParams,
			PromotionInsertUpdateComboPromoParams methodParams			
		);
	
	/*PromotionDetailsResult promotionDetails(
			LoginParams loginParams,
			PromotionDetailsParams methodParams	
	);*/		
	
}