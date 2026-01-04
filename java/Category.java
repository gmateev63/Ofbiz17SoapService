package com.ofb.api;

import java.math.BigDecimal;

enum CategoryType {
	INTEGRATION_CATEGORY, TAX_CATEGORY, ITEM_LIST, CATALOG_CATEGORY
}

public class Category {
	public String productCategoryId;
	public CategoryType productCategoryTypeId;
	public String primaryParentCategoryId;
	public String categoryName;
	public String description;
	public String longDescription;
	public String image;
	public ImageTypes imageType;
	public BigDecimal taxPercentage;
	
	public String toString() {
		return("productCategoryId: " + productCategoryId + " ; productCategoryTypeId:" + productCategoryTypeId
				+ " ; primaryParentCategoryId: " + primaryParentCategoryId + " ; categoryName:" + categoryName
				+ " ; description: " + description + " ; longDescription:" + longDescription
				+ " ; categoryImageUrl: " + image + " ; taxPercentage: " + taxPercentage 
				);
	}
}
