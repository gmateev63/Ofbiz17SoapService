package com.ofb.api;

import java.util.*;
import java.math.BigDecimal;
//import java.util.Date;

public class ProductDetailsResult {
    public int resultCode;
    public String resultText;
    public String token;
	public String upc;
	public String upc_modifier;
	public String name;
	public String description;
	public String departmentId;
	public String departmentName;
	public List<String> categories;
	public BigDecimal quantityIncluded;
	public Long piecesIncluded;
	public Map<String,BigDecimal> prices;	
	public List<String> taxGroups;
	public Map<String,String> goodIdentification;
	public Date introductionDate; 
	public Date salesDiscontinuationDate; 
	
	public String toString() {
		return("\n upc:" + upc + "\n upc+modifier:" + upc_modifier + "\n name: " + name + "\n description: " + description + "\n department: " + departmentId
				+ "\n departmentName: " + departmentName + "\n categories: " + categories
				+ "\n quantityIncluded: " + quantityIncluded + "\n piecesIncluded: " + piecesIncluded
				+ "\n prices: " + prices + "\n taxGroups: " + taxGroups
				+ "\n goodIdentification: " + goodIdentification
				+ "\n introductionDate: " + introductionDate
				+ "\n salesDiscontinuationDate: " + salesDiscontinuationDate + "\n token: " + token);
	}
}