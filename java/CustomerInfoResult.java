package com.ofb.api;

public class CustomerInfoResult {
    public int resultCode;
    public String resultText;
    public String token;
	public String firstName;
	public String middleName;
	public String lastName;
	public String email;
	public String phone;
	public String postalAddress;
	public String webAddress;
	public String ipAddress;
	public String electronicAddress;
	
	public String toString() {
		return("\n firstName: " + firstName + "\n middleName: " + middleName + "\n lastName: " + lastName + "\n email: " + email + "\n phone: " + phone
				+ "\n postalAddress: " + postalAddress + "\n webAddress: " + webAddress 
				+ "\n ipAddress: " + ipAddress + "\n electronicAddress: " + electronicAddress + "\n token: " + token);
	}
}