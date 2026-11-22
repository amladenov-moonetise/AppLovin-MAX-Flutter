package com.moonetise.research.b5;

/** SDK-free fixed research contract. Dimensions are DIPs/OpenRTB size, not physical pixels. */
public final class BannerScope {
    public static final String UNIT = "cecda62026b8a5bb";
    public static final int WIDTH = 320, HEIGHT = 50;
    private BannerScope() {}
    /** Format/size mismatch never reaches the consuming slot operation. */
    public static PendingSlot.Claim claim(PendingSlot slot, String unit, String format,
            int width, int height, long now, java.util.function.BooleanSupplier targeting) {
        if (!compatible(unit, format, width, height)) return null;
        return slot.claim(unit, now, targeting);
    }
    public static boolean compatible(String unit, String format, int width, int height) {
        return UNIT.equals(unit) && "BANNER".equals(format) && width == WIDTH && height == HEIGHT;
    }
    public static boolean creative(String type, int width, int height) {
        return "banner".equals(type) && width == WIDTH && height == HEIGHT;
    }
}
