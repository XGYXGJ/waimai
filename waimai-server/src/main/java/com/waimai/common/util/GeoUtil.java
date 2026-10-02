package com.waimai.common.util;

public class GeoUtil {

    private static final double EARTH_R = 6371.0; // km

    /** Haversine 距离（公里） */
    public static double distanceKm(double lng1, double lat1, double lng2, double lat2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double dLat = radLat2 - radLat1;
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_R * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** 距离衰减因子：exp(-km / 3) */
    public static double distanceDecay(double km) {
        return Math.exp(-km / 3.0);
    }

    /** 归一化到 [0,1] */
    public static double normalize(double v, double min, double max) {
        if (max <= min) return 0.5;
        return Math.max(0, Math.min(1, (v - min) / (max - min)));
    }
}
