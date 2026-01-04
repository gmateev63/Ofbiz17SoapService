package com.ofb.api;

import javax.jws.WebService;

@WebService
public interface ProductService {
	IUDResult updateItemAndPrice(
			LoginParams loginParams,
			ProductUpdateItemAndPriceParams methodPparams
	);
	
	ProductDetailsResult productDetails(
			LoginParams loginParams,
			ProductDetailsParams methodParams	
	);
	
	ProductListProductsResult listProducts(
			LoginParams loginParams,
			ProductListProductsParams methodParams			
		);

	ProductListProductConfigResult listProductConfig(
			LoginParams loginParams,
			ProductListProductConfigParams methodParams			
		);
	
	ProductListProductConfigItemResult listProductConfigItem(
			LoginParams loginParams,
			ProductListProductConfigItemParams methodParams			
		);

	ProductListProductConfigOptionResult listProductConfigOption(
			LoginParams loginParams,
			ProductListProductConfigOptionParams methodParams			
		);

	ProductListProductConfigProductResult listProductConfigProduct(
			LoginParams loginParams,
			ProductListProductConfigProductParams methodParams			
		);
	
	IUDResult insertUpdateProductConfig(
			LoginParams loginParams,
			ProductInsertUpdateProductConfigParams methodParams			
		);
	
	IUDResult insertUpdateProductConfigItem(
			LoginParams loginParams,
			ProductInsertUpdateProductConfigItemParams methodParams			
		);
	
	IUDResult insertUpdateProductConfigOption(
			LoginParams loginParams,
			ProductInsertUpdateProductConfigOptionParams methodParams			
		);
	
	IUDResult insertUpdateProductConfigProduct(
			LoginParams loginParams,
			ProductInsertUpdateProductConfigProductParams methodParams			
		);
		
	IUDResult deleteProductConfig(
			LoginParams loginParams,
			ProductDeleteProductConfigParams methodParams			
		);

	IUDResult deleteProductConfigItem(
			LoginParams loginParams,
			ProductDeleteProductConfigItemParams methodParams			
		);
	
	IUDResult deleteProductConfigOption(
			LoginParams loginParams,
			ProductDeleteProductConfigOptionParams methodParams			
		);
	
	IUDResult deleteProductConfigProduct(
			LoginParams loginParams,
			ProductDeleteProductConfigProductParams methodParams			
		);
	
	IUDResult removeCategoryFromItem(
			LoginParams loginParams,
			ProductRemoveCategoryFromItemParams methodParams			
		);
	
	IUDResult addCategoryToProductList(
			LoginParams loginParams,
			ProductCategoryProductListParams methodParams			
		);
	
	IUDResult removeCategoryFromProductList(
			LoginParams loginParams,
			ProductCategoryProductListParams methodParams			
		);
	
	IUDResult removeAllProductsFromCategory(
			LoginParams loginParams,
			ProductRemoveAllProductsFromCategoryParams methodParams			
		);
	
/*
	IUDResult deleteProduct(
			LoginParams loginParams,
			ProductDeleteProductParams methodParams			
		);
*/
	
	
}