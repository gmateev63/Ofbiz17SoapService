package com.ofb.api;

public class ItemRow {
	public String productId;
	public String productName;
	public String productDepartment;
	public double quantity;
	public double amount;
	public double tax;
	public String taxGroup;
	public double taxPerc;

public String toString() {
    String result = "productId=" + productId + "; productName=" + productName + "; productDepartment=" + productDepartment
    		 + "; quantity=" + quantity + "; amount=" + amount + "; tax=" + tax + "; taxGroup=" + taxGroup + "; taxPerc=" + taxPerc;
	return result;
}

}

