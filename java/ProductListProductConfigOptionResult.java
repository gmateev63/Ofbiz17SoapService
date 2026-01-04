package com.ofb.api;

import java.util.List;

class ProductConfigOption {
	public String configItemId;
	public String configOptionId;
	public String configOptionName;
	public String description;
	public String sequenceNum;
}   

public class ProductListProductConfigOptionResult {
    public int resultCode;
    public String resultText;
    public String token;
    public List <ProductConfigOption> productConfigOptions;
}