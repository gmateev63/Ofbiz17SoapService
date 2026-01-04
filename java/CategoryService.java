package com.ofb.api;

import javax.jws.WebService;

@WebService
public interface CategoryService {
	CategoryListCategoriesResult listCategories(
			LoginParams loginParams		
		);
	
	IUDResult categoryInsertUpdate(
			LoginParams loginParams,
			Category methodParams
	);
	
	IUDResult categoryRollupLinkInsertUpdate(
			LoginParams loginParams,
			CategoryRollup methodParams
	);
	
	IUDResult categoryRollupLinkDelete(
			LoginParams loginParams,
			CategoryRollupKey methodParams
	);
	
	CategoryListCategoryRollupLinksResult listCategoryRollupLinks(
			LoginParams loginParams,
			CategoryListCategoryRollupLinksParams methodParams
	);
	
}