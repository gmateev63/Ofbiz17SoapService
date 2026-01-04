package com.ofb.api;

import java.util.*;

class CategoryRollupLinkResult {
	public String productCategoryId;
	public CategoryType productCategoryTypeId;
	public String parentCategoryId;
	
	public String toString() {
		return "productCategoryId: " + productCategoryId + "; parentCategoryId: " + parentCategoryId + "; productCategoryTypeId: " + productCategoryTypeId;
	}
}

public class CategoryListCategoryRollupLinksResult {
    public int resultCode;
    public String resultText;
    public String token;
    public List <CategoryRollupLinkResult> rollupLinks;
    
	public String toString() {
		String result;
		
		if (resultCode==0) {
			result = "rollupLinks: ";		
			for (CategoryRollupLinkResult cat : rollupLinks) 
				result += cat.toString() + "\n";
		} else {
			result = "resultCode: " + resultCode + "; resultText: " + resultText + "; token: " + token;
		}
		
		return(result);
	}
}