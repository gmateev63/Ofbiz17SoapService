package com.ofb.api;

import java.util.*;

public class CategoryListCategoriesResult {
    public int resultCode;
    public String resultText;
    public String token;
    public List <CategoryResult> categories;
    
	public String toString() {
		String result;
		
		if (resultCode==0) {
			result = "categories: ";		
			for (CategoryResult cat : categories) 
				result += cat.toString() + "\n";
		} else {
			result = "resultCode: " + resultCode + "; resultText: " + resultText + "; token: " + token;
		}
		
		return(result);
	}
}