package com.ofb.api;

public class CategoryResult {
	public String productCategoryId;
	public CategoryType productCategoryTypeId;
	public String primaryParentCategoryId;
	public String categoryName;
	public String description;
	public String longDescription;
	public String categoryImageUrl;
	
	public String toString() {
		return("productCategoryId: " + productCategoryId + " ; productCategoryTypeId:" + productCategoryTypeId
				+ " ; primaryParentCategoryId: " + primaryParentCategoryId + " ; categoryName:" + categoryName
				+ " ; description: " + description + " ; longDescription:" + longDescription
				+ " ; categoryImageUrl: " + categoryImageUrl 
				);
	}
}
