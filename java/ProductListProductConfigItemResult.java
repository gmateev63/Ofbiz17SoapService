package com.ofb.api;

import java.util.List;

class ProductConfigItem {
	public String configItemId;
	public String configItemTypeId;
	public String configItemName;
	public String description;
	public String longDescription;
	public String imageUrl;
}

public class ProductListProductConfigItemResult {
    public int resultCode;
    public String resultText;
    public String token;
    public List <ProductConfigItem> productConfigItems;
}