package com.ofb.api;

import java.util.List;

class ProductConfigProduct {
	public String configItemId;
	public String configOptionId;
	public String productId;
	public String quantity;
	public String sequenceNum;
}

public class ProductListProductConfigProductResult {
    public int resultCode;
    public String resultText;
    public String token;
    public List <ProductConfigProduct> productConfigProducts;
}