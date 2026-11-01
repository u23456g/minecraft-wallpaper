package dev.wallpapermenu.wallpaper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CdpInputMappingTest {
    @Test
    void mapsGlfwModifierBitsToCdpBits() {
        assertEquals(0, CdpInputMapping.modifiers(0));
        assertEquals(2, CdpInputMapping.modifiers(0x0001));
        assertEquals(4, CdpInputMapping.modifiers(0x0002));
        assertEquals(8, CdpInputMapping.modifiers(0x0004));
        assertEquals(1, CdpInputMapping.modifiers(0x0008));
        assertEquals(14, CdpInputMapping.modifiers(0x0007));
        assertEquals(15, CdpInputMapping.modifiers(0x000F));
    }
}
