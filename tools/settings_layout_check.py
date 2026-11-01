"""Check the settings layout bounds at common Minecraft GUI sizes."""
for width, height in [(427, 240), (428, 256), (640, 360), (960, 540)]:
    controls_width = min(170, width // 3 + 12)
    controls_x = width - controls_width - 10
    grid_width = controls_x - 20
    card_width = (grid_width - 12) // 3
    card_height = (height - 98) // 3
    step = min(24, (height - 48) // 9)
    assert 42 + 3 * card_height + 12 <= height - 26
    assert 42 + 7 * step + 20 <= height - 26
    assert 10 + 3 * card_width + 12 < controls_x
    for image_width, image_height in [(1920, 1080), (600, 1200), (512, 512)]:
        scale = min((card_width - 4) / image_width, (card_height - 17) / image_height)
        assert int(image_width * scale) <= card_width - 4
        assert int(image_height * scale) <= card_height - 17
    print(f'{width}x{height}: nine covers, controls and footer do not overlap; images fit')
