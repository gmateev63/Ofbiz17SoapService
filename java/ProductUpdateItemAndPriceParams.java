package com.ofb.api;

import java.util.*;
import java.math.BigDecimal;

enum PriceType {
	DEFAULT_PRICE, LIST_PRICE
}

enum YN {
	Y, N
}

enum ProductTypeId {
	FINISHED_GOOD, AGGREGATED
}

enum VirtualVariantMethodEnum {
	VV_VARIANTTREE, VV_FEATURETREE
}

enum ImageTypes {
	png, jpg, gif
}

class Ident {
	String type;
	String idValue;
};

public class ProductUpdateItemAndPriceParams {
	public String productId;
	public String upc;
	public String upc_modifier;
	public String name;
	public String description;
	public String department;
	public List<String> categories;
	public PriceType priceType;
	public BigDecimal quantityIncluded;
	public long piecesIncluded;
	public Date priceStartingDate;
	public Date priceEndingDate;
	public BigDecimal price;
	public List<String> taxGroups;
	public boolean adhockCategoryCreate;
	public Map<String,String> goodIdentification;
	public Date introductionDate; 
	public Date salesDiscontinuationDate;
	public YN isVirtual;
	public YN isVariant;
	public ProductTypeId productTypeId;
	public VirtualVariantMethodEnum virtualVariantMethodEnum;
	public String image;
	public ImageTypes imageType;
	
	public String toString() {
		return("\n productId: " + productId + "\n upc:" + upc + "\n name: " + name + "\n description: " + description + "\n department: " + department
				+ "\n categories: " + categories + "\n priceType: " + priceType
				+ "\n quantityIncluded: " + quantityIncluded + "\n piecesIncluded: " + piecesIncluded+ "\n priceStartingDate: " + priceStartingDate
				+ "\n priceEndingDate: " + priceEndingDate + "\n price: " + price+ "\n taxGroups: " + taxGroups
				+ "\n adhockCategoryCreate: " + adhockCategoryCreate + "\n goodIdentification: " + goodIdentification
				+ "\n introductionDate: " + introductionDate
				+ "\n salesDiscontinuationDate: " + salesDiscontinuationDate);				
	}
}