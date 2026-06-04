package com.ds.goroute.utils;

public final class AddressDistrictParser {

    private AddressDistrictParser() {
    }

    /**
     * "13 LÃ² ÄÃºc, Hai BÃ  TrÆ°ng, HÃ  Ná»™i" â†’ "Hai BÃ  TrÆ°ng"
     */
    public static String extractDistrict(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String[] parts = address.split(",");
        if (parts.length >= 3) {
            return parts[parts.length - 2].trim();
        }
        if (parts.length == 2) {
            return parts[0].trim();
        }
        return null;
    }
}
